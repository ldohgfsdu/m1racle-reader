package com.m1racle.reader.data.repository

import com.m1racle.reader.pdf.edit.PdfSourceWriteStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import java.io.FileOutputStream
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentAliasDao
import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.PdfPrintPageAnchorDao
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.OutlineNodeSource
import com.m1racle.reader.data.local.TextbookOutlineNodeDao
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeType
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubException
import com.m1racle.reader.epub.EpubPackageImporter
import com.m1racle.reader.packageimport.ZipPackageImporter
import com.m1racle.reader.pdf.TextbookOutlineResolver
import com.m1racle.reader.identity.ContentIdentityDecision
import com.m1racle.reader.identity.ContentIdentityInput
import com.m1racle.reader.identity.ContentIdentityResolution
import com.m1racle.reader.identity.ContentIdentityResolver
import com.m1racle.reader.identity.PreparedContentIdentity
import com.m1racle.reader.identity.DocumentAliasKeys
import com.m1racle.reader.identity.RoomSidecarAliasMigrationTransaction
import com.m1racle.reader.identity.SidecarIdentityMigrationFiles
import com.m1racle.reader.identity.SidecarIdentityMigrationV1
import com.m1racle.reader.identity.SidecarIdentityV1Reader
import com.m1racle.reader.share.ExternalDocumentImporter
import com.m1racle.reader.share.ExternalUriMetadata
import com.m1racle.reader.share.ProviderMetadata
import com.m1racle.reader.share.ProviderMetadataSource
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
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
    private val documentAliasDao: DocumentAliasDao =
        (db as AppDatabase).documentAliasDao(),
    internal val contentIdentityResolver: ContentIdentityResolver =
        ContentIdentityResolver(context, documentDao),
    internal val sidecarIdentityMigration: SidecarIdentityMigrationV1 =
        SidecarIdentityMigrationV1(
            reader = SidecarIdentityV1Reader(),
            files = SidecarIdentityMigrationFiles(context.filesDir),
            transaction = RoomSidecarAliasMigrationTransaction(
                database = db,
                documentDao = documentDao,
                aliasDao = documentAliasDao,
            ),
        ),
    private val importTransactionRunner: ImportTransactionRunner =
        RoomImportTransactionRunner(db),
) {

    private val identityCommitMutex = Mutex()

    fun observeRecentDocuments(): Flow<List<DocumentEntity>> =
        documentDao.observeRecentDocuments()

    private val pdfSourceWrites by lazy {
        PdfSourceWriteStore(File(context.filesDir,"pdf-source-writes"),
            read = { value -> context.contentResolver.openInputStream(Uri.parse(value)) ?: error("无法读取源文件") },
            write = { value -> context.contentResolver.openOutputStream(Uri.parse(value),"wt") ?: error("无法写入源文件，请重新授权") },
        )
    }

    /** A new immutable revision plus a Room pointer update preserves id and every child row. */
    suspend fun savePdfEdits(id: String, expectedHash: String, candidate: File, sourceTarget: Uri? = null): DocumentEntity =
        withContext(Dispatchers.IO) {
            ensureIdentityRuntimeReady()
            identityCommitMutex.withLock {
                if(sourceTarget!=null) check(pdfSourceWrites.pending().none { it.documentId == id }) { "存在未完成的源文件恢复，请先导出备份；当前修改仍可保存到书库" }
                val before = documentDao.getById(id) ?: error("文档已移除")
                check(before.isPdf) { "只能保存 PDF 正文" }
                val actual = context.contentResolver.openInputStream(Uri.parse(before.uri))?.use(PdfSourceWriteStore::sha256)
                check(actual == expectedHash) { "书库文档已发生变化，请重新打开后编辑" }
                val nextHash = PdfSourceWriteStore.sha256(candidate)
                val owner = contentIdentityResolver.findExistingDocument(nextHash)
                check(owner == null || owner.id == id) { "相同内容已在书库中，请使用另存副本" }
                val folder = File(context.filesDir,"library/$nextHash/${UUID.randomUUID()}")
                check(folder.mkdirs()) { "无法创建保存文件" }
                val revision = File(folder,ExternalDocumentImporter.sanitizeFileName(before.displayName,"pdf"))
                var ticket: PdfSourceWriteStore.Ticket? = null
                var writing = false
                var committed = false
                try {
                    FileOutputStream(revision).use { out -> candidate.inputStream().use { it.copyTo(out) }; out.fd.sync() }
                    check(PdfSourceWriteStore.sha256(revision) == nextHash) { "保存文件核验失败" }
                    if (sourceTarget != null) {
                        ticket = pdfSourceWrites.prepare(id,sourceTarget.toString(),before.sourceSha256 ?: expectedHash,revision)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    // Once external truncation starts, finish commit/rollback even if the editor exits.
                    val result = withContext(NonCancellable) {
                        ticket?.let { pdfSourceWrites.replace(it) { writing = true } }
                        importTransactionRunner.run {
                            val current = documentDao.getById(id) ?: error("文档已移除")
                            check(current.uri == before.uri && current.contentSha256 == before.contentSha256) { "文档已发生变化，未保存" }
                            val updated = current.copy(uri=Uri.fromFile(revision).toString(),contentSha256=nextHash,
                                fileSize=revision.length(),lastOpenedAt=System.currentTimeMillis(),
                                sourceUri=sourceTarget?.toString() ?: current.sourceUri,
                                sourceSha256=if(sourceTarget!=null) nextHash else current.sourceSha256 ?: expectedHash)
                            documentDao.updateDocument(updated)
                            searchRepository.index(updated,indexedAt=System.currentTimeMillis())
                            updated
                        }.also { committed = true }
                    }
                    ticket?.let(pdfSourceWrites::finish)
                    runCatching { deleteAppPrivateLibraryCopy(context.filesDir,before.uri) }
                    result
                } catch (error: Exception) {
                    if (!committed) {
                        val pending = ticket
                        if (pending != null && writing) {
                            try {
                                withContext(NonCancellable) { pdfSourceWrites.restore(pending) }
                                pdfSourceWrites.finish(pending)
                            } catch (restore: Exception) {
                                throw java.io.IOException("保存未完成，源文件备份已保留。可导出备份后恢复。",restore)
                            }
                        } else pending?.let(pdfSourceWrites::finish)
                        folder.deleteRecursively()
                    }
                    throw error
                }
            }
        }

    /** Reconcile an interrupted external write using the durable Room commit as authority. */
    suspend fun recoverPdfSourceWrites(documentId: String? = null): List<String> = withContext(Dispatchers.IO) {
        identityCommitMutex.withLock {
            pdfSourceWrites.pending().filter { documentId == null || it.documentId == documentId }.mapNotNull { ticket ->
                try {
                    val document = documentDao.getById(ticket.documentId)
                    val committed = document?.sourceUri == ticket.uri && document.sourceSha256 == ticket.after
                    pdfSourceWrites.recover(ticket,committed)
                    if (!committed) deleteAppPrivateLibraryCopy(context.filesDir,Uri.fromFile(ticket.candidate).toString())
                    null
                } catch (e: Exception) { e.message ?: "源文件恢复失败，备份已保留" }
            }
        }
    }

    suspend fun pdfSourceRecoveryBackup(id: String): File? = withContext(Dispatchers.IO) {
        identityCommitMutex.withLock { pdfSourceWrites.pending().firstOrNull { it.documentId == id }?.backup }
    }

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

    internal suspend fun ensureIdentityRuntimeReady() {
        sidecarIdentityMigration.ensureCompleted()
    }

    suspend fun touchLastOpened(id: String, time: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            documentDao.updateLastOpenedAt(id, time)
        }
    }

    /**
     * Read-only location candidate for the external-open confirmation card.
     * The candidate never skips [ContentIdentityResolver.resolve]; bytes are
     * always staged and verified before [commitResolvedImport].
     */
    internal suspend fun findBySourceAlias(
        sourceUri: Uri,
        canonicalExternalKey: String,
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        ensureIdentityRuntimeReady()
        for (key in sourceAliasKeys(sourceUri, canonicalExternalKey)) {
            val documentId = documentAliasDao.getByKey(key)?.documentId ?: continue
            val document = documentDao.getById(documentId)
            if (document != null) return@withContext document
        }
        null
    }

    /** Host-test seam that keeps hashing/lookup inside ContentIdentityResolver. */
    internal fun contentIdentityResolverWithSource(
        openSource: (Uri) -> InputStream?,
    ): ContentIdentityResolver = ContentIdentityResolver(
        documentDao = documentDao,
        stagingDirectory = File(context.filesDir, "identity-staging"),
        openSource = openSource,
    )

    /**
     * The sole document-identity commit boundary.
     *
     * It revalidates exact content through [ContentIdentityResolver] inside the
     * configured Room transaction, preserves every existing primary key and
     * child row, creates UUID ids only for genuinely new content, and attaches
     * only byte-verified aliases. It never calls the REPLACE upsert.
     */
    suspend fun commitResolvedImport(
        request: ResolvedImportRequest,
    ): ResolvedImportCommit = withContext(Dispatchers.IO) {
        try {
            ensureIdentityRuntimeReady()
        } catch (error: Throwable) {
            request.content.close()
            throw error
        }
        identityCommitMutex.withLock {
            val prepared = request.content
            var payload: MaterializedImportPayload? = null
            var payloadOwnedByCommittedDocument = false
            try {
                check(prepared.stagedFile.isFile) { "Resolved import staging file is missing" }

                var earlyConstraint: SQLiteConstraintException? = null
                for (attempt in 0 until MAX_IDENTITY_COMMIT_ATTEMPTS) {
                    try {
                        val firstDecision = importTransactionRunner.run {
                            reuseOrEnrichResolvedImport(request, System.currentTimeMillis())
                        }
                        if (firstDecision != null) {
                            return@withLock firstDecision
                        }
                        earlyConstraint = null
                        break
                    } catch (conflict: SQLiteConstraintException) {
                        earlyConstraint = conflict
                    }
                }
                if (earlyConstraint != null) throw earlyConstraint

                // New ids are minted only here, after a fresh transactional miss.
                val newDocumentId = UUID.randomUUID().toString()
                payload = materializeImportPayload(request, newDocumentId)

                var lastConstraint: SQLiteConstraintException? = null
                repeat(MAX_IDENTITY_COMMIT_ATTEMPTS) {
                    try {
                        val committed = importTransactionRunner.run {
                            reuseOrEnrichResolvedImport(
                                request = request,
                                now = System.currentTimeMillis(),
                            ) ?: insertResolvedImport(
                                request = request,
                                documentId = newDocumentId,
                                payload = requireNotNull(payload),
                                now = System.currentTimeMillis(),
                            )
                        }
                        if (committed.document.id == newDocumentId) {
                            payloadOwnedByCommittedDocument = true
                        }
                        return@withLock committed
                    } catch (conflict: SQLiteConstraintException) {
                        // ABORT rolls back the whole attempt. Requery/re-evaluate
                        // in a fresh transaction; a same-hash winner is reused.
                        lastConstraint = conflict
                    }
                }
                throw requireNotNull(lastConstraint)
            } finally {
                prepared.close()
                if (!payloadOwnedByCommittedDocument) {
                    payload?.deleteOperationOwnedFiles()
                }
            }
        }
    }

    private suspend fun reuseOrEnrichResolvedImport(
        request: ResolvedImportRequest,
        now: Long,
    ): ResolvedImportCommit? {
        val prepared = request.content
        val hashOwner = contentIdentityResolver.findExistingDocument(prepared.contentSha256)
        if (hashOwner != null) {
            // Equal bytes may come from different external files. Write back to the
            // source the user actually opened, rather than the first imported alias.
            val opened = hashOwner.copy(lastOpenedAt=now,
                sourceUri=if(hashOwner.isPdf) prepared.sourceUri.toString() else hashOwner.sourceUri,
                sourceSha256=if(hashOwner.isPdf) prepared.contentSha256 else hashOwner.sourceSha256)
            documentDao.updateDocument(opened)
            bindVerifiedSourceAliases(request, hashOwner.id, now)
            return ResolvedImportCommit(
                document = opened,
                reusedExisting = true,
            )
        }

        val resolvedCandidate =
            (prepared.decision as? ContentIdentityDecision.ExistingDocumentFound)?.document
        if (resolvedCandidate != null && resolvedCandidate.contentSha256 == null) {
            val current = documentDao.getById(resolvedCandidate.id)
            if (current != null && current.contentSha256 == null) {
                val updated = documentDao.assignContentSha256IfNull(
                    id = current.id,
                    contentSha256 = prepared.contentSha256,
                    lastOpenedAt = now,
                )
                check(updated == 1) { "Verified legacy identity candidate changed during commit" }
                bindVerifiedSourceAliases(request, current.id, now)
                return ResolvedImportCommit(
                    document = current.copy(
                        contentSha256 = prepared.contentSha256,
                        lastOpenedAt = now,
                    ),
                    reusedExisting = true,
                )
            }
        }

        var aliasCandidate: DocumentEntity? = null
        for (key in sourceAliasKeys(prepared.sourceUri, request.canonicalExternalKey)) {
            val documentId = documentAliasDao.getByKey(key)?.documentId ?: continue
            val document = documentDao.getById(documentId) ?: continue
            if (document.contentSha256 == null) {
                aliasCandidate = document
                break
            }
        }
        val legacyCandidate = aliasCandidate ?: return null

        val updated = documentDao.assignContentSha256IfNull(
            id = legacyCandidate.id,
            contentSha256 = prepared.contentSha256,
            lastOpenedAt = now,
        )
        check(updated == 1) { "Legacy identity candidate changed during commit" }
        bindVerifiedSourceAliases(request, legacyCandidate.id, now)
        return ResolvedImportCommit(
            document = legacyCandidate.copy(
                contentSha256 = prepared.contentSha256,
                lastOpenedAt = now,
            ),
            reusedExisting = true,
        )
    }

    private suspend fun insertResolvedImport(
        request: ResolvedImportRequest,
        documentId: String,
        payload: MaterializedImportPayload,
        now: Long,
    ): ResolvedImportCommit {
        val prepared = request.content
        val entity = DocumentEntity(
            id = documentId,
            displayName = payload.displayName,
            uri = payload.uri,
            mimeType = payload.mimeType,
            fileSize = payload.fileSize,
            lastOpenedAt = now,
            createdAt = now,
            lastScrollIndex = 0,
            lastScrollOffset = 0,
            readingProgress = 0f,
            title = payload.title,
            headingCount = 0,
            documentType = request.documentType.name,
            pageCount = 0,
            contentSha256 = prepared.contentSha256,
            sourceUri = if (request.documentType == DocumentType.PDF) prepared.sourceUri.toString() else null,
            sourceSha256 = if (request.documentType == DocumentType.PDF) prepared.contentSha256 else null,
        )
        documentDao.insertDocument(entity)
        searchRepository.index(entity, indexedAt = now)
        bindVerifiedSourceAliases(request, documentId, now)
        return ResolvedImportCommit(document = entity, reusedExisting = false)
    }

    private suspend fun bindVerifiedSourceAliases(
        request: ResolvedImportRequest,
        documentId: String,
        now: Long,
    ) {
        val primaryKey = DocumentAliasKeys.uriV1(request.canonicalExternalKey)
        bindAlias(primaryKey, DocumentAliasKeys.URI_V1_KIND, documentId, now)

        // v13 migration created this key for old Path-A ids. It remains a
        // lookup/rebind bridge only; new imports never create legacy aliases.
        val legacyKey = DocumentAliasKeys.legacyRawUriV1(request.content.sourceUri.toString())
        if (documentAliasDao.getByKey(legacyKey) != null) {
            bindAlias(legacyKey, DocumentAliasKeys.LEGACY_RAW_URI_V1_KIND, documentId, now)
        }
    }

    private suspend fun bindAlias(
        aliasKey: String,
        kind: String,
        documentId: String,
        now: Long,
    ) {
        val existing = documentAliasDao.getByKey(aliasKey)
        if (existing == null) {
            documentAliasDao.insertAlias(
                DocumentAliasEntity(
                    aliasKey = aliasKey,
                    documentId = documentId,
                    kind = kind,
                    createdAt = now,
                    lastSeenAt = now,
                ),
            )
        } else {
            documentAliasDao.updateAlias(
                existing.copy(
                    documentId = documentId,
                    kind = kind,
                    lastSeenAt = now,
                ),
            )
        }
    }

    private fun materializeImportPayload(
        request: ResolvedImportRequest,
        documentId: String,
    ): MaterializedImportPayload {
        val prepared = request.content
        if (request.documentType == DocumentType.MARKDOWN_PACKAGE) {
            return materializeZipPackage(prepared, documentId)
        }
        if (request.payloadPolicy == ImportPayloadPolicy.SOURCE_URI) {
            return MaterializedImportPayload(
                uri = prepared.sourceUri.toString(),
                displayName = prepared.metadata.displayName,
                mimeType = prepared.metadata.mimeType,
                fileSize = prepared.byteCount,
                title = null,
                operationOwnedPath = null,
            )
        }

        val destinationDirectory = File(
            context.filesDir,
            "library/${prepared.contentSha256}/$documentId",
        )
        if (!destinationDirectory.mkdirs() && !destinationDirectory.isDirectory) {
            throw IllegalStateException("Unable to create managed import directory")
        }
        val safeName = ExternalDocumentImporter.sanitizeFileName(
            prepared.metadata.displayName,
            ExternalUriMetadata.extensionForMime(prepared.metadata.mimeType) ?: "bin",
        )
        val destination = File(destinationDirectory, safeName)
        try {
            check(!destination.exists()) { "Managed import destination already exists" }
            if (!prepared.stagedFile.renameTo(destination)) {
                prepared.stagedFile.copyTo(destination, overwrite = false)
                prepared.stagedFile.delete()
            }
            check(destination.isFile && destination.length() == prepared.byteCount) {
                "Managed import length mismatch"
            }
            return MaterializedImportPayload(
                uri = Uri.fromFile(destination).toString(),
                displayName = prepared.metadata.displayName,
                mimeType = prepared.metadata.mimeType,
                fileSize = prepared.byteCount,
                title = null,
                operationOwnedPath = destinationDirectory,
            )
        } catch (error: Exception) {
            destinationDirectory.deleteRecursively()
            throw error
        }
    }

    private fun materializeZipPackage(
        prepared: PreparedContentIdentity,
        documentId: String,
    ): MaterializedImportPayload {
        val destinationDirectory = File(context.filesDir, "packages/$documentId")
        val extracted = try {
            prepared.stagedFile.inputStream().use { stream ->
                ZipPackageImporter.extract(stream, destinationDirectory)
            }
        } catch (invalid: IllegalArgumentException) {
            destinationDirectory.deleteRecursively()
            throw ResolvedImportCommitException.UnsupportedFormat(invalid)
        } catch (error: Exception) {
            destinationDirectory.deleteRecursively()
            throw ResolvedImportCommitException.Failed(error)
        }

        val entry = extracted.entryFile
        val title = runCatching {
            ZipPackageImporter.extractFirstH1(entry.readText(Charsets.UTF_8))
        }.getOrNull()
        val extractedSize = entry.length().takeIf { it > 0L }
            ?: ZipPackageImporter.packageTotalSize(extracted.packageRoot).takeIf { it > 0L }
            ?: prepared.byteCount
        val zipLabel = prepared.metadata.displayName
            .removeSuffix(".zip")
            .removeSuffix(".ZIP")
            .trim()
            .takeIf {
                it.isNotEmpty() &&
                    it != "未命名文档" &&
                    !it.equals("shared", ignoreCase = true)
            }
        return MaterializedImportPayload(
            uri = Uri.fromFile(entry).toString(),
            displayName = zipLabel ?: entry.name.takeIf(String::isNotBlank) ?: "Markdown 教材包",
            mimeType = prepared.metadata.mimeType ?: "application/zip",
            fileSize = extractedSize,
            title = title,
            operationOwnedPath = destinationDirectory,
        )
    }

    private fun sourceAliasKeys(sourceUri: Uri, canonicalExternalKey: String): List<String> =
        listOf(
            DocumentAliasKeys.uriV1(canonicalExternalKey),
            DocumentAliasKeys.legacyRawUriV1(sourceUri.toString()),
        )

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
        drafts: List<com.m1racle.reader.pdf.EmbeddedOutlineNodeDraft>,
        mode: com.m1racle.reader.pdf.OutlineImportMode,
    ): Int = withContext(Dispatchers.IO) {
        val existing = textbookOutlineNodeDao.listForDocument(documentId)
        if (com.m1racle.reader.pdf.PdfEmbeddedOutlineImporter.isAlreadyImported(drafts, existing) &&
            mode == com.m1racle.reader.pdf.OutlineImportMode.MERGE
        ) {
            return@withContext 0
        }
        // REPLACE always rewrites; MERGE may skip all
        if (mode == com.m1racle.reader.pdf.OutlineImportMode.REPLACE) {
            // REPLACE: if identical embedded set already present and no MANUAL nodes, skip
            val onlyEmbedded = existing.isNotEmpty() &&
                existing.all { it.source == com.m1racle.reader.data.local.OutlineNodeSource.PDF_EMBEDDED.name }
            if (onlyEmbedded &&
                com.m1racle.reader.pdf.PdfEmbeddedOutlineImporter.isAlreadyImported(drafts, existing) &&
                existing.size == drafts.size
            ) {
                return@withContext 0
            }
            textbookOutlineNodeDao.deleteAllForDocument(documentId)
            val plan = com.m1racle.reader.pdf.PdfEmbeddedOutlineImporter.buildReplace(documentId, drafts)
            plan.nodes.forEach { textbookOutlineNodeDao.upsert(it) }
            return@withContext plan.inserted
        }
        val plan = com.m1racle.reader.pdf.PdfEmbeddedOutlineImporter.buildMerge(
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
     * Open an EPUB document: resolve the managed .epub copy, extract to the
     * app-private cache (epubs/{id}, reused while the container marker exists),
     * parse, and persist OPF title + TOC size. Throws [EpubException].
     */
    suspend fun openEpubBook(id: String): EpubBook = withContext(Dispatchers.IO) {
        val entity = documentDao.getById(id) ?: throw EpubException.Corrupt("文档不存在")
        check(entity.isEpub) { "不是 EPUB 文档" }
        val epubFile = uriToFile(entity.uri) ?: throw EpubException.Corrupt("找不到文件")
        val destDir = java.io.File(context.filesDir, "epubs/$id")
        val book = try {
            if (!java.io.File(destDir, "META-INF/container.xml").isFile) {
                destDir.deleteRecursively()
                EpubPackageImporter.extract(epubFile, destDir)
            }
            EpubPackageImporter.parseExtracted(destDir)
        } catch (e: EpubException) {
            throw e
        } catch (_: Exception) {
            throw EpubException.Corrupt("文件可能已损坏")
        }
        touchLastOpenedAt(id)
        updateParseMetadata(id, book.metadata.title, book.toc.size)
        book
    }

    private fun uriToFile(uriString: String): java.io.File? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { java.io.File(it) }?.takeIf { it.isFile }
            } else {
                null
            }
        } catch (_: Exception) {
            null
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

    suspend fun updatePdfViewportSession(id: String, session: com.m1racle.reader.pdf.PdfViewportSessionState) {
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
     * a single Room transaction. Used by [com.m1racle.reader.pdf.FinalPdfProgressFlusher]'s
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
        session: com.m1racle.reader.pdf.PdfViewportSessionState?,
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

    /** Home SAF adapter: metadata/type/grant only; identity and commit are shared. */
    suspend fun importDocument(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            ensureIdentityRuntimeReady()
            takeReadPermission(uri)
            val provider = applyFileUriEnrichment(
                ProviderMetadataSource.collect(context.contentResolver, uri),
                uri,
            )
            val resolved = ExternalUriMetadata.resolve(provider, intentMime = provider.mimeType)
            if (!DocumentType.isAllowedImport(
                    resolved.mimeType,
                    resolved.displayName,
                    provider.lastPathSegment,
                )
            ) {
                return@withContext ImportResult.UnsupportedFormat
            }

            val fileNameFromUri = if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path?.let { File(it).name }
            } else {
                null
            }
            val isZip = DocumentType.isZipFileName(resolved.displayName) ||
                DocumentType.isZipFileName(provider.lastPathSegment) ||
                DocumentType.isZipFileName(fileNameFromUri) ||
                DocumentType.isZipMime(resolved.mimeType)
            val documentType = if (isZip) {
                DocumentType.MARKDOWN_PACKAGE
            } else {
                DocumentType.resolve(
                    resolved.mimeType,
                    resolved.displayName,
                    provider.lastPathSegment,
                )
            }
            val canonicalExternalKey = ExternalUriMetadata.canonicalExternalKey(
                scheme = provider.scheme,
                authority = provider.authority,
                uriString = uri.toString(),
                documentIdFromProvider = provider.lastPathSegment,
            )

            when (
                val identity = contentIdentityResolver.resolve(
                    ContentIdentityInput(
                        sourceUri = uri,
                        metadata = resolved,
                        maximumBytes = ExternalDocumentImporter.MAX_BYTES,
                    ),
                )
            ) {
                is ContentIdentityResolution.Resolved -> {
                    val committed = commitResolvedImport(
                        ResolvedImportRequest(
                            content = identity.content,
                            canonicalExternalKey = canonicalExternalKey,
                            documentType = documentType,
                            payloadPolicy = if (documentType == DocumentType.MARKDOWN) {
                                ImportPayloadPolicy.SOURCE_URI
                            } else {
                                ImportPayloadPolicy.MANAGED_COPY
                            },
                        ),
                    )
                    ImportResult.Success(committed.document)
                }
                ContentIdentityResolution.SourceTooLarge -> ImportResult.SourceTooLarge
                is ContentIdentityResolution.Failed -> ImportResult.ReadFailed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ResolvedImportCommitException.UnsupportedFormat) {
            ImportResult.UnsupportedFormat
        } catch (_: Exception) {
            ImportResult.ReadFailed
        }
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
     * [com.m1racle.reader.share.ProviderMetadataSource.collect]; this repository-side
     * step only re-applies the legacy file:// enrichment (name from path, size from
     * the real file) on top of the collected probe — identical conditions to the
     * former private queryMetadata fallback.
     */
    internal fun applyFileUriEnrichment(
        base: ProviderMetadata,
        uri: Uri,
    ): ProviderMetadata {
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
        private const val MAX_IDENTITY_COMMIT_ATTEMPTS = 3

        /** Progress 0f..1f from 0-based page index. */
        fun pdfReadingProgress(pageIndex: Int, pageCount: Int): Float {
            if (pageCount <= 0) return 0f
            if (pageCount == 1) return if (pageIndex > 0) 1f else 0f
            val idx = pageIndex.coerceIn(0, pageCount - 1)
            return (idx.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
        }
    }
}

enum class ImportPayloadPolicy {
    /** Keep a persistable/user-owned locator; the resolver stage is identity evidence only. */
    SOURCE_URI,

    /** Consume the resolver stage into app-private managed storage. */
    MANAGED_COPY,
}

data class ResolvedImportRequest(
    val content: PreparedContentIdentity,
    val canonicalExternalKey: String,
    val documentType: DocumentType,
    val payloadPolicy: ImportPayloadPolicy,
)

data class ResolvedImportCommit(
    val document: DocumentEntity,
    val reusedExisting: Boolean,
)

sealed class ResolvedImportCommitException(cause: Throwable) : Exception(cause) {
    class UnsupportedFormat(cause: Throwable) : ResolvedImportCommitException(cause)
    class Failed(cause: Throwable) : ResolvedImportCommitException(cause)
}

private data class MaterializedImportPayload(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val fileSize: Long,
    val title: String?,
    /** Unique path created by this operation; never points at an existing document payload. */
    val operationOwnedPath: File?,
) {
    fun deleteOperationOwnedFiles() {
        operationOwnedPath?.deleteRecursively()
    }
}

interface ImportTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

private class RoomImportTransactionRunner(
    private val db: RoomDatabase,
) : ImportTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        db.withTransaction { block() }
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
     * ([com.m1racle.reader.share.ExternalDocumentImporter.MAX_BYTES]) — the
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
