package com.markleaf.app.pdf.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ChapterPackageCleanupTest {

    @Test
    fun cleanupDir_removesTempFiles() {
        val dir = createTempDirectory("ch_export_test").toFile()
        File(dir, "a.txt").writeText("x")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.txt").writeText("y")
        assertTrue(dir.exists())
        ChapterPackageExporter.cleanupDir(dir)
        assertFalse(dir.exists())
    }

    @Test
    fun zipEntryNames_fixedOnly() {
        val dir = createTempDirectory("zip_test").toFile()
        try {
            val a = File(dir, "source.pdf").apply { writeText("%PDF") }
            val z = File(dir, "out.zip")
            ChapterPackageExporter.zipFiles(z, listOf(a to "source.pdf"))
            assertTrue(z.exists() && z.length() > 0)
            var threw = false
            try {
                ChapterPackageExporter.zipFiles(z, listOf(a to "../evil.pdf"))
            } catch (_: Exception) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            ChapterPackageExporter.cleanupDir(dir)
        }
    }
}
