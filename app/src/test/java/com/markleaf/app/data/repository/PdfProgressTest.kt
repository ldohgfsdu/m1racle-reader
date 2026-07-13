package com.markleaf.app.data.repository

import com.markleaf.app.data.local.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfProgressTest {

    @Test
    fun pdfReadingProgress_emptyOrSinglePage() {
        assertEquals(0f, DocumentRepository.pdfReadingProgress(0, 0), 0.001f)
        assertEquals(0f, DocumentRepository.pdfReadingProgress(0, 1), 0.001f)
        assertEquals(1f, DocumentRepository.pdfReadingProgress(1, 1), 0.001f)
    }

    @Test
    fun pdfReadingProgress_multiPageEndpoints() {
        assertEquals(0f, DocumentRepository.pdfReadingProgress(0, 10), 0.001f)
        assertEquals(1f, DocumentRepository.pdfReadingProgress(9, 10), 0.001f)
        assertEquals(0.5f, DocumentRepository.pdfReadingProgress(5, 11), 0.001f)
    }

    @Test
    fun pdfReadingProgress_clampsIndex() {
        assertEquals(0f, DocumentRepository.pdfReadingProgress(-3, 5), 0.001f)
        assertEquals(1f, DocumentRepository.pdfReadingProgress(99, 5), 0.001f)
    }

    @Test
    fun isAllowedFileName_includesPdfAndMarkdown() {
        assertTrue(DocumentRepository.isAllowedFileName("a.md"))
        assertTrue(DocumentRepository.isAllowedFileName("a.markdown"))
        assertTrue(DocumentRepository.isAllowedFileName("a.txt"))
        assertTrue(DocumentRepository.isAllowedFileName("report.PDF"))
        assertFalse(DocumentRepository.isAllowedFileName("report.docx"))
        assertFalse(DocumentRepository.isAllowedFileName(null))
    }

    @Test
    fun documentType_notPdfForMarkdownNames() {
        assertEquals(
            DocumentType.MARKDOWN,
            DocumentType.resolve("text/plain", "x.md"),
        )
        assertFalse(DocumentType.hasPdfExtension("x.md"))
    }
}
