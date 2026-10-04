package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EpubReadingAnchors.restore] directly on the parsed book: no rows, pager,
 * Compose or measured page. Identity is chapter href + in-chapter block +
 * in-block source offset, verified by a quote.
 */
class EpubReadingAnchorsTest {

    private fun p(text: String) = EpubBlock.Paragraph(listOf(EpubInline(text)))

    private val longText = (0 until 900).joinToString(" ") { "词$it" } // > 2 048 chars

    private fun chapter0(extraSplit: Boolean = false) = EpubChapter(0, "text/c0.xhtml", "第一章",
        buildList {
            add(EpubBlock.Heading(1, "第一章"))
            if (extraSplit) {
                // A parser change that splits the long paragraph into two blocks.
                add(p(longText.substring(0, 1_000)))
                add(p(longText.substring(1_000)))
            } else {
                add(p(longText))
            }
            add(EpubBlock.ListBlock(true, List(5) { listOf(EpubInline("条目 $it 的内容")) }))
            add(EpubBlock.Image(alt = null, src = "a.png", resolvedPath = "/missing/a.png"))
            add(p("第一章结尾段落"))
        }, emptyMap())

    private val notes = EpubChapter(1, "text/notes.xhtml", "注释", listOf(p("非线性注释")), emptyMap())
    private val chapter2 = EpubChapter(2, "text/c2.xhtml", "第二章",
        List(10) { p("第二章第 $it 段，" + "内容".repeat(it + 1)) } + p("重复的句子在这里") + p("重复的句子在这里"),
        emptyMap())
    private val empty = EpubChapter(3, "text/empty.xhtml", null, listOf(EpubBlock.Unsupported("空章节")), emptyMap())

    private fun book(c0: EpubChapter = chapter0(), c2: EpubChapter = chapter2) = EpubBook(
        EpubMetadata("书", emptyList(), "zh", null, "3.0", false, false),
        listOf(
            EpubSpineItem("c0", c0.href, true, 0),
            EpubSpineItem("notes", notes.href, false, 1),
            EpubSpineItem("c2", c2.href, true, 2),
            EpubSpineItem("empty", empty.href, true, 3),
        ),
        emptyList(),
        listOf(c0, notes, c2, empty),
    )

    private fun saved(anchor: DocumentAnchor.EpubText?, progress: Float = 0f) = EpubSavedPosition(anchor, 0, 0, progress)

    private fun exact(location: EpubLocation) = EpubRestoreResult(EpubRestoreTier.Exact, location = location)

    /** Category 6: split long paragraph (continuation middle), list item N, image, empty chapter. */
    private val probes = listOf(
        EpubLocation(0, 1, 3_000), // inside the second 2 048-char measurement chunk
        EpubLocation(0, 1, 2_048), // at a continuation start
        EpubLocation(0, 2, 3 * 12 + 2), // fourth list item
        EpubLocation(0, 3, 0), // image only
        EpubLocation(3, 0, 0), // empty chapter placeholder
        EpubLocation(2, 7, 5),
        EpubLocation(0, 0, 0),
    )

    @Test fun `every probe location round trips exactly through the stored anchor`() {
        val book = book()
        probes.forEach { location ->
            val anchor = requireNotNull(EpubReadingAnchors.anchorAt(book, location))
            assertEquals(EpubXhtmlParser.BLOCK_RULES_VERSION, anchor.parserVersion)
            assertNull(anchor.charEnd)
            val stored = AnchorCodec.encode(anchor)
            val document = documentWith(stored)
            assertEquals(location.toString(), exact(location), EpubReadingAnchors.restore(book, document))
        }
        val listItem = EpubReadingAnchors.anchorAt(book, probes[2])!!
        assertEquals("text/c0.xhtml", listItem.spineHref)
        assertEquals(2, listItem.blockIndex)
        assertTrue(listItem.quote, listItem.quote.startsWith("条目 3 的内容"))
        assertEquals("", EpubReadingAnchors.anchorAt(book, probes[3])!!.quote)
        assertNull(EpubReadingAnchors.anchorAt(book, EpubLocation(9, 0, 0)))
        assertNull(EpubReadingAnchors.anchorAt(book, EpubLocation(0, 99, 0)))
    }

