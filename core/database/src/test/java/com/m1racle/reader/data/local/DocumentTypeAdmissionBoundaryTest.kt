package com.m1racle.reader.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundary cells of the PDF-only admission gate.
 *
 * The golden parity matrix covers the ordinary name/MIME grid. These are the
 * cells it does not: a name with a trailing dot, a name and a MIME that both
 * name a format but disagree, and a MIME that is a generic container rather
 * than a reading format.
 *
 * The gate decides admission only. A name or a MIME is not the file's content;
 * the PDF open and parse path is what verifies the bytes.
 */
class DocumentTypeAdmissionBoundaryTest {

    private fun admitted(mime: String?, name: String): Boolean =
        DocumentType.isAllowedImport(mime, name, name)

    @Test
    fun trailingDotNameHasNoExtensionSoTheMimeDecides() {
        // "book." has a trailing dot, not an unknown extension. hasNonPdfExtension
        // returns false for it, so the gate falls through to the MIME exactly as it
        // does for "book".
        assertTrue(admitted("application/pdf", "book."))
        assertTrue(admitted("application/x-pdf", "book."))
        assertFalse(admitted("text/plain", "book."))
        assertFalse(admitted("application/epub+zip", "book."))
        assertFalse(admitted(null, "book."))
    }

    @Test
    fun pdfNameWithEpubMimeIsRefusedBecauseBothSidesAreExplicit() {
        // A .pdf name and an EPUB MIME are two explicit, contradictory claims, so
        // neither is trusted. This is symmetric with .epub under application/pdf,
        // which the gate already refuses. EPUB is the only MIME that names another
        // reading format this product used to ship, which is why it is treated
        // differently from a generic container MIME below.
        assertFalse(admitted("application/epub+zip", "book.pdf"))
    }

    @Test
    fun pdfNameWithGenericContainerMimeStaysAdmitted() {
        // application/zip is a generic container, not a reading format: a .pdf name
        // with a zip MIME is far more likely a mislabelled PDF than an archive.
        // Admission is not verification either way.
        assertTrue(admitted("application/zip", "book.pdf"))
        assertTrue(admitted("application/x-zip-compressed", "book.pdf"))
        assertTrue(admitted("application/octet-stream", "book.pdf"))
        assertTrue(admitted("text/plain", "book.pdf"))
        assertTrue(admitted(null, "book.pdf"))
    }

    @Test
    fun pdfNameIsAdmittedUnderEveryNonEpubMime() {
        val mimes = listOf(
            "application/pdf",
            "application/x-pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-zip",
            "text/markdown",
            "text/plain",
            "application/octet-stream",
            "image/jpeg",
            "text/html",
            "application/msword",
            null,
        )
        for (mime in mimes) {
            assertTrue("$mime + book.pdf must be admitted", admitted(mime, "book.pdf"))
        }
    }

    @Test
    fun compoundAndBareDotPdfNamesAreAdmitted() {
        assertTrue(admitted("application/pdf", "a.tar.pdf"))
        assertTrue(admitted("application/pdf", ".pdf"))
        // A dotfile named exactly ".pdf" ends with ".pdf", so the extension rule wins
        // even without a PDF MIME. Pinned as current behaviour, not as a guarantee.
        assertTrue(admitted("text/plain", ".pdf"))
    }

    @Test
    fun doubleTrailingDotDropsThePdfIdentity() {
        // "book.pdf." ends in a dot, so by the gate's rule it has no extension and
        // the name no longer says PDF. Only the MIME can admit it.
        assertTrue(admitted("application/pdf", "book.pdf."))
        assertFalse(admitted("text/plain", "book.pdf."))
        assertFalse(admitted(null, "book.pdf."))
    }

    @Test
    fun retiredFormatsAreRefusedEvenWhenTheMimeSaysPdf() {
        for (name in listOf("book.epub", "notes.md", "notes.markdown", "notes.txt", "pack.zip")) {
            assertFalse("$name under application/pdf must be refused", admitted("application/pdf", name))
        }
    }

    @Test
    fun extensionlessNameNeedsAPdfMime() {
        assertTrue(admitted("application/pdf", "README"))
        assertFalse(admitted("text/plain", "README"))
        assertFalse(admitted("application/octet-stream", "README"))
        assertFalse(admitted(null, "README"))
    }
}
