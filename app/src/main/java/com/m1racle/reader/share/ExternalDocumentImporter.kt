package com.m1racle.reader.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportPayloadPolicy
import com.m1racle.reader.data.repository.ResolvedImportCommitException
import com.m1racle.reader.data.repository.ResolvedImportRequest
import com.m1racle.reader.identity.ContentIdentityInput
import com.m1racle.reader.identity.ContentIdentityResolution
import com.m1racle.reader.identity.ContentIdentityResolver
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-phase VIEW/SEND adapter.
 *
 * [prepare] performs metadata/type/grant work and delegates every accepted byte
 * stream to [ContentIdentityResolver]. [commitPrepared] delegates all document
 * id selection, alias writes, and payload ownership to
 * [DocumentRepository.commitResolvedImport].
 */
open class ExternalDocumentImporter(
    private val context: Context,
    private val repository: DocumentRepository,
    contentIdentityResolver: ContentIdentityResolver? = null,
    /** Host-test source seam; the real resolver still owns copy/hash/lookup. */
    openStreamOverride: ((Uri) -> InputStream?)? = null,
    /** Test-only failure after real resolver staging, before repository commit. */
    private val forceRoomUpsertFailure: Boolean = false,
) {
    private val contentIdentityResolver: ContentIdentityResolver =
        contentIdentityResolver
            ?: openStreamOverride?.let(repository::contentIdentityResolverWithSource)
            ?: repository.contentIdentityResolver

    companion object {
        /** Hard cap shared by Home SAF, VIEW, and SEND. */
        const val MAX_BYTES: Long = 200L * 1024L * 1024L

        /** Single managed-copy filename sanitizer, also consumed by shared commit. */
        internal fun sanitizeFileName(name: String, fallbackExt: String): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("""[^\w.\-()（）\u4e00-\u9fff\s]+"""), "_")
                .trim()
                .take(120)
                .ifBlank { "file.$fallbackExt" }
            return if (base.contains('.')) base else "$base.$fallbackExt"
        }
    }

    open suspend fun prepare(
        sourceUri: Uri,
        intentType: String?,
        grantFlags: Int = 0,
        activeDocumentId: String? = null,
    ): PrepareOutcome = withContext(Dispatchers.IO) {
        try {
            repository.ensureIdentityRuntimeReady()
            maybeTakePersistable(sourceUri, grantFlags)
            val provider = ProviderMetadataSource.collect(
                context.contentResolver,
                sourceUri,
                intentType,
            )
            val resolved = ExternalUriMetadata.resolve(provider, intentType)
            if (!DocumentType.isAllowedImport(
                    resolved.mimeType,
                    resolved.displayName,
                    provider.lastPathSegment,
                )
            ) {
                return@withContext PrepareOutcome.Unsupported
            }

            val isZipPackage = DocumentType.isZipFileName(resolved.displayName) ||
                DocumentType.isZipFileName(provider.lastPathSegment) ||
                DocumentType.isZipMime(resolved.mimeType)
            val documentType = if (isZipPackage) {
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
                uriString = sourceUri.toString(),
                documentIdFromProvider = provider.lastPathSegment,
            )

            when (
                val identity = contentIdentityResolver.resolve(
                    ContentIdentityInput(
                        sourceUri = sourceUri,
                        metadata = resolved,
                        maximumBytes = MAX_BYTES,
                    ),
                )
            ) {
                is ContentIdentityResolution.Resolved -> PrepareOutcome.Success(
                    PreparedOpen(
                        importRequest = ResolvedImportRequest(
                            content = identity.content,
                            canonicalExternalKey = canonicalExternalKey,
                            documentType = documentType,
                            payloadPolicy = ImportPayloadPolicy.MANAGED_COPY,
                        ),
                        activeDocumentId = activeDocumentId,
                    ),
                )
                ContentIdentityResolution.SourceTooLarge -> PrepareOutcome.TooLarge
                is ContentIdentityResolution.Failed -> PrepareOutcome.Failed(
                    identity.cause.message ?: "读取失败",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PrepareOutcome.Failed(error.message ?: "准备失败")
        }
    }

    /** PHASE 2 body. The coordinator still flushes the old viewer before this call. */
    open suspend fun commitPrepared(prepared: PreparedOpen): CommitOutcome =
        withContext(Dispatchers.IO) {
            try {
                if (forceRoomUpsertFailure) {
                    prepared.importRequest.content.close()
                    return@withContext CommitOutcome.Failed("Room 写入失败")
                }
                val committed = repository.commitResolvedImport(prepared.importRequest)
                val document = committed.document
                CommitOutcome.Success(
                    documentId = document.id,
                    documentType = document.type,
                    displayName = document.displayName,
                    sizeBytes = document.fileSize ?: prepared.sizeBytes,
                    contentSha256 = requireNotNull(document.contentSha256),
                    reusedExisting = committed.reusedExisting,
                    bringToFrontOnly = document.id == prepared.activeDocumentId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ResolvedImportCommitException.UnsupportedFormat) {
                CommitOutcome.Failed("教材包超出大小或条目限制")
            } catch (error: Exception) {
                prepared.importRequest.content.close()
                CommitOutcome.Failed(error.message ?: "提交失败")
            }
        }

    /**
     * Metadata + alias candidate for the confirmation card. A candidate is
     * advisory only: the coordinator still executes prepare and commit before
     * deciding whether the final document is already active.
     */
    open suspend fun probeIncomingCard(sourceUri: Uri, intentType: String?): DocumentIdentityCard {
        val provider = ProviderMetadataSource.collect(context.contentResolver, sourceUri, intentType)
        val resolved = ExternalUriMetadata.resolve(provider, intentType)
        val externalKey = ExternalUriMetadata.canonicalExternalKey(
            provider.scheme,
            provider.authority,
            sourceUri.toString(),
            provider.lastPathSegment,
        )
        val candidate = repository.findBySourceAlias(sourceUri, externalKey)
        val resolvedType = DocumentType.resolve(
            resolved.mimeType,
            resolved.displayName,
            provider.lastPathSegment,
        )
        return DocumentIdentityCard(
            documentId = candidate?.id,
            displayName = candidate?.displayName
                ?.takeIf { it.isNotBlank() && it != "未命名文档" }
                ?: resolved.displayName,
            sizeBytes = candidate?.fileSize ?: resolved.sizeBytesHint,
            sizeUnknownUntilOpen = candidate?.fileSize == null && resolved.sizeBytesHint == null,
            isPdf = candidate?.isPdf ?: (resolvedType == DocumentType.PDF),
        )
    }

    open fun discardPrepared(prepared: PreparedOpen?) {
        prepared?.importRequest?.content?.close()
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
}
