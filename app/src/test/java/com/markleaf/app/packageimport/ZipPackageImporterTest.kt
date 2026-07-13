package com.markleaf.app.packageimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPackageImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun legalZip_extractsAndFindsIndex() {
        val zip = buildZip(
            "index.md" to "# Hello\n\n![a](images/a.png)\n",
            "images/a.png" to "PNG",
        )
        val dest = tmp.newFolder("pkg")
        val result = ZipPackageImporter.extract(ByteArrayInputStream(zip), dest)
        assertTrue(result.entryFile.isFile)
        assertEquals("index.md", result.entryRelativePath)
        assertTrue(File(dest, "images/a.png").isFile)
    }

    @Test
    fun zipSlip_rejected() {
        val zip = buildZip("../evil.md" to "x")
        val dest = tmp.newFolder("slip")
        try {
            ZipPackageImporter.extract(ByteArrayInputStream(zip), dest)
            fail("expected SecurityException")
        } catch (_: SecurityException) {
            // ok
        }
    }

    @Test
    fun zipSlip_absolutePathRejected() {
        // Leading slash cleaned then still may escape if ".." used
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../tmp/evil.txt"))
            zos.write("x".toByteArray())
            zos.closeEntry()
        }
        val dest = tmp.newFolder("slip2")
        try {
            ZipPackageImporter.extract(ByteArrayInputStream(baos.toByteArray()), dest)
            fail("expected SecurityException")
        } catch (_: SecurityException) {
            // ok
        }
    }

    @Test
    fun duplicateExtract_overwritesSameDest() {
        val zip = buildZip("index.md" to "# V1\n")
        val dest = tmp.newFolder("dup")
        ZipPackageImporter.extract(ByteArrayInputStream(zip), dest)
        assertTrue(File(dest, "index.md").readText().contains("V1"))
        val zip2 = buildZip("index.md" to "# V2\n")
        ZipPackageImporter.extract(ByteArrayInputStream(zip2), dest)
        assertTrue(File(dest, "index.md").readText().contains("V2"))
    }

    @Test
    fun relativeImage_resolvesInsidePackage() {
        val root = tmp.newFolder("doc")
        File(root, "package-manifest.json").writeText("{}")
        val md = File(root, "index.md").also { it.writeText("# t") }
        File(root, "images").mkdirs()
        // real png bytes for extension+read checks
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02,
            0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C,
            0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(),
            0xC0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE.toByte(),
            0xD4.toByte(), 0xEF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        val img = File(root, "images/a.png").also { it.writeBytes(png) }
        val resolved = ZipPackageImporter.resolvePackageImage(md, "./images/a.png", root)
        assertNotNull(resolved)
        assertEquals(img.canonicalPath, resolved!!.canonicalPath)
    }

    @Test
    fun relativeImage_traversalRejected() {
        val root = tmp.newFolder("doc2")
        val md = File(root, "index.md").also { it.writeText("# t") }
        assertNull(ZipPackageImporter.resolvePackageImage(md, "../secret.png", root))
        assertNull(ZipPackageImporter.resolvePackageImage(md, "a/../../secret.png", root))
    }

    @Test
    fun isZipFileName() {
        assertTrue(ZipPackageImporter.isZipFileName("book.zip"))
        assertFalse(ZipPackageImporter.isZipFileName("book.md"))
    }

    private fun buildZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
