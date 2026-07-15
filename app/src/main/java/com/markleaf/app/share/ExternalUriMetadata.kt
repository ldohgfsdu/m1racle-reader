package com.markleaf.app.share

import java.util.Locale

/**
 * Pure resolution rules for external content metadata.
 * Android ContentResolver results are injected as [ProviderMetadata]; this type has no Android deps.
 */
data class ProviderMetadata(
    val displayName: String? = null,
    val size: Long? = null,
    val mimeType: String? = null,
    val scheme: String? = null,
    val authority: String? = null,
    val lastPathSegment: String? = null,
    val assetFileDescriptorLength: Long? = null,
    val parcelFileDescriptorStatSize: Long? = null,
)

data class ResolvedExternalMetadata(
    val displayName: String,
    val sizeBytesHint: Long?,
    val mimeType: String?,
    val nameSource: NameSource,
    val sizeSource: SizeSource?,
) {
    enum class NameSource {
        DISPLAY_NAME,
        PATH_SEGMENT,
        MIME_GENERATED,
        FALLBACK_UNNAMED,
    }

    enum class SizeSource {
        OPENABLE_SIZE,
        ASSET_FD_LENGTH,
        PFD_STAT_SIZE,
        COPY_BYTE_COUNT,
    }
}

object ExternalUriMetadata {

    private const val UNNAMED = "未命名文档"

    /**
     * Name priority:
     * 1. OpenableColumns.DISPLAY_NAME
     * 2. (provider metadata already folded into displayName by caller if any)
     * 3. Uri last path segment (safely decoded)
     * 4. MIME-based generated name
     * 5. 未命名文档
     */
    fun resolveDisplayName(
        provider: ProviderMetadata,
        intentMime: String? = null,
    ): Pair<String, ResolvedExternalMetadata.NameSource> {
        val fromColumn = provider.displayName?.trim()?.takeIf { it.isNotEmpty() && it != UNNAMED }
        if (fromColumn != null) {
            return fromColumn to ResolvedExternalMetadata.NameSource.DISPLAY_NAME
        }

        val segment = decodePathSegment(provider.lastPathSegment)
            ?.takeIf { it.isNotEmpty() && !looksLikeOpaqueProviderId(it) }
        if (segment != null) {
            val withExt = ensureExtension(segment, provider.mimeType ?: intentMime)
            return withExt to ResolvedExternalMetadata.NameSource.PATH_SEGMENT
        }

        val mime = provider.mimeType ?: intentMime
        val generated = nameFromMime(mime)
        if (generated != null) {
            return generated to ResolvedExternalMetadata.NameSource.MIME_GENERATED
        }

        return UNNAMED to ResolvedExternalMetadata.NameSource.FALLBACK_UNNAMED
    }

    /**
     * Size priority (hint before copy). Final authority after copy is [resolveFinalSizeBytes].
     * 1. OpenableColumns.SIZE
     * 2. AssetFileDescriptor.length
     * 3. ParcelFileDescriptor.statSize
     */
    fun resolveSizeHint(provider: ProviderMetadata): Pair<Long?, ResolvedExternalMetadata.SizeSource?> {
        val openable = provider.size?.takeIf { it >= 0L }
        if (openable != null) return openable to ResolvedExternalMetadata.SizeSource.OPENABLE_SIZE

        val afd = provider.assetFileDescriptorLength?.takeIf { it >= 0L && it != 0x7FFFFFFFFFFFFFFFL }
        if (afd != null) return afd to ResolvedExternalMetadata.SizeSource.ASSET_FD_LENGTH

        val pfd = provider.parcelFileDescriptorStatSize?.takeIf { it >= 0L }
        if (pfd != null) return pfd to ResolvedExternalMetadata.SizeSource.PFD_STAT_SIZE

        return null to null
    }

    /** After successful copy, actual byte count is authoritative. */
    fun resolveFinalSizeBytes(
        copiedBytes: Long,
        hint: Long?,
        hintSource: ResolvedExternalMetadata.SizeSource?,
    ): Pair<Long, ResolvedExternalMetadata.SizeSource> {
        if (copiedBytes >= 0L) {
            return copiedBytes to ResolvedExternalMetadata.SizeSource.COPY_BYTE_COUNT
        }
        val h = hint?.takeIf { it >= 0L }
        if (h != null && hintSource != null) return h to hintSource
        return 0L to ResolvedExternalMetadata.SizeSource.COPY_BYTE_COUNT
    }

    fun resolve(
        provider: ProviderMetadata,
        intentMime: String? = null,
    ): ResolvedExternalMetadata {
        val (name, nameSource) = resolveDisplayName(provider, intentMime)
        val (size, sizeSource) = resolveSizeHint(provider)
        return ResolvedExternalMetadata(
            displayName = name,
            sizeBytesHint = size,
            mimeType = provider.mimeType ?: intentMime,
            nameSource = nameSource,
            sizeSource = sizeSource,
        )
    }

    fun ensureExtension(name: String, mime: String?): String {
        if (name.contains('.')) return name
        val ext = extensionForMime(mime) ?: return name
        return "$name.$ext"
    }

    fun extensionForMime(mime: String?): String? =
        when (mime?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()) {
            "application/pdf" -> "pdf"
            "application/zip", "application/x-zip-compressed", "application/x-zip" -> "zip"
            "text/markdown", "text/x-markdown" -> "md"
            "text/plain" -> "txt"
            else -> null
        }

    fun nameFromMime(mime: String?): String? {
        val ext = extensionForMime(mime) ?: return null
        val stem = when (ext) {
            "pdf" -> "document"
            "zip" -> "package"
            "md", "txt" -> "document"
            else -> "document"
        }
        return "$stem.$ext"
    }

    fun decodePathSegment(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val segment = raw.substringAfterLast('/').substringAfterLast('\\')
        return try {
            java.net.URLDecoder.decode(segment, Charsets.UTF_8.name())
        } catch (_: Exception) {
            segment
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun looksLikeOpaqueProviderId(segment: String): Boolean {
        if (segment.length >= 24 && segment.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '-' }) {
            return true
        }
        if (segment.startsWith("msf:") || segment.contains("%3A") || segment.contains(":")) {
            // Often provider document ids — still usable if it has a file-looking suffix
            val after = segment.substringAfterLast(':').substringAfterLast('/')
            return !after.contains('.')
        }
        return false
    }

    fun canonicalExternalKey(
        scheme: String?,
        authority: String?,
        uriString: String,
        documentIdFromProvider: String? = null,
    ): String {
        val s = scheme?.lowercase(Locale.ROOT).orEmpty()
        val a = authority.orEmpty()
        val doc = documentIdFromProvider?.takeIf { it.isNotBlank() }
        return if (doc != null && a.isNotBlank()) {
            "doc:$a/$doc"
        } else {
            "uri:$s://$a|${uriString.trim()}"
        }
    }
}
