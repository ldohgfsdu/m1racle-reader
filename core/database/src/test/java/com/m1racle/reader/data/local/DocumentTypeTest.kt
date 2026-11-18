package com.m1racle.reader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTypeTest {

    @Test
    fun resolve_pdfMime_isPdf() {
        assertEquals(
            DocumentType.PDF,
            DocumentType.resolve("application/pdf", "notes.txt"),
        )
    }

    @Test
    fun resolve_pdfExtension_withOctetStream_isPdf() {
        assertEquals(
            DocumentType.PDF,
            DocumentType.resolve("application/octet-stream", "book.PDF"),
        )
    }

    @Test
    fun resolve_pdfExtension_overridesIncorrectTextMime() {
        assertEquals(
            DocumentType.PDF,
            DocumentType.resolve("text/plain", "provider-labelled-wrong.pdf"),
        )
    }

    @Test
    fun resolve_markdownExtension_isMarkdown() {
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve("text/plain", "readme.md"),
        )
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve(null, "doc.markdown"),
        )
    }

    @Test
    fun resolve_nonPdfNeverMisclassified() {
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve("text/plain", "notes.txt"),
        )
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve("image/png", "scan.png"),
        )
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve(null, "archive.zip"),
        )
    }

    @Test
    fun isAllowedImport_acceptsPdfOnly() {
        assertTrue(DocumentType.isAllowedImport("application/pdf", "a.pdf"))
        assertTrue(DocumentType.isAllowedImport("application/x-pdf", "scan.pdf"))
        assertTrue(DocumentType.isAllowedImport("application/octet-stream", "book.PDF"))
        assertFalse(DocumentType.isAllowedImport("text/markdown", "a.md"))
        assertFalse(DocumentType.isAllowedImport(null, "a.markdown"))
        assertFalse(DocumentType.isAllowedImport("image/jpeg", "photo.jpg"))
        assertFalse(DocumentType.isAllowedImport(null, "data.bin"))
    }

    @Test
    fun resolve_epubIsClassifiedAsNonPdf() {
        // The EPUB reader is retired. A shared .epub must classify as something the
        // gate refuses, never as a type that implies a reader exists for it.
        assertEquals(DocumentType.MARKDOWN, DocumentType.resolve("application/epub+zip", "book.bin"))
        assertEquals(DocumentType.MARKDOWN, DocumentType.resolve(null, "book.epub"))
        assertEquals(DocumentType.MARKDOWN, DocumentType.resolve("application/octet-stream", "book.EPUB"))
    }

    @Test
    fun epubMimeIsRefusedByTheGateEvenWhenTheNameSaysPdf() {
        // Classification and admission are different questions. A .pdf name wins the
        // classification, but two explicit and contradictory claims are not trusted,
        // so the gate refuses the pair before resolve() is even consulted.
        assertEquals(DocumentType.PDF, DocumentType.resolve("application/epub+zip", "book.pdf"))
        assertFalse(DocumentType.isAllowedImport("application/epub+zip", "book.pdf"))
        assertFalse(DocumentType.isAllowedImport("application/epub+zip", "book.epub"))
        assertTrue(DocumentType.isAllowedImport("application/octet-stream", "book.pdf"))
    }

    @Test
    fun isAllowedImport_refusesEpubAndOtherEbooks() {
        assertFalse(DocumentType.isAllowedImport("application/epub+zip", "b.epub"))
        assertFalse(DocumentType.isAllowedImport(null, "b.epub"))
        assertFalse(DocumentType.isAllowedImport(null, "b.mobi"))
        assertFalse(DocumentType.isAllowedImport(null, "b.azw3"))
    }

    @Test
    fun fromStorage_roundTripsEpub() {
        assertEquals(DocumentType.EPUB, DocumentType.fromStorage("EPUB"))
        assertEquals(DocumentType.EPUB, DocumentType.fromStorage("epub"))
    }

    @Test
    fun fromStorage_defaultsToMarkdown() {
        assertEquals(DocumentType.MARKDOWN, DocumentType.fromStorage(null))
        assertEquals(DocumentType.MARKDOWN, DocumentType.fromStorage("MARKDOWN"))
        assertEquals(DocumentType.PDF, DocumentType.fromStorage("pdf"))
        assertEquals(DocumentType.PDF, DocumentType.fromStorage("PDF"))
    }
}
