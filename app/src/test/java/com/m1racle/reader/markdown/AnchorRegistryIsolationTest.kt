package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-document isolation: each parse builds a fresh [AnchorRegistry].
 * Opening B must not keep A's fragment map.
 */
class AnchorRegistryIsolationTest {

    @Test
    fun separateParses_doNotShareRegistryOrTargets() {
        val docA = MarkdownParser.parse(
            """
            <a id="only-in-a"></a>
            # 文档A
            [跳](#only-in-a)
            """.trimIndent(),
        )
        val docB = MarkdownParser.parse(
            """
            <a id="only-in-b"></a>
            # 文档B
            [跳](#only-in-b)
            """.trimIndent(),
        )

        assertNotSame(docA.anchorRegistry, docB.anchorRegistry)
        assertEquals(0, docA.anchorRegistry.resolve("only-in-a")?.blockIndex)
        assertNull(docA.anchorRegistry.resolve("only-in-b"))
        assertEquals(0, docB.anchorRegistry.resolve("only-in-b")?.blockIndex)
        assertNull(docB.anchorRegistry.resolve("only-in-a"))
        assertTrue(docA.toc.none { it.title.contains("文档B") })
        assertTrue(docB.toc.none { it.title.contains("文档A") })
        assertEquals("文档A", docA.toc.single { it.level == 1 }.title)
        assertEquals("文档B", docB.toc.single { it.level == 1 }.title)
    }

    @Test
    fun emptyRegistry_missIsSafe() {
        assertNull(AnchorRegistry.Empty.resolve("anything"))
        assertEquals(0, AnchorRegistry.Empty.size)
    }
}
