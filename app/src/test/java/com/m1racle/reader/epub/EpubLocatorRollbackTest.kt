package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubLocatorRollbackTest {
    private val text = "起始文字，" + "旧版保存的实际正文可以在换字体后继续阅读。".repeat(8)
    private val book = EpubBook(EpubMetadata("书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("c", "OPS/chap+ter.xhtml", true, 0)), emptyList(),
        listOf(EpubChapter(0, "OPS/chap+ter.xhtml", "章", listOf(
            EpubBlock.Paragraph(listOf(EpubInline("开篇"))),
            EpubBlock.Paragraph(listOf(EpubInline(text))),
        ), emptyMap())))
    private fun record(anchor: String?) = DocumentEntity("book", "原书", "file:///book.epub",
        "application/epub+zip", 500, 1, 1, 73, 0, .6f, null, 0,
        DocumentType.EPUB.name, readingAnchor = anchor)
    private fun locator(href: String = "OPS/chap+ter.xhtml", quote: String = text.substring(5)) =
        "epub-locator:" + org.json.JSONObject().put("href", href)
            .put("locations", org.json.JSONObject().put("progression", .7).put("totalProgression", .6))
            .put("text", org.json.JSONObject().put("highlight", quote)).toString()

    @Test fun convertsVisibleSourceTextAndDoesNotReuseTheSdkIndexAsAParagraph() {
        val after = rollbackEpubPosition(book, record(locator()))
        val anchor = AnchorCodec.decode(after.readingAnchor) as DocumentAnchor.EpubText
        assertEquals(1, anchor.blockIndex)
        assertEquals(5, anchor.charStart)
        assertEquals(EpubXhtmlParser.BLOCK_RULES_VERSION, anchor.parserVersion)
        assertEquals(0, after.lastScrollIndex)
        assertEquals(.6f, after.readingProgress, 0f)
        assertEquals(EpubRestoreTier.Exact, EpubReadingAnchors.restore(book, after).tier)
        assertSame(after, rollbackEpubPosition(book, after))
    }

    @Test fun literalPlusAndEncodedHrefBothResolveTheOriginalChapter() {
        val literal = rollbackEpubPosition(book, record(locator()))
        val encoded = rollbackEpubPosition(book, record(locator("OPS/chap%2Bter.xhtml#paragraph")))
        assertEquals(literal.readingAnchor, encoded.readingAnchor)
    }

    @Test fun sdkRootPathResolvesTheOpfRelativeChapterAndQuote() {
        val after = rollbackEpubPosition(book, record(locator("BOOK/OPS/chap%2Bter.xhtml#paragraph")))
        val anchor = AnchorCodec.decode(after.readingAnchor) as DocumentAnchor.EpubText
        assertEquals("OPS/chap+ter.xhtml", anchor.spineHref)
        assertEquals(1, anchor.blockIndex)
        assertEquals(5, anchor.charStart)
        assertEquals(EpubRestoreTier.Exact, EpubReadingAnchors.restore(book, after).tier)
    }

    @Test fun ambiguousPathSuffixDoesNotGuessAChapter() {
        val second = book.chapters.single().copy(spineIndex = 1, href = "chap+ter.xhtml")
        val ambiguous = book.copy(spine = book.spine + EpubSpineItem("other", second.href, true, 1),
            chapters = book.chapters + second)
        val after = rollbackEpubPosition(ambiguous, record(locator("BOOK/OPS/chap+ter.xhtml")))
        assertEquals(EpubRestoreTier.Progress, EpubReadingAnchors.restore(ambiguous, after).tier)
    }

    @Test fun unmatchedQuotePreservesChapterFallbackAndSaveProtection() {
        val after = rollbackEpubPosition(book, record(locator(quote = "文本已被修改且无法匹配")))
        val restored = EpubReadingAnchors.restore(book, after)
        assertEquals(EpubRestoreTier.ChapterStart, restored.tier)
        assertTrue(restored.degraded)
        assertTrue(restored.needsDegradedNotice)
    }

    @Test fun missingChapterAndCorruptLocatorUsePercentageRatherThanSdkPageNumber() {
        for (value in listOf(locator("missing.xhtml"), "epub-locator:{broken")) {
            val after = rollbackEpubPosition(book, record(value))
            val restored = EpubReadingAnchors.restore(book, after)
            assertEquals(EpubRestoreTier.Progress, restored.tier)
            assertEquals(.6f, restored.progress!!, 0f)
            assertEquals(0, after.lastScrollIndex)
            assertFalse(after.readingAnchor?.startsWith("epub-locator:") == true)
        }
    }

    @Test fun existing43AnchorsAndUnopenedBooksAreUntouched() {
        val native = record(AnchorCodec.encode(EpubReadingAnchors.anchorAt(book, EpubLocation(0, 1, 5))!!))
        assertSame(native, rollbackEpubPosition(book, native))
        val unopened = record(null)
        assertSame(unopened, rollbackEpubPosition(book, unopened))
    }
}
