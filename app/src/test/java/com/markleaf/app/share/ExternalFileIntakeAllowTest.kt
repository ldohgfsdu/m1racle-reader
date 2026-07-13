package com.markleaf.app.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalFileIntakeAllowTest {

    @Test
    fun allowsPdfZipMarkdownTxt() {
        assertTrue(ExternalFileIntake.isAllowed("application/pdf", "a.pdf", null))
        assertTrue(ExternalFileIntake.isAllowed("application/zip", "b.zip", null))
        assertTrue(ExternalFileIntake.isAllowed("text/markdown", "c.md", null))
        assertTrue(ExternalFileIntake.isAllowed("text/plain", "d.txt", null))
        assertTrue(ExternalFileIntake.isAllowed("application/x-zip-compressed", "e.zip", null))
        assertTrue(ExternalFileIntake.isAllowed("text/x-markdown", "f.markdown", null))
    }

    @Test
    fun rejectsUnknownWithoutExtension() {
        assertFalse(ExternalFileIntake.isAllowed("application/octet-stream", "mystery", null))
        assertFalse(ExternalFileIntake.isAllowed("image/png", "x.png", null))
    }

    @Test
    fun octetStreamAllowedWithKnownExtension() {
        assertTrue(ExternalFileIntake.isAllowed("application/octet-stream", "book.pdf", null))
        assertTrue(ExternalFileIntake.isAllowed("application/octet-stream", "pack.zip", null))
    }
}