    @Test fun `an extra block in an earlier chapter does not move later chapter anchors`() {
        val anchor = EpubReadingAnchors.anchorAt(book(), EpubLocation(2, 6, 4))!!
        val reparsed = book(c0 = chapter0(extraSplit = true))
        assertEquals(exact(EpubLocation(2, 6, 4)), EpubReadingAnchors.restore(reparsed, saved(anchor)))
        // Only chapter 0 itself changed block numbers; its later blocks relocate by quote.
        val inChapter0 = EpubReadingAnchors.anchorAt(book(), EpubLocation(0, 4, 2))!!
        assertEquals(EpubRestoreResult(EpubRestoreTier.Quote, location = EpubLocation(0, 5, 2)),
            EpubReadingAnchors.restore(reparsed, saved(inChapter0)))
    }

    @Test fun `changed block rules or a stale block number relocate by quote within the chapter`() {
        val book = book()
        val anchor = EpubReadingAnchors.anchorAt(book, EpubLocation(2, 6, 3))!!
        val expected = EpubRestoreResult(EpubRestoreTier.Quote, location = EpubLocation(2, 6, 3))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor.copy(parserVersion = anchor.parserVersion + 1))))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor.copy(blockIndex = 999))))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor.copy(charStart = 100_000))))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor.copy(blockIndex = 2))))
    }

    @Test fun `duplicate quotes resolve to the occurrence nearest the saved block`() {
        val book = book()
        val anchor = EpubReadingAnchors.anchorAt(book, EpubLocation(2, 11, 0))!!.copy(parserVersion = 0)
        assertEquals(EpubLocation(2, 11, 0), EpubReadingAnchors.restore(book, saved(anchor)).location)
        assertEquals(EpubLocation(2, 10, 0), EpubReadingAnchors.restore(book, saved(anchor.copy(blockIndex = 9))).location)
    }

    @Test fun `an unfindable quote degrades to the chapter start never the book start`() {
        val book = book()
        val anchor = EpubReadingAnchors.anchorAt(book, EpubLocation(2, 6, 3))!!
            .copy(blockIndex = 999, quote = "这段文字已经不存在了")
        val expected = EpubRestoreResult(EpubRestoreTier.ChapterStart, location = EpubLocation(2, 0, 0))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor, progress = 0.9f)))
        assertEquals(expected, EpubReadingAnchors.restore(book, saved(anchor.copy(quote = ""))))
        assertTrue(expected.degraded)
    }

    @Test fun `changed quote suffix is a second round match even at the original offset`() {
        val original = EpubReadingAnchors.anchorAt(book(), EpubLocation(2, 6, 0))!!
        val changed = original.copy(quote = original.quote.take(12) + "原来的后半句")
        val result = EpubReadingAnchors.restore(book(), saved(changed))
        assertEquals(EpubRestoreTier.QuotePrefix, result.tier)
        assertEquals(EpubLocation(2, 6, 0), result.location)
        assertFalse(result.degraded)
    }

    @Test fun `second round duplicates select nearest block then nearest raw offset`() {
        val prefix = "abcdefghijkl"
        val c2 = chapter2.copy(blocks = listOf(
            p(prefix + "changed"),
            p("x".repeat(10) + prefix + "changed " + prefix + "changed"),
            p(prefix + "changed"),
        ))
        val anchor = DocumentAnchor.EpubText(c2.href, 1, 28, parserVersion = 0, quote = prefix + "old suffix")
        val result = EpubReadingAnchors.restore(book(c2 = c2), saved(anchor))
        assertEquals(EpubRestoreTier.QuotePrefix, result.tier)
        assertEquals(EpubLocation(2, 1, 30), result.location)
    }

    @Test fun `full quote match takes precedence over closer prefix match`() {
        val quote = "abcdefghijkloriginal"
        val c2 = chapter2.copy(blocks = listOf(p("abcdefghijklchanged"), p(quote)))
        val anchor = DocumentAnchor.EpubText(c2.href, 0, 0, parserVersion = 0, quote = quote)
        val result = EpubReadingAnchors.restore(book(c2 = c2), saved(anchor))
        assertEquals(EpubRestoreTier.Quote, result.tier)
        assertEquals(EpubLocation(2, 1, 0), result.location)
    }

    @Test fun `a missing or non linear chapter falls back to the saved progress`() {
        val book = book()
        val anchor = EpubReadingAnchors.anchorAt(book, EpubLocation(2, 1, 0))!!
        val progress = EpubRestoreResult(EpubRestoreTier.Progress, progress = 0.63f)
        assertEquals(progress, EpubReadingAnchors.restore(book, saved(anchor.copy(spineHref = "text/gone.xhtml"), 0.63f)))
        assertEquals(progress, EpubReadingAnchors.restore(book,
            saved(anchor.copy(spineHref = notes.href, blockIndex = 0, quote = "非线性注释"), 0.63f)))
        assertTrue(progress.degraded)
        // spineIndex is derived: chapter 2 is found by href although a non-linear item precedes it.
        assertEquals(exact(EpubLocation(2, 1, 0)), EpubReadingAnchors.restore(book, saved(anchor)))
    }

    @Test fun `legacy row and pixel records migrate once and anchored records never do`() {
        val book = book()
        assertEquals(EpubRestoreResult(EpubRestoreTier.BookStart), EpubReadingAnchors.restore(book, EpubSavedPosition.Start))
        val legacy = EpubSavedPosition(anchor = null, lastScrollIndex = 6, lastScrollOffset = 120, readingProgress = 0.4f)
        assertEquals(EpubRestoreResult(EpubRestoreTier.LegacyPosition, legacyRow = 6, legacyPixelOffset = 120),
            EpubReadingAnchors.restore(book, legacy))
        assertEquals(EpubRestoreTier.LegacyPosition,
            EpubReadingAnchors.restore(book, legacy.copy(lastScrollIndex = 0, lastScrollOffset = 30)).tier)
        assertFalse(EpubReadingAnchors.restore(book, legacy).degraded)
        // After the first open the reader saves an anchor next to the same legacy fields.
        val anchor = EpubReadingAnchors.anchorAt(book, EpubLocation(2, 1, 0))!!
        val migrated = documentWith(AnchorCodec.encode(anchor), lastScrollIndex = 6, lastScrollOffset = 120)
        val second = EpubReadingAnchors.restore(book, migrated)
        assertNotEquals(EpubRestoreTier.LegacyPosition, second.tier)
        assertEquals(exact(EpubLocation(2, 1, 0)), second)
        // A corrupt stored anchor is treated as absent, never as a crash.
        assertEquals(EpubRestoreTier.LegacyPosition, EpubReadingAnchors.restore(book, documentWith("epub:broken")).tier)
    }

    @Test fun `quotes are normalized bounded and never end in half a surrogate pair`() {
        val text = "  a \n\n b\t\tc" + "😀".repeat(40)
        val quote = epubQuoteAt(text, 0)
        assertTrue(quote, quote.startsWith("a b c"))
        assertTrue(quote.length <= EPUB_QUOTE_CHARS)
        assertFalse(quote.last().isHighSurrogate())
        assertEquals("", epubQuoteAt("abc", 3))
        assertEquals("", epubQuoteAt("abc", 99))
    }

    @Test fun `reading text is the single source of in block offsets`() {
        val list = EpubBlock.ListBlock(true, listOf(listOf(EpubInline("a", bold = true), EpubInline("b")), listOf(EpubInline("c"))))
        assertEquals("1. ab\n2. c", list.readingText())
        val quote = EpubBlock.Quote(listOf(p("x"), EpubBlock.Code("y")))
        assertEquals("x\ny", quote.readingText())
        assertEquals(true, quote.readingRuns().last().code)
        assertEquals("", EpubBlock.Image(null, "a.png", "/a.png").readingText())
        assertEquals("本书图片暂不支持显示", EpubBlock.Image(null, "a.png", null).readingText())
    }

    private fun documentWith(anchor: String?, lastScrollIndex: Int = 3, lastScrollOffset: Int = 10) = DocumentEntity(
        id = "c:book", displayName = "书.epub", uri = "file:///a.epub", mimeType = null, fileSize = null,
        lastOpenedAt = 0, createdAt = 0, lastScrollIndex = lastScrollIndex, lastScrollOffset = lastScrollOffset,
        readingProgress = 0.5f, title = null, headingCount = 0, documentType = "EPUB", readingAnchor = anchor,
    )
}
