package com.markleaf.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.DocumentType
import com.markleaf.app.data.repository.DocumentRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-phase external import:
 * - [prepare]: metadata, optional stream copy+hash, dedup — never touches active session.
 * - [commitPrepared]: Room write + identity index bind after caller flushed old progress.
 *
 * Room + private files are authority; [DocumentIdentityIndex] is rebuildable.
 */
open class ExternalDocumentImporter(
    private val context: Context,
    private val repository: DocumentRepository,
    private val identityIndex: DocumentIdentityIndex = DocumentIdentityIndex(context),
    /**
     * Test hooks — production leaves defaults. Failure injectors must run on real copy paths
     * (not skip copy).
     */
    private val openStreamOverride: ((Uri) -> InputStream?)? = null,
    private val afterBytesCopied: ((File, Long, String) -> Unit)? = null,
    private val forceRoomUpsertFailure: Boolean = false,
    private val forceIndexBindFailure: Boolean = false,
) {
    companion object {
        /** Hard cap to avoid huge share payloads (shared semantics with zip intake). */
        const val MAX_BYTES: Long = 200L * 1024L * 1024L

        /**
         * Single import-side staged-file name sanitizer (A3-3a: the
         * ExternalFileIntake twin was deleted with its dead call path).
         * Pure function; JVM-testable golden lock in
         * [ExternalDocumentImporterSanitizeGoldenTest].
         */
        internal fun sanitizeFileName(name: String, fallbackExt: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("""[^\w.\-()（）\u4e00-\u9fff\s]+"""), "_")
                .trim()
                .take(120)
                .ifBlank { "file.$fallbackExt" }
            return if (base.contains('.')) base else "$base.$fallbackExt"
        }
    }

    val index: DocumentIdentityIndex get() = identityIndex

    open suspend fun prepare(
        sourceUri: Uri,
        intentType: String?,
        grantFlags: Int = 0,
        activeDocumentId: String? = null,
    ): PrepareOutcome = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            maybeTakePersistable(sourceUri, grantFlags)
            val provider = queryProviderMetadata(sourceUri, intentType)
            val resolved = ExternalUriMetadata.resolve(provider, intentType)
            val externalKey = ExternalUriMetadata.canonicalExternalKey(
                scheme = provider.scheme,
                authority = provider.authority,
                uriString = sourceUri.toString(),
                documentIdFromProvider = provider.lastPathSegment,
            )

            if (!DocumentType.isAllowedImport(
                    resolved.mimeType,
                    resolved.displayName,
                    provider.lastPathSegment,
                )
            ) {
                return@withContext PrepareOutcome.Unsupported
            }
            resolved.sizeBytesHint?.let {
                if (it > MAX_BYTES) return@withContext PrepareOutcome.TooLarge
            }

            // Repair index: drop entries pointing at missing Room docs.
            val allIds = repository.getAllDocuments().map { it.id }.toSet()
            identityIndex.pruneMissingDocuments(allIds)

            // Level-1 URI identity
            resolveExistingByUri(externalKey, sourceUri, resolved, activeDocumentId)?.let {
                return@withContext PrepareOutcome.Success(it)
            }

            // Stream copy + hash (required for new content / content-level dedup)
            val libraryDir = File(context.filesDir, "library").also { if (!it.exists()) it.mkdirs() }
            tempFile = File(libraryDir, "${System.nanoTime()}.importing")
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L

            val input = openStream(sourceUri)
                ?: return@withContext PrepareOutcome.Failed("无法打开输入流").also { tempFile?.delete() }
            try {
                input.use { stream ->
                    FileOutputStream(tempFile).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = stream.read(buf)
                            if (n < 0) break
                            written += n
                            if (written > MAX_BYTES) {
                                tempFile.delete()
                                return@withContext PrepareOutcome.TooLarge
                            }
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                        out.fd.sync()
                    }
                }
            } catch (e: SecurityException) {
                tempFile.delete()
                return@withContext PrepareOutcome.Failed("权限不足: ${e.message}")
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext PrepareOutcome.Failed(e.message ?: "读取失败")
            }

            if (written <= 0L || !tempFile.isFile) {
                tempFile.delete()
                return@withContext PrepareOutcome.Failed("空文件或落盘失败")
            }

            // Validate staged file is readable
            try {
                FileInputStream(tempFile).use { s ->
                    if (s.read() < 0 && written > 0) {
                        // empty read after non-zero write is odd; still check length
                    }
                }
                if (tempFile.length() != written) {
                    tempFile.delete()
                    return@withContext PrepareOutcome.Failed("临时文件大小不一致")
                }
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext PrepareOutcome.Failed("临时文件不可读: ${e.message}")
            }

            val shaHex = digest.digest().joinToString("") { b -> "%02x".format(b) }
            afterBytesCopied?.invoke(tempFile, written, shaHex)

            val (finalSize, _) = ExternalUriMetadata.resolveFinalSizeBytes(
                copiedBytes = written,
                hint = resolved.sizeBytesHint,
                hintSource = resolved.sizeSource,
            )
            val displayName = resolved.displayName
            val mime = resolved.mimeType
            val documentType = DocumentType.resolve(mime, displayName, provider.lastPathSegment)

            // Level-2 content identity (sha + size)
            resolveExistingByContent(shaHex, finalSize, externalKey, displayName, mime, activeDocumentId)?.let {
                tempFile.delete()
                return@withContext PrepareOutcome.Success(it)
            }

            // Stage final path under content dir but keep as .importing until commit renames/uses it
            val documentId = "c:$shaHex"
            val contentDir = File(libraryDir, shaHex).also { if (!it.exists()) it.mkdirs() }
            val safeName = sanitizeFileName(displayName, ExternalUriMetadata.extensionForMime(mime) ?: "bin")
            val stagedFinal = File(contentDir, "$safeName.importing")
            if (stagedFinal.exists()) stagedFinal.delete()
            if (!tempFile.renameTo(stagedFinal)) {
                tempFile.copyTo(stagedFinal, overwrite = true)
                tempFile.delete()
            }
            tempFile = null
            if (!stagedFinal.isFile) {
                return@withContext PrepareOutcome.Failed("临时文件落盘失败")
            }

            PrepareOutcome.Success(
                PreparedOpen.NewContent(
                    documentId = documentId,
                    isPdf = documentType == DocumentType.PDF,
                    displayName = displayName,
                    sizeBytes = finalSize,
                    contentSha256 = shaHex,
                    externalKey = externalKey,
                    mimeType = mime,
                    stagedFile = stagedFinal,
                    documentTypeName = documentType.name,
                ),
            )
        } catch (e: Exception) {
            tempFile?.delete()
            PrepareOutcome.Failed(e.message ?: "准备失败")
        }
    }

    /**
     * PHASE 2 body for library/DB only — does not clear active session (caller does after success).
     */
    open suspend fun commitPrepared(prepared: PreparedOpen): CommitOutcome = withContext(Dispatchers.IO) {
        var promotedFinalFile: File? = null
        try {
            when (prepared) {
                is PreparedOpen.CurrentActive -> CommitOutcome.Success(
                    documentId = prepared.documentId,
                    isPdf = prepared.isPdf,
                    displayName = prepared.displayName,
                    sizeBytes = prepared.sizeBytes ?: 0L,
                    contentSha256 = prepared.documentId.removePrefix("c:"),
                    reusedExisting = true,
                    bringToFrontOnly = true,
                )
                is PreparedOpen.ExistingLibrary -> {
                    repository.touchLastOpened(prepared.documentId)
                    identityIndex.bind(
                        documentId = prepared.documentId,
                        canonicalUriIdentity = prepared.externalKey,
                        sha256 = prepared.contentSha256,
                        sizeBytes = prepared.sizeBytes ?: -1L,
                        mimeType = prepared.mimeType,
                    )
                    CommitOutcome.Success(
                        documentId = prepared.documentId,
                        isPdf = prepared.isPdf,
                        displayName = prepared.displayName,
                        sizeBytes = prepared.sizeBytes ?: 0L,
                        contentSha256 = prepared.contentSha256,
                        reusedExisting = true,
                        bringToFrontOnly = false,
                    )
                }
                is PreparedOpen.NewContent -> {
                    if (!prepared.stagedFile.isFile) {
                        return@withContext CommitOutcome.Failed("准备的文件已丢失")
                    }
                    val finalFile = File(
                        prepared.stagedFile.parentFile,
                        prepared.stagedFile.name.removeSuffix(".importing"),
                    )
                    val canReuseExistingFinal = finalFile.isFile &&
                        finalFile.length() == prepared.sizeBytes
                    if (canReuseExistingFinal) {
                        prepared.stagedFile.delete()
                    } else {
                        if (finalFile.exists() && !finalFile.delete()) {
                            throw IllegalStateException("无法替换最终文件")
                        }
                        // Track before promotion so a partial copy is also removed on failure.
                        promotedFinalFile = finalFile
                        if (!prepared.stagedFile.renameTo(finalFile)) {
                            prepared.stagedFile.copyTo(finalFile, overwrite = true)
                            prepared.stagedFile.delete()
                        }
                    }
                    if (!finalFile.isFile) {
                        throw IllegalStateException("最终文件写入失败")
                    }
                    val now = System.currentTimeMillis()
                    val localUri = Uri.fromFile(finalFile).toString()
                    val existingRow = repository.getDocumentById(prepared.documentId)
                    val entity = if (existingRow != null) {
                        existingRow.copy(
                            displayName = prepared.displayName,
                            uri = localUri,
                            mimeType = prepared.mimeType,
                            fileSize = prepared.sizeBytes,
                            lastOpenedAt = now,
                            documentType = prepared.documentTypeName,
                        )
                    } else {
                        DocumentEntity(
                            id = prepared.documentId,
                            displayName = prepared.displayName,
                            uri = localUri,
                            mimeType = prepared.mimeType,
                            fileSize = prepared.sizeBytes,
                            lastOpenedAt = now,
                            createdAt = now,
                            lastScrollIndex = 0,
                            lastScrollOffset = 0,
                            readingProgress = 0f,
                            title = null,
                            headingCount = 0,
                            documentType = prepared.documentTypeName,
                            pageCount = 0,
                        )
                    }
                    if (forceRoomUpsertFailure) {
                        throw IllegalStateException("Room 写入失败")
                    }
                    repository.upsert(entity)
                    // Room now owns the reference; failure cleanup must no longer delete it.
                    promotedFinalFile = null
                    // Index bind failure must not leave unidentifiable duplicates: Room is
                    // authority and the document remains openable via its Room id, so a bind
                    // error is swallowed here (best-effort rebuild happens on next prepare).
                    if (!forceIndexBindFailure) {
                        runCatching {
                            identityIndex.bind(
                                documentId = prepared.documentId,
                                canonicalUriIdentity = prepared.externalKey,
                                sha256 = prepared.contentSha256,
                                sizeBytes = prepared.sizeBytes,
                                mimeType = prepared.mimeType,
                            )
                        }
                    }
                    CommitOutcome.Success(
                        documentId = prepared.documentId,
                        isPdf = prepared.isPdf,
                        displayName = prepared.displayName,
                        sizeBytes = prepared.sizeBytes,
                        contentSha256 = prepared.contentSha256,
                        reusedExisting = existingRow != null,
                        bringToFrontOnly = false,
                    )
                }
            }
        } catch (e: Exception) {
            // Roll back both possible names. After promotion the staged path no
            // longer exists, so deleting it alone leaves an orphan final file.
            if (prepared is PreparedOpen.NewContent) {
                prepared.stagedFile.delete()
                promotedFinalFile?.let { promoted ->
                    promoted.delete()
                    promoted.parentFile?.delete()
                }
            }
            CommitOutcome.Failed(e.message ?: "提交失败")
        }
    }

    /**
     * Metadata-only probe for the confirm dialog. Does not copy, write Room, or touch lastOpened.
     * [documentId] is set only when the identity index points at a live Room row.
     */
    open suspend fun probeIncomingCard(sourceUri: Uri, intentType: String?): DocumentIdentityCard {
        val provider = queryProviderMetadata(sourceUri, intentType)
        val resolved = ExternalUriMetadata.resolve(provider, intentType)
        val isPdf = DocumentType.resolve(resolved.mimeType, resolved.displayName, provider.lastPathSegment) ==
            DocumentType.PDF
        val sizeUnknown = resolved.sizeBytesHint == null
        val externalKey = ExternalUriMetadata.canonicalExternalKey(
            provider.scheme,
            provider.authority,
            sourceUri.toString(),
            provider.lastPathSegment,
        )
        // Prefer live index, validated against Room (Room is authority).
        val indexedId = identityIndex.findByExternalKey(externalKey)?.documentId
        val validatedId = indexedId?.let { id ->
            repository.getDocumentById(id)?.id
        }
        // Prefer library display name / size when reopening a known external key.
        val libraryDoc = validatedId?.let { repository.getDocumentById(it) }
        val displayName = when {
            libraryDoc != null &&
                libraryDoc.displayName.isNotBlank() &&
                libraryDoc.displayName != "未命名文档" -> libraryDoc.displayName
            else -> resolved.displayName
        }
        val sizeBytes = libraryDoc?.fileSize ?: resolved.sizeBytesHint
        val sizeUnknownUntilOpen = libraryDoc?.fileSize == null && sizeUnknown
        return DocumentIdentityCard(
            documentId = validatedId,
            displayName = displayName,
            sizeBytes = sizeBytes,
            sizeUnknownUntilOpen = sizeUnknownUntilOpen,
            isPdf = libraryDoc?.isPdf ?: isPdf,
        )
    }

    open fun discardPrepared(prepared: PreparedOpen?) {
        if (prepared is PreparedOpen.NewContent) {
            prepared.stagedFile.delete()
        }
    }

    private suspend fun resolveExistingByUri(
        externalKey: String,
        sourceUri: Uri,
        resolved: ResolvedExternalMetadata,
        activeDocumentId: String?,
    ): PreparedOpen? {
        val fromIndex = identityIndex.findByExternalKey(externalKey)
        if (fromIndex != null) {
            val doc = repository.getDocumentById(fromIndex.documentId)
            if (doc == null) {
                identityIndex.removeByDocumentId(fromIndex.documentId)
            } else {
                val improvedName =
                    if (doc.displayName == "未命名文档" && resolved.displayName != "未命名文档") {
                        resolved.displayName
                    } else {
                        doc.displayName
                    }
                if (doc.id == activeDocumentId) {
                    return PreparedOpen.CurrentActive(
                        documentId = doc.id,
                        isPdf = doc.isPdf,
                        displayName = improvedName,
                        sizeBytes = doc.fileSize,
                    )
                }
                return PreparedOpen.ExistingLibrary(
                    documentId = doc.id,
                    isPdf = doc.isPdf,
                    displayName = improvedName,
                    sizeBytes = doc.fileSize,
                    contentSha256 = fromIndex.sha256,
                    externalKey = externalKey,
                    mimeType = doc.mimeType ?: resolved.mimeType,
                )
            }
        }

        val legacyId = DocumentRepository.stableUriId(sourceUri.toString())
        repository.getDocumentById(legacyId)?.let { doc ->
            if (doc.id == activeDocumentId) {
                return PreparedOpen.CurrentActive(doc.id, doc.isPdf, doc.displayName, doc.fileSize)
            }
            return PreparedOpen.ExistingLibrary(
                documentId = doc.id,
                isPdf = doc.isPdf,
                displayName = resolved.displayName.takeIf { it != "未命名文档" } ?: doc.displayName,
                sizeBytes = doc.fileSize ?: resolved.sizeBytesHint,
                contentSha256 = doc.id.removePrefix("c:"),
                externalKey = externalKey,
                mimeType = doc.mimeType ?: resolved.mimeType,
            )
        }
        return null
    }

    private suspend fun resolveExistingByContent(
        shaHex: String,
        sizeBytes: Long,
        externalKey: String,
        displayName: String,
        mime: String?,
        activeDocumentId: String?,
    ): PreparedOpen? {
        val fromIndex = identityIndex.findByContentSha(shaHex, sizeBytes)
        if (fromIndex != null) {
            val doc = repository.getDocumentById(fromIndex.documentId)
            if (doc == null) {
                identityIndex.removeByDocumentId(fromIndex.documentId)
            } else if (doc.fileSize == null || doc.fileSize == sizeBytes) {
                if (doc.id == activeDocumentId) {
                    return PreparedOpen.CurrentActive(doc.id, doc.isPdf, doc.displayName, doc.fileSize)
                }
                return PreparedOpen.ExistingLibrary(
                    documentId = doc.id,
                    isPdf = doc.isPdf,
                    displayName = if (doc.displayName == "未命名文档") displayName else doc.displayName,
                    sizeBytes = doc.fileSize ?: sizeBytes,
                    contentSha256 = shaHex,
                    externalKey = externalKey,
                    mimeType = doc.mimeType ?: mime,
                )
            }
        }
        repository.findByContentSha256(shaHex, sizeBytes)?.let { existing ->
            if (existing.id == activeDocumentId) {
                return PreparedOpen.CurrentActive(
                    existing.id,
                    existing.isPdf,
                    existing.displayName,
                    existing.fileSize,
                )
            }
            return PreparedOpen.ExistingLibrary(
                documentId = existing.id,
                isPdf = existing.isPdf,
                displayName = existing.displayName,
                sizeBytes = existing.fileSize ?: sizeBytes,
                contentSha256 = shaHex,
                externalKey = externalKey,
                mimeType = existing.mimeType ?: mime,
            )
        }
        return null
    }

    private fun maybeTakePersistable(uri: Uri, grantFlags: Int) {
        if (grantFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
    }

    private fun queryProviderMetadata(uri: Uri, intentType: String?): ProviderMetadata {
        return ProviderMetadataSource.collect(context.contentResolver, uri, intentType)
    }

    private fun openStream(uri: Uri): InputStream? {
        openStreamOverride?.let { return it(uri) }
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "file" -> uri.path?.let { p ->
                val f = File(p)
                if (f.isFile) FileInputStream(f) else null
            }
            else -> context.contentResolver.openInputStream(uri)
        }
    }
}
