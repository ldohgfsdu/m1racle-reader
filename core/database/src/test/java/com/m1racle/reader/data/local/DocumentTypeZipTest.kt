package com.m1racle.reader.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTypeZipTest {

    @Test
    fun zipImportRefusedButZipNameStillRecognised() {
        assertFalse(DocumentType.isAllowedImport("application/zip", "book.zip", null))
        assertFalse(DocumentType.isAllowedImport(null, "教材包.zip", null))
        // Name classification is still used to recognise a shared ZIP and refuse it
        // with the right message; only admission is PDF-only.
        assertTrue(DocumentType.isZipFileName("demo.ZIP"))
    }

    @Test
    fun markdownRefusedPdfAllowed() {
        assertFalse(DocumentType.isAllowedImport("text/markdown", "a.md", null))
        assertTrue(DocumentType.isAllowedImport("application/pdf", "a.pdf", null))
    }
}
