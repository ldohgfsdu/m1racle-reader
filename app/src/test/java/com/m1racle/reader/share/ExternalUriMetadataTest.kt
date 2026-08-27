package com.m1racle.reader.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalUriMetadataTest {

    @Test
    fun displayNameAndSizeBothPresent() {
        val resolved = ExternalUriMetadata.resolve(
            ProviderMetadata(
                displayName = "教材 (修订).pdf",
                size = 12_345L,
                mimeType = "application/pdf",
            ),
        )
        assertEquals("教材 (修订).pdf", resolved.displayName)
        assertEquals(12_345L, resolved.sizeBytesHint)
        assertEquals(ResolvedExternalMetadata.NameSource.DISPLAY_NAME, resolved.nameSource)
        assertEquals(ResolvedExternalMetadata.SizeSource.OPENABLE_SIZE, resolved.sizeSource)
    }

    @Test
    fun sizeMissingUsesAssetFileDescriptor() {
        val resolved = ExternalUriMetadata.resolve(
            ProviderMetadata(
                displayName = "a.pdf",
                size = null,
                assetFileDescriptorLength = 99_000L,
            ),
        )
        assertEquals(99_000L, resolved.sizeBytesHint)
        assertEquals(ResolvedExternalMetadata.SizeSource.ASSET_FD_LENGTH, resolved.sizeSource)
    }

    @Test
    fun metadataMissingUsesCopyByteCountAsFinalAuthority() {
        val (finalSize, source) = ExternalUriMetadata.resolveFinalSizeBytes(
            copiedBytes = 42_000L,
            hint = null,
            hintSource = null,
        )
        assertEquals(42_000L, finalSize)
        assertEquals(ResolvedExternalMetadata.SizeSource.COPY_BYTE_COUNT, source)
    }

    @Test
    fun chineseSpacesAndParenthesesInName() {
        val (name, source) = ExternalUriMetadata.resolveDisplayName(
            ProviderMetadata(displayName = "高等数学 （上册） 第二版.pdf"),
        )
        assertEquals("高等数学 （上册） 第二版.pdf", name)
        assertEquals(ResolvedExternalMetadata.NameSource.DISPLAY_NAME, source)
    }

    @Test
    fun missingExtensionFilledFromMime() {
        val (name, source) = ExternalUriMetadata.resolveDisplayName(
            ProviderMetadata(
                displayName = null,
                lastPathSegment = "report",
                mimeType = "application/pdf",
            ),
        )
        assertEquals("report.pdf", name)
        assertEquals(ResolvedExternalMetadata.NameSource.PATH_SEGMENT, source)
    }

    @Test
    fun mimeGeneratedWhenNoName() {
        val (name, source) = ExternalUriMetadata.resolveDisplayName(
            ProviderMetadata(displayName = null, lastPathSegment = null, mimeType = "application/pdf"),
        )
        assertEquals("document.pdf", name)
        assertEquals(ResolvedExternalMetadata.NameSource.MIME_GENERATED, source)
    }

    @Test
    fun legacyPdfMimeGeneratesPdfName() {
        val (name, source) = ExternalUriMetadata.resolveDisplayName(
            ProviderMetadata(displayName = null, lastPathSegment = null, mimeType = "application/x-pdf"),
        )
        assertEquals("document.pdf", name)
        assertEquals(ResolvedExternalMetadata.NameSource.MIME_GENERATED, source)
    }

    @Test
    fun unnamedOnlyAsLastResort() {
        val (name, source) = ExternalUriMetadata.resolveDisplayName(
            ProviderMetadata(displayName = null, lastPathSegment = null, mimeType = null),
        )
        assertEquals("未命名文档", name)
        assertEquals(ResolvedExternalMetadata.NameSource.FALLBACK_UNNAMED, source)
    }

    @Test
    fun sameUriCanonicalKeyStable() {
        val a = ExternalUriMetadata.canonicalExternalKey("content", "com.foo", "content://com.foo/1", "1")
        val b = ExternalUriMetadata.canonicalExternalKey("content", "com.foo", "content://com.foo/1", "1")
        assertEquals(a, b)
    }

    @Test
    fun differentUriKeysDifferEvenIfNamesMatch() {
        val a = ExternalUriMetadata.canonicalExternalKey("content", "com.foo", "content://com.foo/a", "a")
        val b = ExternalUriMetadata.canonicalExternalKey("content", "com.foo", "content://com.foo/b", "b")
        assertNotEquals(a, b)
    }

    @Test
    fun fileSizeFormatterUnits() {
        assertEquals("512 B", FileSizeFormatter.format(512))
        assertTrue(FileSizeFormatter.format(2048).endsWith("KB"))
        assertTrue(FileSizeFormatter.format(2L * 1024 * 1024).contains("MB"))
        assertEquals("未知大小", FileSizeFormatter.format(null))
        assertEquals(
            "大小将在打开时确认",
            FileSizeFormatter.formatForConfirmDialog(null, sizeUnknownUntilOpen = true),
        )
        assertEquals(
            FileSizeFormatter.SIZE_COMPUTE_AFTER_OPEN,
            FileSizeFormatter.formatForConfirmDialog(null, sizeUnknownUntilOpen = true),
        )
        assertEquals("512 B", FileSizeFormatter.formatForConfirmDialog(512L, sizeUnknownUntilOpen = false))
    }

    @Test
    fun pfdStatSizePreferredAfterOpenableMissing() {
        val resolved = ExternalUriMetadata.resolve(
            ProviderMetadata(
                displayName = "x.pdf",
                size = null,
                assetFileDescriptorLength = null,
                parcelFileDescriptorStatSize = 777L,
            ),
        )
        assertEquals(777L, resolved.sizeBytesHint)
        assertEquals(ResolvedExternalMetadata.SizeSource.PFD_STAT_SIZE, resolved.sizeSource)
    }

    @Test
    fun copyBytesOverrideStaleHint() {
        val (finalSize, source) = ExternalUriMetadata.resolveFinalSizeBytes(
            copiedBytes = 100L,
            hint = 999L,
            hintSource = ResolvedExternalMetadata.SizeSource.OPENABLE_SIZE,
        )
        assertEquals(100L, finalSize)
        assertEquals(ResolvedExternalMetadata.SizeSource.COPY_BYTE_COUNT, source)
    }
}
