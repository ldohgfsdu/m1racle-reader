package com.markleaf.app.data.local

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
    fun isAllowedImport_acceptsMarkdownAndPdf() {
        assertTrue(DocumentType.isAllowedImport("text/markdown", "a.md"))
        assertTrue(DocumentType.isAllowedImport("application/pdf", "a.pdf"))
        assertTrue(DocumentType.isAllowedImport(null, "a.markdown"))
        assertFalse(DocumentType.isAllowedImport("image/jpeg", "photo.jpg"))
        assertFalse(DocumentType.isAllowedImport(null, "data.bin"))
    }

    @Test
    fun fromStorage_defaultsToMarkdown() {
        assertEquals(DocumentType.MARKDOWN, DocumentType.fromStorage(null))
        assertEquals(DocumentType.MARKDOWN, DocumentType.fromStorage("MARKDOWN"))
        assertEquals(DocumentType.PDF, DocumentType.fromStorage("pdf"))
        assertEquals(DocumentType.PDF, DocumentType.fromStorage("PDF"))
    }
}
