package com.markleaf.app.packageimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real package directory + real PNG file (IHDR width/height > 0).
 * Avoids android.graphics.BitmapFactory stubs in pure JVM unit tests.
 */
class PackageImageIntegrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun packageRelativeImages_resolveAndDecode() {
        val pkg = tmp.newFolder("packages", "test-package")
        File(pkg, "package-manifest.json").writeText("""{"name":"test"}""")
        File(pkg, "index.md").writeText(
            """
            # 第一章 总论

            ![fig](./assets/figure.png)
            ![fig2](assets/figure.png)
            """.trimIndent(),
        )
        val assets = File(pkg, "assets").also { it.mkdirs() }
        val pngBytes = minimalPng()
        File(assets, "figure.png").writeBytes(pngBytes)

        val entry = File(pkg, "index.md")
        val root = ZipPackageImporter.findPackageRoot(entry)
        assertEquals(pkg.canonicalPath, root.canonicalPath)

        val a = ZipPackageImporter.resolvePackageImage(entry, "./assets/figure.png", root)
        val b = ZipPackageImporter.resolvePackageImage(entry, "assets/figure.png", root)
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a!!.isFile && a.canRead())
        assertTrue(ZipPackageImporter.isUnder(a, root))

        val (w, h) = readPngSize(a.readBytes())
        assertTrue(w > 0 && h > 0)

        assertNull(ZipPackageImporter.resolvePackageImage(entry, "../figure.png", root))
        assertNull(ZipPackageImporter.resolvePackageImage(entry, "/absolute/a.png", root))
        assertNull(ZipPackageImporter.resolvePackageImage(entry, "file://x", root))
    }

    @Test
    fun fileEntrypointUri_loadsPackageImage() {
        val pkg = tmp.newFolder("packages", "pkg2")
        File(pkg, "package-manifest.json").writeText("{}")
        val entry = File(pkg, "index.md").also { it.writeText("# T\n") }
        File(pkg, "assets").mkdirs()
        val img = File(pkg, "assets/figure.png").also { it.writeBytes(minimalPng()) }

        val entryUri = "file://${entry.absolutePath}"
        val resolved = com.markleaf.app.image.LocalImageLoader.resolveRelativeImage(
            entryUri,
            "./assets/figure.png",
        )
        assertNotNull(resolved)
        assertEquals(img.canonicalPath, resolved!!.canonicalPath)
        val (w, h) = readPngSize(resolved.readBytes())
        assertTrue(w > 0 && h > 0)
    }

    @Test
    fun zipExtract_keepsAssetsAndMetadata() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("index.md"))
            zos.write("# 第一章 总论\n\n![a](./assets/figure.png)\n".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("package-manifest.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/figure.png"))
            zos.write(minimalPng())
            zos.closeEntry()
        }
        val dest = tmp.newFolder("packages", "from-zip")
        val result = ZipPackageImporter.extract(ByteArrayInputStream(baos.toByteArray()), dest)
        assertTrue(File(dest, "assets/figure.png").isFile)
        assertEquals("index.md", result.entryRelativePath)
        assertTrue(ZipPackageImporter.extractFirstH1(result.entryFile.readText())!!.contains("第一章"))
        assertTrue(ZipPackageImporter.packageTotalSize(dest) > 0)

        val img = ZipPackageImporter.resolvePackageImage(
            result.entryFile,
            "./assets/figure.png",
            result.packageRoot,
        )
        assertNotNull(img)
        val (w, h) = readPngSize(img!!.readBytes())
        assertTrue(w > 0 && h > 0)
    }

    private fun readPngSize(data: ByteArray): Pair<Int, Int> {
        assertTrue(data.size >= 24)
        assertEquals(0x89.toByte(), data[0])
        assertEquals('P'.code.toByte(), data[1])
        val w = ByteBuffer.wrap(data, 16, 4).int
        val h = ByteBuffer.wrap(data, 20, 4).int
        return w to h
    }

    /** 1×1 PNG */
    private fun minimalPng(): ByteArray {
        return byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02,
            0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C,
            0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(),
            0xC0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE.toByte(),
            0xD4.toByte(), 0xEF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
    }
}
