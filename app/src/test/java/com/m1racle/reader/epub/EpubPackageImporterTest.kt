package com.m1racle.reader.epub

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EpubPackageImporterTest {
    private fun fixture(name: String): File {
        val direct = File("src/test/resources/fixtures/epub", name)
        if (direct.isFile) return direct
        val alt = File("app/src/test/resources/fixtures/epub", name)
        if (alt.isFile) return alt
        fail("missing fixture $name")
        error("unreachable")
    }

    private fun tmpDir(): File {
        val dir = File.createTempFile("epub-test", "").let { it.delete(); File(it.path + "-d") }
        dir.mkdirs()
        return dir
    }

    @Test
    fun `epub2 parses spine toc and blocks`() {
        val book = EpubPackageImporter.open(fixture("sample-epub2.epub"), tmpDir())
        assertEquals("测试书 EPUB2", book.metadata.title)
        assertEquals(listOf("墨阅"), book.metadata.creators)
        assertEquals("2.0", book.metadata.version)
        assertFalse(book.metadata.hasEncryption)
        assertFalse(book.metadata.isFixedLayout)
        assertEquals(2, book.spine.size)
        assertEquals("ch1.xhtml", book.spine[0].href)
        assertTrue(book.spine[0].linear)
        assertFalse(book.spine[1].linear)
        assertEquals(2, book.toc.size)
        assertEquals("第一章", book.toc[0].title)
        assertEquals(0, book.toc[0].spineIndex)
        assertEquals("ch2.xhtml#sec", book.toc[1].href)
        assertEquals(1, book.toc[1].spineIndex)
        val ch1 = book.chapters[0]
        assertTrue(ch1.blocks.any { it is EpubBlock.Heading && it.level == 1 && it.text.contains("第一章") })
        assertTrue(ch1.blocks.any { it is EpubBlock.ListBlock && it.items.size == 2 })
        val bold = ch1.blocks.filterIsInstance<EpubBlock.Paragraph>()
            .flatMap { it.inlines }.firstOrNull { it.bold }
        assertEquals("加粗", bold?.text)
    }

    @Test
    fun `epub3 prefers nav toc and degrades image to placeholder`() {
        val book = EpubPackageImporter.open(fixture("sample-epub3.epub"), tmpDir())
        assertEquals("测试书 EPUB3", book.metadata.title)
        assertEquals("3.0", book.metadata.version)
        assertEquals(2, book.toc.size)
        assertEquals("第一章", book.toc[0].title)
        assertEquals(1, book.toc[1].children.size)
        assertEquals("尾段", book.toc[1].children[0].title)
        val ch2 = book.chapters[1]
        assertTrue(ch2.blocks.any { it is EpubBlock.Image })
        assertTrue(ch2.blocks.any { it is EpubBlock.Quote })
        assertTrue(ch2.blocks.any { it is EpubBlock.Divider })
    }

    @Test
    fun `metadata reads without extraction`() {
        val meta = EpubPackageImporter.readMetadata(fixture("sample-epub3.epub"))
        assertEquals("测试书 EPUB3", meta.title)
        assertFalse(meta.hasEncryption)
    }

    @Test
    fun `zip slip is rejected`() {
        try {
            EpubPackageImporter.open(fixture("evil-slip.epub"), tmpDir())
            fail("expected UnsafeEntry")
        } catch (e: EpubException.UnsafeEntry) {
            // expected
        }
    }

    @Test
    fun `encryption is rejected`() {
        try {
            EpubPackageImporter.open(fixture("evil-encryption.epub"), tmpDir())
            fail("expected Protected")
        } catch (e: EpubException.Protected) {
            // expected
        }
        assertTrue(EpubPackageImporter.readMetadata(fixture("evil-encryption.epub")).hasEncryption)
    }

    @Test
    fun `random bytes are rejected`() {
        try {
            EpubPackageImporter.open(fixture("not-epub.epub"), tmpDir())
            fail("expected NotAnEpub")
        } catch (e: EpubException.NotAnEpub) {
            // expected
        }
    }

    @Test
    fun `xxe doctype is rejected`() {
        try {
            EpubPackageImporter.parseXml(
                fixture("evil-xxe.opf").readBytes(), "OPF",
            )
            fail("expected Corrupt")
        } catch (e: EpubException.Corrupt) {
            // expected
        }
    }
}
