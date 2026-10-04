package com.m1racle.reader.epub

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class EpubPackageImporterTest {
    @Test
    fun `incomplete extraction with container is rebuilt before opening`() {
        val dir = tmpDir()
        try {
            EpubPackageImporter.extract(fixture("sample-epub2.epub"), dir)
            val chapter = dir.walkTopDown().first { it.name == "ch1.xhtml" }
            chapter.writeText("<broken")
            val book = EpubPackageImporter.openCached(fixture("sample-epub2.epub"), dir, "a".repeat(64))
            assertTrue(book.chapters.first().blocks.any { it is EpubBlock.Heading })
            assertTrue(File(dir, ".m1racle-extraction-complete").isFile)
            // A different source identity also invalidates a completed tree.
            chapter.writeText("<broken")
            val reopened = EpubPackageImporter.openCached(fixture("sample-epub2.epub"), dir, "b".repeat(64))
            assertTrue(reopened.chapters.first().blocks.any { it is EpubBlock.Heading })
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `repeated spine references count toward whole book text budget`() {
        val dir = tmpDir()
        try {
            writeSyntheticBook(dir, 6, "<p>${"x".repeat(800_000)}</p>")
            expectTooLarge { EpubPackageImporter.parseExtracted(dir) }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `whole book block and spine budgets reject amplification`() {
        val dir = tmpDir()
        try {
            writeSyntheticBook(dir, 6, "<p>x</p>".repeat(8_000))
            expectTooLarge { EpubPackageImporter.parseExtracted(dir) }
            writeSyntheticBook(dir, EpubPackageImporter.MAX_SPINE_ITEMS + 1, "<p>x</p>")
            expectTooLarge { EpubPackageImporter.parseExtracted(dir) }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `whole book XML budget includes repeated references even without body text`() {
        val dir = tmpDir()
        try {
            writeSyntheticBook(dir, 6, "<!--${"x".repeat(6 * 1024 * 1024)}-->")
            expectTooLarge { EpubPackageImporter.parseExtracted(dir) }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `failed extraction does not publish a partial cache or completion marker`() {
        val parent = tmpDir()
        try {
            val archive = File(parent, "bad.epub")
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("META-INF/container.xml"))
                zip.write("<container/>".toByteArray()); zip.closeEntry()
                zip.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
                zip.write("unsafe".toByteArray()); zip.closeEntry()
            }
            val cache = File(parent, "cache").apply { mkdirs() }
            File(cache, "prior").writeText("old cache")
            try { EpubPackageImporter.openCached(archive, cache, "c".repeat(64)); fail("unsafe EPUB accepted") }
            catch (_: EpubException.UnsafeEntry) { }
            assertEquals("old cache", File(cache, "prior").readText())
            assertFalse(File(cache, ".m1racle-extraction-complete").exists())
            assertFalse(parent.listFiles().orEmpty().any { it.name.startsWith("cache.extract-") })
        } finally { parent.deleteRecursively() }
    }

    private fun expectTooLarge(action: () -> Unit) {
        try { action(); fail("expected whole-book limit") } catch (_: EpubException.TooLarge) { }
    }

    private fun writeSyntheticBook(root: File, repetitions: Int, body: String) {
        File(root, "META-INF").mkdirs()
        File(root, "META-INF/container.xml").writeText("""<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""")
        File(root, "book.opf").writeText("""<package version="3.0"><metadata/><manifest><item id="ch" href="ch.xhtml" media-type="application/xhtml+xml"/></manifest><spine>${"<itemref idref=\"ch\"/>".repeat(repetitions)}</spine></package>""")
        File(root, "ch.xhtml").writeText("<html><body>$body</body></html>")
    }
    companion object {
        private val platformFactoryName = DocumentBuilderFactory.newInstance().javaClass.name
    }

    @Test
    fun `epub opens when parser does not implement XInclude configuration`() {
        val property = "javax.xml.parsers.DocumentBuilderFactory"
        val previous = System.getProperty(property)
        System.setProperty(property, NoXIncludeFactory::class.java.name)
        try {
            for (name in listOf("sample-epub2.epub", "sample-epub3.epub")) {
                val dir = tmpDir()
                try {
                    val book = EpubPackageImporter.open(fixture(name), dir)
                    assertEquals(2, book.chapters.size)
                    assertTrue(book.toc.isNotEmpty())
                    assertTrue(book.chapters.first().blocks.any { it is EpubBlock.Heading })
                } finally {
                    dir.deleteRecursively()
                }
            }
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
        }
    }

    /** Models Android's unsupported setter, including attempts to set false. */
    class NoXIncludeFactory : DocumentBuilderFactory() {
        private val delegate = DocumentBuilderFactory.newInstance(platformFactoryName, javaClass.classLoader)

        override fun setXIncludeAware(state: Boolean) {
            throw UnsupportedOperationException("This parser does not support specification \"Unknown\" version \"0.0\"")
        }

        override fun newDocumentBuilder(): javax.xml.parsers.DocumentBuilder {
            delegate.isNamespaceAware = isNamespaceAware
            delegate.isValidating = isValidating
            delegate.isExpandEntityReferences = isExpandEntityReferences
            return delegate.newDocumentBuilder()
        }

        override fun setAttribute(name: String, value: Any) = delegate.setAttribute(name, value)
        override fun getAttribute(name: String): Any = delegate.getAttribute(name)
        override fun setFeature(name: String, value: Boolean) = delegate.setFeature(name, value)
        override fun getFeature(name: String): Boolean = delegate.getFeature(name)
    }

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
    fun `epub3 prefers nav toc and resolves local image`() {
        val dir = tmpDir()
        val book = EpubPackageImporter.open(fixture("sample-epub3.epub"), dir)
        assertEquals("测试书 EPUB3", book.metadata.title)
        assertEquals("3.0", book.metadata.version)
        assertEquals(2, book.toc.size)
        assertEquals("第一章", book.toc[0].title)
        assertEquals(1, book.toc[1].children.size)
        assertEquals("尾段", book.toc[1].children[0].title)
        val ch2 = book.chapters[1]
        val image = ch2.blocks.filterIsInstance<EpubBlock.Image>().single()
        assertEquals("示例图", image.alt)
        assertEquals("img.png", image.src)
        assertTrue(image.resolvedPath!!.endsWith("img.png"))
        assertTrue(java.io.File(image.resolvedPath!!).isFile)
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
        val image = blocks.filterIsInstance<EpubBlock.Image>().single()
        assertEquals("x.png", image.src)
        assertTrue(blocks.any { it is EpubBlock.Divider })
    }

    @Test
    fun `svg cover image and table rows become readable blocks`() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:xlink="http://www.w3.org/1999/xlink">
            <body>
            <div><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
            <image width="10" height="10" xlink:href="cover.jpeg"/>
            </svg></div>
            <table><tr><th>问题</th><th>去哪看</th></tr>
            <tr><td>几乎不花钱</td><td>第一章</td></tr></table>
            </body></html>
        """.trimIndent()
        val (blocks, _) = EpubXhtmlParser.parse(html.toByteArray(Charsets.UTF_8))
        val image = blocks.filterIsInstance<EpubBlock.Image>().single()
        assertEquals("cover.jpeg", image.src)
        val tableText = blocks.filterIsInstance<EpubBlock.Paragraph>()
            .flatMap { it.inlines }.joinToString("|") { it.text }
        assertTrue(tableText, tableText.contains("问题") && tableText.contains("几乎不花钱"))
        assertFalse(blocks.any { it is EpubBlock.Unsupported && it.label == "表格" })
    }

    @Test
    fun `relative image stays in extract root and escapes are rejected`() {
        val root = tmpDir()
        val chapterDir = File(root, "OEBPS/text").apply { mkdirs() }
        val image = File(root, "OEBPS/images/pic.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val outside = File(root.parentFile, "epub-outside-${root.name}.bin").apply { writeBytes(byteArrayOf(9)) }
        try {
            val ok = EpubPackageImporter.resolvePackagedFile(chapterDir, "../images/pic.png", root)
            assertEquals(image.canonicalPath, ok?.canonicalPath)
            assertEquals(null, EpubPackageImporter.resolvePackagedFile(chapterDir, "../../../${outside.name}", root))
            assertEquals(null, EpubPackageImporter.resolvePackagedFile(chapterDir, "https://example.com/x.png", root))
        } finally {
            outside.delete()
        }
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
