package com.m1racle.reader.data.anchor

import com.m1racle.reader.data.local.BookmarkLocationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** EPUB text anchor storage form: deterministic, lossless, and never throws on decode. */
class EpubTextAnchorCodecTest {

    private fun anchor(
        href: String = "OEBPS/text/ch01.xhtml",
        quote: String = "第一章 开头的原文",
        end: Int? = null,
    ) = DocumentAnchor.EpubText(
        spineHref = href,
        blockIndex = 12,
        charStart = 345,
        charEnd = end,
        parserVersion = 1,
        quote = quote,
    )

    @Test fun `round trip is identical for reading and range anchors`() {
        listOf(anchor(), anchor(end = 400), anchor(quote = "")).forEach { value ->
            val encoded = AnchorCodec.encode(value)
            repeat(3) { assertEquals(encoded, AnchorCodec.encode(value)) }
            assertTrue(encoded, encoded.startsWith("epub:"))
            assertEquals(value, AnchorCodec.decode(encoded))
        }
        assertTrue(AnchorCodec.encode(anchor()).contains("|12|345,-|v1|"))
        assertTrue(AnchorCodec.encode(anchor(end = 400)).contains("|12|345,400|v1|"))
    }

    @Test fun `href and quote with separators CJK hash plus percent and spaces round trip`() {
        val hrefs = listOf("a|b,c.xhtml", "章节/第一章.xhtml", "text/ch#1.xhtml", "a b+c%20d.xhtml", "x;y=z&w?.html")
        val quotes = listOf("他说：“a|b, c”#1", "  多  空白 ", "100% + 50%", "emoji 😀 end", "|,|,|", "v1|epub:")
        hrefs.forEach { href ->
            quotes.forEach { quote ->
                val value = anchor(href = href, quote = quote)
                assertEquals(value, AnchorCodec.decode(AnchorCodec.encode(value)))
            }
        }
    }

    @Test fun `malformed epub strings decode to null without throwing`() {
        listOf(
            "epub:", "epub:|||", "epub:a.xhtml|1|2,-|v1", "epub:a.xhtml|1|2,-|v1|q|extra",
            "epub:a.xhtml|-1|2,-|v1|q", "epub:a.xhtml|1|-2,-|v1|q", "epub:a.xhtml|1|5,4|v1|q",
            "epub:a.xhtml|1|2|v1|q", "epub:a.xhtml|1|2,3,4|v1|q", "epub:a.xhtml|1|2,-|1|q",
            "epub:a.xhtml|1|2,-|v-1|q", "epub:a.xhtml|1|2,-|vx|q", "epub:|1|2,-|v1|q",
            "epub:%ZZ|1|2,-|v1|q", "epub:a.xhtml|1|2,-|v1|%Z", "epub:a.xhtml|x|2,-|v1|q",
            "epub:a.xhtml|99999999999|2,-|v1|q", "EPUB:a.xhtml|1|2,-|v1|q", "epub:a.xhtml|1|2,-|v1|q|",
        ).forEach { raw ->
            val decoded = try {
                AnchorCodec.decode(raw)
            } catch (e: Exception) {
                fail("decode threw for '$raw': $e")
            }
            assertNull("expected null for '$raw'", decoded)
        }
    }

    @Test fun `every truncation of a valid anchor is rejected or still a valid EPUB anchor`() {
        val good = AnchorCodec.encode(anchor(end = 400))
        for (i in good.indices) {
            val raw = good.substring(0, i)
            val decoded = try {
                AnchorCodec.decode(raw)
            } catch (e: Exception) {
                fail("decode threw for '$raw': $e")
            }
            if (decoded != null) assertTrue(raw, decoded is DocumentAnchor.EpubText)
        }
    }

    @Test fun `EPUB_TEXT is registered and projects the spine order as its sort key`() {
        assertEquals("EPUB_TEXT", BookmarkLocationKind.EPUB_TEXT.name)
        assertSame(EpubTextAnchorResolver, AnnotationAnchorResolvers.forLocationKind("EPUB_TEXT"))
        assertSame(EpubTextAnchorResolver, AnnotationAnchorResolvers.forAnchor(anchor()))
        val projection = EpubTextAnchorResolver.projectInBook(anchor(), spineIndex = 4)
        assertEquals("EPUB_TEXT", projection.locationKind)
        assertEquals(4, projection.location)
        assertEquals(anchor(), EpubTextAnchorResolver.resolve("EPUB_TEXT", 4, projection.encoded))
        assertNull(EpubTextAnchorResolver.resolve("PAGE_INDEX", 4, projection.encoded))
        assertNull(EpubTextAnchorResolver.resolve("EPUB_TEXT", 4, "md:1|-"))
        assertNull(EpubTextAnchorResolver.resolve("EPUB_TEXT", 4, null))
        // Without the chapter order a projection would carry a wrong sort key: refuse loudly.
        try {
            EpubTextAnchorResolver.project(anchor())
            fail("project without spine order must not guess")
        } catch (_: IllegalStateException) {
        }
    }
}
