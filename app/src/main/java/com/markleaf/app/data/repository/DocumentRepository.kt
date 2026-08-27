package com.markleaf.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.markleaf.app.data.local.DocumentDao
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.local.PdfPrintPageAnchorDao
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.OutlineNodeSource
import com.markleaf.app.data.local.TextbookOutlineNodeDao
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.data.local.TextbookOutlineNodeType
import com.markleaf.app.packageimport.ZipPackageImporter
import com.markleaf.app.pdf.TextbookOutlineResolver
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Data access for recent-read documents.
 * Delete never touches a user-owned source file. App-managed copies under
 * `files/library`, and extracted package trees under `files/packages`, are
 * removed after the database transaction commits.
 */
class DocumentRepository(
    private val context: Context,
    private val db: RoomDatabase,
    private val documentDao: DocumentDao,
    private val pdfPrintPageAnchorDao: PdfPrintPageAnchorDao,
    private val textbookOutlineNodeDao: TextbookOutlineNodeDao,
    private val searchRepository: SearchRepository,
) {

    fun observeRecentDocuments(): Flow<List<DocumentEntity>> =
        documentDao.observeRecentDocuments()

    suspend fun getById(id: String): DocumentEntity? =
        documentDao.getById(id)

    suspend fun getDocumentById(id: String): DocumentEntity? =
        withContext(Dispatchers.IO) {
            documentDao.getById(id)
        }

    suspend fun getAllDocuments(): List<DocumentEntity> =
        withContext(Dispatchers.IO) {
            documentDao.getAll()
        }

    suspend fun touchLastOpened(id: String, time: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            documentDao.updateLastOpenedAt(id, time)
        }
    }

    /**
     * Locate an existing private-library document whose content SHA-256 matches.
     * Used for external-open content dedup without a Room schema change.
     */
    suspend fun findByContentSha256(sha256: String, sizeBytes: Long?): DocumentEntity? =
        withContext(Dispatchers.IO) {
            val want = sha256.lowercase(Locale.ROOT)
            val byId = documentDao.getById("c:$want")
            if (byId != null) return@withContext byId
            val candidates = documentDao.getAll().filter { doc ->
                sizeBytes == null || doc.fileSize == null || doc.fileSize == sizeBytes
            }
            for (doc in candidates) {
                val path = filePathFromUriString(doc.uri) ?: continue
                val file = File(path)
                if (!file.isFile) continue
                if (sizeBytes != null && file.length() != sizeBytes) continue
                if (sha256OfFile(file) == want) return@withContext doc
            }
            null
        }

    private fun filePathFromUriString(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "file" -> uri.path
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    suspend fun upsert(document: DocumentEntity) {
        withContext(Dispatchers.IO) {
            upsertAndIndex(document)
        }
    }

    private suspend fun upsertAndIndex(document: DocumentEntity) {
        documentDao.upsert(document)
        searchRepository.index(document)
    }

    suspend fun deleteById(id: String) {
        withContext(Dispatchers.IO) {
            val document = documentDao.getById(id)
            // P0A: wrap three-step delete in a Room transaction so a failure
            // in any step rolls back all prior steps. The FK CASCADE would
            // eventually clean up outline/anchor on document delete, but
            // explicit ordering inside a transaction guarantees consistency
            // even if CASCADE behavior differs across migration paths.
            //
            // Do NOT simplify to CASCADE-only without real DB tests proving
            // both outline and print anchor cascade on the current v6 schema
            // AND on historical migration databases (see DeleteByIdTransactionTest).
            db.withTransaction {
                textbookOutlineNodeDao.deleteAllForDocument(id)
                pdfPrintPageAnchorDao.deleteAllForDocument(id)
                searchRepository.clearDocument(id)
                documentDao.deleteById(id)
            }
            // Sidecar identity index is rebuildable; remove best-effort after Room commit.
            try {
                com.markleaf.app.share.DocumentIdentityIndex(context).removeByDocumentId(id)
            } catch (_: Exception) {
            }
            // Markdown-package extract tree (ZIP textbooks). Orphan dirs accumulate if
            // only Room rows are deleted — wipe best-effort after successful commit.
            try {
                val packageDir = java.io.File(context.filesDir, "packages/$id")
                if (packageDir.exists()) {
                    packageDir.deleteRecursively()
                }
            } catch (_: Exception) {
            }
            // External VIEW/SEND intake owns copies under files/library. Once the
            // final Room reference is gone, remove that private copy as well. The
            // canonical-path guard in deleteAppPrivateLibraryCopy prevents this
            // from ever deleting a user-owned SAF/file source outside the sandbox.
            if (document != null) {
                try {
                    val stillReferenced = documentDao.getAll().any { it.uri == document.uri }
                    if (!stillReferenced) {
                        deleteAppPrivateLibraryCopy(context.filesDir, document.uri)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // region Textbook outline (P0.3A)

    suspend fun listOutlineNodes(documentId: String): List<TextbookOutlineNodeEntity> =
        withContext(Dispatchers.IO) {
            textbookOutlineNodeDao.listForDocument(documentId)
        }

    suspend fun upsertOutlineNode(
        documentId: String,
        id: String? = null,
        parentId: String?,
        type: TextbookOutlineNodeType,
        title: String,
        startPageIndex: Int,
        endPageIndex: Int,
        sortOrder: Int = 0,
        pageCount: Int,
    ): TextbookOutlineNodeEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (start, end) = TextbookOutlineResolver.normalizeRange(
            startPageIndex,
            endPageIndex,
            pageCount,
        )
        val existingId = id?.takeIf { it.isNotBlank() }
        val entity = if (existingId != null) {
            val old = textbookOutlineNodeDao.getById(existingId)
            TextbookOutlineNodeEntity(
                id = existingId,
                documentId = documentId,
                parentId = parentId,
                type = type.name,
                title = title.trim().ifBlank { type.displayLabelZh() },
                startPageIndex = start,
                endPageIndex = end,
                sortOrder = sortOrder,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                source = old?.source ?: OutlineNodeSource.MANUAL.name,
                importKey = old?.importKey,
            )
        } else {
            TextbookOutlineNodeEntity(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                parentId = parentId,
                type = type.name,
                title = title.trim().ifBlank { type.displayLabelZh() },
                startPageIndex = start,
                endPageIndex = end,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now,
                source = OutlineNodeSource.MANUAL.name,
                importKey = null,
            )
        }
        // Prevent setting parent to self or descendant.
        if (entity.parentId != null) {
            val all = textbookOutlineNodeDao.listForDocument(documentId)
            val forbidden = TextbookOutlineResolver.subtreeIds(entity.id, all + entity)
            if (entity.parentId in forbidden) {
                textbookOutlineNodeDao.upsert(entity.copy(parentId = null))
                return@withContext entity.copy(parentId = null)
            }
        }
        textbookOutlineNodeDao.upsert(entity)
        entity
    }

    /** Delete node and all descendants. */
    suspend fun deleteOutlineNode(documentId: String, nodeId: String) {
        withContext(Dispatchers.IO) {
            val all = textbookOutlineNodeDao.listForDocument(documentId)
            val ids = TextbookOutlineResolver.subtreeIds(nodeId, all)
            ids.forEach { textbookOutlineNodeDao.deleteById(it) }
        }
    }

    suspend fun clearAllOutlineNodes(documentId: String) {
        withContext(Dispatchers.IO) {
            textbookOutlineNodeDao.deleteAllForDocument(documentId)
        }
    }

    suspend fun insertOutlineNodes(nodes: List<TextbookOutlineNodeEntity>) {
        withContext(Dispatchers.IO) {
            nodes.forEach { textbookOutlineNodeDao.upsert(it) }
        }
    }

    /**
     * Import embedded PDF outline drafts.
     * @return number of nodes written (0 if idempotent skip).
     */
    suspend fun importEmbeddedOutline(
        documentId: String,
        drafts: List<com.markleaf.app.pdf.EmbeddedOutlineNodeDraft>,
        mode: com.markleaf.app.pdf.OutlineImportMode,
    ): Int = withContext(Dispatchers.IO) {
        val existing = textbookOutlineNodeDao.listForDocument(documentId)
        if (com.markleaf.app.pdf.PdfEmbeddedOutlineImporter.isAlreadyImported(drafts, existing) &&
            mode == com.markleaf.app.pdf.OutlineImportMode.MERGE
        ) {
            return@withContext 0
        }
        // REPLACE always rewrites; MERGE may skip all
        if (mode == com.markleaf.app.pdf.OutlineImportMode.REPLACE) {
            // REPLACE: if identical embedded set already present and no MANUAL nodes, skip
            val onlyEmbedded = existing.isNotEmpty() &&
                existing.all { it.source == com.markleaf.app.data.local.OutlineNodeSource.PDF_EMBEDDED.name }
            if (onlyEmbedded &&
                com.markleaf.app.pdf.PdfEmbeddedOutlineImporter.isAlreadyImported(drafts, existing) &&
                existing.size == drafts.size
            ) {
                return@withContext 0
            }
            textbookOutlineNodeDao.deleteAllForDocument(documentId)
            val plan = com.markleaf.app.pdf.PdfEmbeddedOutlineImporter.buildReplace(documentId, drafts)
            plan.nodes.forEach { textbookOutlineNodeDao.upsert(it) }
            return@withContext plan.inserted
        }
        val plan = com.markleaf.app.pdf.PdfEmbeddedOutlineImporter.buildMerge(
            documentId = documentId,
            drafts = drafts,
            existing = existing,
        )
        plan.nodes.forEach { textbookOutlineNodeDao.upsert(it) }
        plan.inserted
    }

    // endregion

    suspend fun listPrintPageAnchors(documentId: String): List<PdfPrintPageAnchorEntity> =
        withContext(Dispatchers.IO) {
            pdfPrintPageAnchorDao.listForDocument(documentId)
        }

    /**
     * Set or replace the textbook print page for a PDF page (0-based index).
     */
    suspend fun upsertPrintPageAnchor(
        documentId: String,
        pdfPageIndex: Int,
        printPageNumber: Int,
    ) {
        withContext(Dispatchers.IO) {
            pdfPrintPageAnchorDao.upsert(
                PdfPrintPageAnchorEntity(
                    documentId = documentId,
                    pdfPageIndex = pdfPageIndex.coerceAtLeast(0),
                    printPageNumber = printPageNumber.coerceAtLeast(1),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clearPrintPageAnchor(documentId: String, pdfPageIndex: Int) {
        withContext(Dispatchers.IO) {
            pdfPrintPageAnchorDao.delete(documentId, pdfPageIndex)
        }
    }

    /** Bump lastOpenedAt so Home list reorders by recent open. */
    suspend fun touchLastOpenedAt(id: String) {
        withContext(Dispatchers.IO) {
            documentDao.updateLastOpenedAt(id, System.currentTimeMillis())
        }
    }

    /**
     * Persist title (first H1 when present) and TOC heading count after parse.
     */
    suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
        withContext(Dispatchers.IO) {
            documentDao.updateParseMetadata(
                id = id,
                title = title,
                headingCount = headingCount,
            )
            documentDao.getById(id)?.let { searchRepository.index(it) }
        }
    }

    /**
     * Persist LazyColumn scroll position and reading progress (0f–1f) for Markdown.
     */
    suspend fun updateReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
    ) {
        withContext(Dispatchers.IO) {
            documentDao.updateReadingPosition(
                id = id,
                lastScrollIndex = lastScrollIndex.coerceAtLeast(0),
                lastScrollOffset = lastScrollOffset.coerceAtLeast(0),
                readingProgress = readingProgress.coerceIn(0f, 1f),
            )
        }
    }

    /**
     * Persist PDF page progress (0-based page index + total page count).
     */
    suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    ) {
        withContext(Dispatchers.IO) {
            documentDao.updatePdfReadingPosition(
                id = id,
                pageIndex = pageIndex.coerceAtLeast(0),
                intraPageFractionEncoded = intraPageFractionEncoded.coerceAtLeast(0),
                pageCount = pageCount.coerceAtLeast(0),
                readingProgress = readingProgress.coerceIn(0f, 1f),
            )
        }
    }

    suspend fun updatePdfViewportSession(id: String, session: com.markleaf.app.pdf.PdfViewportSessionState) {
        withContext(Dispatchers.IO) {
            documentDao.updatePdfViewportSession(
                id, session.version, session.readingMode.toStorage(), session.pageIndex,
                session.pageAnchorXFraction, session.pageAnchorYFraction,
                session.viewportAnchorXFraction, session.viewportAnchorYFraction,
                session.scale, session.generation,
            )
        }
    }

    /**
     * P0A2/P0A3: atomically persist PDF reading position + viewport session in
     * a single Room transaction. Used by [com.markleaf.app.pdf.FinalPdfProgressFlusher]'s
     * writeFn so that a crash between the two DAO updates cannot leave the
     * database in an inconsistent state (position written, session lost — or
     * vice versa). The coroutine Mutex in the flusher serializes writeFn calls
     * but does NOT provide DB atomicity; this transaction does.
     *
     * Either both updates land or neither does. Schema is unchanged.
     *
     * P0A3: position may be null for a session-only update (e.g. zoom settle
     * where readingPosition is unchanged). In that case only the viewport
     * session is written, but still inside the same transaction so the
     * flusher's writeFn always has a single atomic DB boundary.
     */
    suspend fun updatePdfProgressSnapshotAtomic(
        id: String,
        pageIndex: Int?,
        intraPageFractionEncoded: Int?,
        pageCount: Int?,
        readingProgress: Float?,
        session: com.markleaf.app.pdf.PdfViewportSessionState?,
    ) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                pageIndex?.let { pi ->
                    documentDao.updatePdfReadingPosition(
                        id = id,
                        pageIndex = pi.coerceAtLeast(0),
                        intraPageFractionEncoded = (intraPageFractionEncoded ?: 0).coerceAtLeast(0),
                        pageCount = (pageCount ?: 0).coerceAtLeast(0),
                        readingProgress = (readingProgress ?: 0f).coerceIn(0f, 1f),
                    )
                }
                session?.let { s ->
                    documentDao.updatePdfViewportSession(
                        id, s.version, s.readingMode.toStorage(), s.pageIndex,
                        s.pageAnchorXFraction, s.pageAnchorYFraction,
                        s.viewportAnchorXFraction, s.viewportAnchorYFraction,
                        s.scale, s.generation,
                    )
                }
            }
        }
    }

    /**
     * Reads the complete UTF-8 Markdown document from a SAF or app-private URI.
     * Oversized input fails explicitly instead of silently returning partial text.
     * Runs on [Dispatchers.IO]; never throws to callers.
     */
    suspend fun readDocumentText(uriString: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val input = openUriInputStream(uri)
                    ?: return@withContext Result.failure(DocumentReadException.NotFound)

                input.use { stream ->
                    Result.success(readUtf8TextFully(stream))
                }
            } catch (e: DocumentReadException.TooLarge) {
                Result.failure(e)
            } catch (_: SecurityException) {
                Result.failure(DocumentReadException.PermissionDenied)
            } catch (_: FileNotFoundException) {
                Result.failure(DocumentReadException.NotFound)
            } catch (e: Exception) {
                Result.failure(DocumentReadException.Failed(e))
            }
        }

    private fun openUriInputStream(uri: Uri): java.io.InputStream? {
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> {
                val path = uri.path ?: return null
                val f = File(path)
                if (f.isFile) FileInputStream(f) else null
            }
            else -> context.contentResolver.openInputStream(uri)
        }
    }

    /**
     * Import a document via SAF URI: persist read permission, validate type,
     * read metadata, and upsert into Room. Runs on [Dispatchers.IO].
     */
    suspend fun importDocument(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            takeReadPermission(uri)

            val metadata = applyFileUriEnrichment(
                com.markleaf.app.share.ProviderMetadataSource.collect(context.contentResolver, uri),
                uri,
            )
            val mimeType = metadata.mimeType
            val pathSegment = metadata.lastPathSegment
            val resolvedMeta = com.markleaf.app.share.ExternalUriMetadata.resolve(
                metadata,
                intentMime = mimeType,
            )
            val displayName = resolvedMeta.displayName

            // Single acceptance authority (import policy batch 2): the former
            // isAllowedFileName OR-branches were a proven subset of this gate.
            if (!DocumentType.isAllowedImport(mimeType, displayName, pathSegment)) {
                return@withContext ImportResult.UnsupportedFormat
            }

            // Unified 200 MB source limit (import policy batch 3). Shared
            // constant with the VIEW/SEND path; enforced on the provider hint
            // when available, otherwise by a bounded drain of the source stream
            // below (aborts past the cap, so worst-case read is cap + chunk).
            val knownSize = resolvedMeta.sizeBytesHint
                ?: when (uri.scheme?.lowercase(Locale.ROOT)) {
                    "file" -> uri.path?.let { File(it).length().takeIf { len -> len > 0 } }
                    else -> null
                }
            if (knownSize != null && knownSize > com.markleaf.app.share.ExternalDocumentImporter.MAX_BYTES) {
                return@withContext ImportResult.SourceTooLarge
            }

            val fileNameFromUri = when (uri.scheme?.lowercase(Locale.ROOT)) {
                "file" -> uri.path?.let { File(it).name }
                else -> null
            }
            val isZip = DocumentType.isZipFileName(displayName) ||
                DocumentType.isZipFileName(pathSegment) ||
                DocumentType.isZipFileName(fileNameFromUri) ||
                DocumentType.isZipMime(mimeType)

            val uriString = uri.toString()
            val id = stableUriId(uriString)
            val now = System.currentTimeMillis()
            val existing = documentDao.getById(id)

            if (isZip) {
                val zipDisplay = listOf(displayName, fileNameFromUri, pathSegment)
                    .firstOrNull { DocumentType.isZipFileName(it) }
                    ?: displayName
                return@withContext importZipPackage(
                    sourceUri = uri,
                    id = id,
                    displayName = zipDisplay,
                    mimeType = mimeType,
                    fileSize = resolvedMeta.sizeBytesHint
                        ?: fileNameFromUri?.let { uri.path?.let { p -> File(p).length().takeIf { len -> len > 0 } } },
                    now = now,
                    existing = existing,
                )
            }

            // Verify the URI is readable; when no usable size metadata exists,
            // the same pass bound-verifies the source against the shared cap
            // (applies to every document type, zip included — extraction caps
            // in ZipPackageImporter remain independent, uncompressed-side bounds).
            openUriInputStream(uri)?.use { input ->
                if (knownSize == null) {
                    val buf = ByteArray(64 * 1024)
                    var seen = 0L
                    while (true) {
                        val n = try {
                            input.read(buf)
                        } catch (_: Exception) {
                            -1
                        }
                        if (n < 0) break
                        seen += n
                        if (seen > com.markleaf.app.share.ExternalDocumentImporter.MAX_BYTES) {
                            return@withContext ImportResult.SourceTooLarge
                        }
                    }
                }
            } ?: return@withContext ImportResult.ReadFailed

            val documentType = DocumentType.resolve(mimeType, displayName, pathSegment)
            // Note: the former "markdown requires known text extension" re-check
            // is unreachable after policy unification — a MARKDOWN-typed input
            // can only pass the gate with document extensions on its (resolved)
            // names, either via branch 1 or via ensureExtension normalization.

            val resolvedSize = knownSize

            val entity = if (existing != null) {
                // Same file again: refresh open time + metadata; keep progress/title.
                existing.copy(
                    displayName = displayName,
                    uri = uriString,
                    mimeType = mimeType,
                    fileSize = resolvedSize ?: existing.fileSize,
                    lastOpenedAt = now,
                    documentType = documentType.name,
                )
            } else {
                DocumentEntity(
                    id = id,
                    displayName = displayName,
                    uri = uriString,
                    mimeType = mimeType,
                    fileSize = resolvedSize,
                    lastOpenedAt = now,
                    createdAt = now,
                    lastScrollIndex = 0,
                    lastScrollOffset = 0,
                    readingProgress = 0f,
                    title = null,
                    headingCount = 0,
                    documentType = documentType.name,
                    pageCount = 0,
                )
            }

            upsertAndIndex(entity)
            ImportResult.Success(entity)
        } catch (_: SecurityException) {
            ImportResult.ReadFailed
        } catch (_: Exception) {
            ImportResult.ReadFailed
        }
    }

    /**
     * Extract textbook ZIP into app-private storage; [DocumentEntity.uri] points at entry .md
     * (file://) so existing progress + reader work without schema migration.
     */
    private suspend fun importZipPackage(
        sourceUri: Uri,
        id: String,
        displayName: String,
        mimeType: String?,
        fileSize: Long?,
        now: Long,
        existing: DocumentEntity?,
    ): ImportResult {
        val destDir = File(context.filesDir, "packages/$id")
        val extracted = try {
            // Must support file:// (share-intake copy) as well as content:// (FAB SAF).
            openUriInputStream(sourceUri)?.use { stream ->
                ZipPackageImporter.extract(stream, destDir)
            } ?: return ImportResult.ReadFailed
        } catch (_: SecurityException) {
            destDir.deleteRecursively()
            return ImportResult.ReadFailed
        } catch (_: IllegalArgumentException) {
            destDir.deleteRecursively()
            return ImportResult.UnsupportedFormat
        } catch (_: Exception) {
            destDir.deleteRecursively()
            return ImportResult.ReadFailed
        }

        val entry = extracted.entryFile
        val packageRoot = extracted.packageRoot
        // Document URI = package entry Markdown (assets/ stays sibling under packageRoot).
        val entryUri = Uri.fromFile(entry).toString()

        val mdText = try {
            entry.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
        val h1Title = ZipPackageImporter.extractFirstH1(mdText)
        val entrySize = entry.length().takeIf { it > 0 }
        val packageSize = ZipPackageImporter.packageTotalSize(packageRoot).takeIf { it > 0 }
        val resolvedSize = entrySize ?: packageSize ?: fileSize

        val zipLabel = displayName
            .removeSuffix(".zip").removeSuffix(".ZIP")
            .trim()
            .takeIf { it.isNotEmpty() && it != "未命名文档" && !it.equals("shared", true) }
        val listName = zipLabel
            ?: entry.name.takeIf { it.isNotBlank() }
            ?: "Markdown 教材包"

        val entity = if (existing != null) {
            existing.copy(
                displayName = listName,
                uri = entryUri,
                mimeType = mimeType ?: "application/zip",
                fileSize = resolvedSize,
                lastOpenedAt = now,
                documentType = DocumentType.MARKDOWN_PACKAGE.name,
                title = h1Title ?: existing.title,
            )
        } else {
            DocumentEntity(
                id = id,
                displayName = listName,
                uri = entryUri,
                mimeType = mimeType ?: "application/zip",
                fileSize = resolvedSize,
                lastOpenedAt = now,
                createdAt = now,
                lastScrollIndex = 0,
                lastScrollOffset = 0,
                readingProgress = 0f,
                title = h1Title,
                headingCount = 0,
                documentType = DocumentType.MARKDOWN_PACKAGE.name,
                pageCount = 0,
            )
        }
        upsertAndIndex(entity)
        return ImportResult.Success(entity)
    }

    private fun takeReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Provider may not support persistable grants; continue if stream is readable.
        }
    }

    /**
     * A3-3a: provider metadata probing is owned by
     * [com.markleaf.app.share.ProviderMetadataSource.collect]; this repository-side
     * step only re-applies the legacy file:// enrichment (name from path, size from
     * the real file) on top of the collected probe — identical conditions to the
     * former private queryMetadata fallback.
     */
    internal fun applyFileUriEnrichment(
        base: com.markleaf.app.share.ProviderMetadata,
        uri: Uri,
    ): com.markleaf.app.share.ProviderMetadata {
        if (!uri.scheme.equals("file", ignoreCase = true)) return base
        val f = uri.path?.let { File(it) }
        var displayName = base.displayName
        var size = base.size
        if (displayName.isNullOrBlank()) displayName = f?.name
        if ((size == null || size < 0L) && f != null && f.isFile) size = f.length()
        return base.copy(displayName = displayName, size = size)
    }

    companion object {
        /**
         * Full-read safety ceiling. 32 MiB is far beyond the old 200 KiB preview
         * while keeping the current full-document parser inside a bounded heap.
         */
        const val MAX_MARKDOWN_BYTES: Int = 32 * 1024 * 1024

        fun stableUriId(uriString: String): String {
            val digest = MessageDigest
                .getInstance("SHA-256")
                .digest(uriString.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
        }

        /** Progress 0f..1f from 0-based page index. */
        fun pdfReadingProgress(pageIndex: Int, pageCount: Int): Float {
            if (pageCount <= 0) return 0f
            if (pageCount == 1) return if (pageIndex > 0) 1f else 0f
            val idx = pageIndex.coerceIn(0, pageCount - 1)
            return (idx.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
        }
    }
}

internal fun readUtf8TextFully(
    input: InputStream,
    maxBytes: Int = DocumentRepository.MAX_MARKDOWN_BYTES,
): String {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (read > maxBytes - total) {
            throw DocumentReadException.TooLarge(maxBytes)
        }
        output.write(buffer, 0, read)
        total += read
    }
    return output.toString(Charsets.UTF_8.name())
}

sealed class ImportResult {
    data class Success(val document: DocumentEntity) : ImportResult()
    data object UnsupportedFormat : ImportResult()

    /**
     * Source exceeded the shared 200 MB import limit
     * ([com.markleaf.app.share.ExternalDocumentImporter.MAX_BYTES]) — the
     * Path-A twin of PrepareOutcome.TooLarge (import policy batch 3).
     */
    data object SourceTooLarge : ImportResult()
    data object ReadFailed : ImportResult()
}

sealed class DocumentReadException : Exception() {
    data object NotFound : DocumentReadException()
    data object PermissionDenied : DocumentReadException()
    data class TooLarge(val maxBytes: Int) : DocumentReadException()
    data class Failed(val causeError: Throwable? = null) : DocumentReadException() {
        override val cause: Throwable?
            get() = causeError
    }
}
