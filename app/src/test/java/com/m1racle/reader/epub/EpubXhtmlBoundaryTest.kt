package com.m1racle.reader.epub

import org.junit.Assert.*
import org.junit.Test

class EpubXhtmlBoundaryTest {
    private fun parse(body: String) = EpubXhtmlParser.parse(
        "<html><body>$body</body></html>".toByteArray(),
    )

    @Test fun `deep containers are rejected as a bounded input error`() {
        assertThrows(EpubException.TooLarge::class.java) {
            parse("<div>".repeat(10_000) + "<p>x</p>" + "</div>".repeat(10_000))
        }
    }

    @Test fun `nested blocks share the chapter block budget`() {
        assertThrows(EpubException.TooLarge::class.java) {
            parse("<div>" + "<p>x</p>".repeat(20_001) + "</div>")
        }
        assertEquals(20_000, parse("<div>" + "<p>x</p>".repeat(20_000) + "</div>").first.size)
    }

    @Test fun `inline boundaries preserve one authored space and emphasis`() {
        val block = parse("<p> Hello <strong> world </strong> <a href='x'>again</a>! </p>").first.single() as EpubBlock.Paragraph
        assertEquals("Hello world again!", block.inlines.joinToString("") { it.text })
        assertTrue(block.inlines.any { it.bold && it.text.contains("world") })
        val adjacent = parse("<p>中<strong>文</strong>测试</p>").first.single() as EpubBlock.Paragraph
        assertEquals("中文测试", adjacent.inlines.joinToString("") { it.text })
    }

    @Test fun `preformatted code preserves newlines indentation and long content`() {
        val code = "first\n    second\n" + "x".repeat(8_100)
        assertEquals(code, (parse("<pre>$code</pre>").first.single() as EpubBlock.Code).text)
    }

    @Test fun `inline and nested quote anchors point to the containing top level block`() {
        val (_, anchors) = parse("<p>first</p><p>second <span><a id='target'/>part</span></p>" +
            "<blockquote><p>a</p><p id='quoted'>b</p></blockquote>")
        assertEquals(1, anchors["target"])
        assertEquals(2, anchors["quoted"])
    }

    @Test fun `id and name anchors are registered at block and inline positions`() {
        val (_, anchors) = parse(
            "<a id='block-id' name='block-name'>block</a>" +
                "<p>inline <a name='name-only'/><a id='' name='inline-name'/>" +
                "<a id='inline-id' name='different-name'>run</a></p>",
        )

        assertEquals(0, anchors["block-id"])
        assertEquals(0, anchors["block-name"])
        assertEquals(1, anchors["name-only"])
        assertEquals(1, anchors["inline-name"])
        assertEquals(1, anchors["inline-id"])
        assertEquals(1, anchors["different-name"])
        assertFalse(anchors.containsKey(""))
    }

    @Test fun `duplicate id and name anchors keep the first document position`() {
        val (_, anchors) = parse(
            "<p>first<a id='first-id' name='first-name'/></p>" +
                "<p><a id='first-id' name='first-name'>duplicate</a></p>" +
                "<p id='cross-key'>first</p>" +
                "<p><a name='cross-key'>duplicate</a></p>" +
                "<p id='' name=''>empty</p>",
        )

        assertEquals(0, anchors["first-id"])
        assertEquals(0, anchors["first-name"])
        assertEquals(2, anchors["cross-key"])
        assertFalse(anchors.containsKey(""))
    }

    @Test fun `unrelated name attributes do not shadow a later fragment id`() {
        val (_, anchors) = parse("<p name='target'>first</p><p id='target'>target</p>")
        assertEquals(1, anchors["target"])
    }
}
