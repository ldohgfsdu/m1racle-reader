package com.m1racle.reader.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Single Android-side collector for external-provider metadata (A3-3a merge of
 * ExternalDocumentImporter.queryProviderMetadata and DocumentRepository.queryMetadata).
 *
 * Resolution rules stay exclusively in [ExternalUriMetadata.resolve]; this object
 * only performs the ContentResolver probing and fills [ProviderMetadata].
 *
 * MIME precedence is caller-owned via [intentMime]: a non-blank intentMime wins
 * over ContentResolver.getType; callers without an intent mime pass null.
 * Every probe is individually exception-guarded — a failing probe leaves its
 * field null instead of failing the collection.
 */
object ProviderMetadataSource {

    fun collect(
        resolver: ContentResolver,
        uri: Uri,
        intentMime: String? = null,
    ): ProviderMetadata {
        var displayName: String? = null
        var size: Long? = null
        try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) displayName = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
        }

        val mime = try {
            intentMime?.takeIf { it.isNotBlank() } ?: resolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        var afdLen: Long? = null
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len >= 0L) afdLen = len
            }
        } catch (_: Exception) {
        }

        var pfdStat: Long? = null
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val s = pfd.statSize
                if (s >= 0L) pfdStat = s
            }
        } catch (_: Exception) {
        }

        return ProviderMetadata(
            displayName = displayName,
            size = size,
            mimeType = mime,
            scheme = uri.scheme,
            authority = uri.authority,
            lastPathSegment = uri.lastPathSegment,
            assetFileDescriptorLength = afdLen,
            parcelFileDescriptorStatSize = pfdStat,
        )
    }
}
