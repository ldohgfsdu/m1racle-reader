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
    fun `tag soup voids and html entities are sanitized`() {
        val soup = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
            "<p>a&nbsp;b<br>第一行<br/>第二行</p><img src=\"x.png\"><hr>" +
            "</body></html>"
        val (blocks, _) = EpubXhtmlParser.parse(soup.toByteArray(Charsets.UTF_8))
        val text = blocks.filterIsInstance<EpubBlock.Paragraph>()
            .flatMap { it.inlines }.joinToString("") { it.text }
        assertTrue(text.contains("a b"))
        assertFalse(text.contains(" "))
        assertTrue(blocks.any { it is EpubBlock.Image })
        assertTrue(blocks.any { it is EpubBlock.Divider })
    }

    @Test
    fun `broken chapter degrades to placeholder without shifting spine`() {
        val dir = tmpDir()
        val epub = File(dir, "bad-chapter.epub")
        java.util.zip.ZipOutputStream(epub.outputStream()).use { zip ->
            fun entry(name: String, data: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                "<?xml version=\"1.0\"?><container version=\"1.0\" " +
                    "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                    "<rootfiles><rootfile full-path=\"content.opf\" " +
                    "media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            )
            entry(
                "content.opf",
                "<?xml version=\"1.0\"?><package version=\"2.0\" " +
                    "xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"b\">" +
                    "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                    "<dc:title>t</dc:title></metadata><manifest>" +
                    "<item id=\"c1\" href=\"c1.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                    "<item id=\"c2\" href=\"c2.xhtml\" media-type=\"application/xhtml+xml\"/>" +
                    "</manifest><spine><itemref idref=\"c1\"/><itemref idref=\"c2\"/></spine></package>",
            )
            entry(
                "c1.xhtml",
                "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><p>好章节</p></body></html>",
            )
            entry(
                "c2.xhtml",
                "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><p>坏章节</p></div></body></html>",
            )
        }
        val book = EpubPackageImporter.open(epub, File(dir, "out"))
        assertEquals(2, book.chapters.size)
        assertTrue(
            book.chapters[0].blocks.any {
                it is EpubBlock.Paragraph && it.inlines.any { inline -> inline.text.contains("好章节") }
            },
        )
        assertEquals(
            listOf(EpubBlock.Unsupported("本章排版异常，已跳过")),
            book.chapters[1].blocks,
        )
    }

    @Test
    fun `xxe external entity is not resolved`() {
        // DOCTYPE is allowed (real books use it); the external file must not leak in.
        val doc = EpubPackageImporter.parseXml(
            fixture("evil-xxe.opf").readBytes(), "OPF",
        )
        val title = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title")
            .item(0).textContent.orEmpty()
        assertFalse(title.contains("root:"))
    }
}
