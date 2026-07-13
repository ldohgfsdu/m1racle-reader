package com.markleaf.app.data.local

import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTypeZipTest {

    @Test
    fun zipImportAllowed() {
        assertTrue(DocumentType.isAllowedImport("application/zip", "book.zip", null))
        assertTrue(DocumentType.isAllowedImport(null, "教材包.zip", null))
        assertTrue(DocumentType.isZipFileName("demo.ZIP"))
    }

    @Test
    fun markdownAndPdfStillAllowed() {
        assertTrue(DocumentType.isAllowedImport("text/markdown", "a.md", null))
        assertTrue(DocumentType.isAllowedImport("application/pdf", "a.pdf", null))
    }
}
