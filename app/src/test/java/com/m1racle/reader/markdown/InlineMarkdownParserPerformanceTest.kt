package com.m1racle.reader.markdown

import androidx.compose.ui.text.AnnotatedString
import com.m1racle.reader.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Exercises the shipped [InlineMarkdownParser] / [MarkdownPerformance] path used by
 * first-entry paragraph rendering — not a reimplementation of the parser.
 */
class InlineMarkdownParserPerformanceTest {
    private val recordsDiagnostics = BuildConfig.BUILD_TYPE == "qa"
    private fun recorded(count: Long): Long = if (recordsDiagnostics) count else 0L

    @Before
    fun resetCounters() {
        MarkdownPerformance.reset()
    }

    @Test
    fun mayContainInlineMarkup_ignoresMidWordUnderscoreOnly() {
        assertFalse(InlineMarkdownParser.mayContainInlineMarkup("token SCROLL_BODY density"))
        assertFalse(
            InlineMarkdownParser.mayContainInlineMarkup(
                "This is paragraph 0 for continuous scroll performance measurement. " +
                    "Chinese 中文段落编号 0 with mixed content. Repeat token SCROLL_BODY for density.",
            ),
        )
        assertTrue(InlineMarkdownParser.mayContainInlineMarkup("hello **world**"))
        assertTrue(InlineMarkdownParser.mayContainInlineMarkup("see _emphasis_ here"))
        assertTrue(InlineMarkdownParser.mayContainInlineMarkup("a `code` span"))
        assertTrue(InlineMarkdownParser.mayContainInlineMarkup("[link](https://example.com)"))
    }

    @Test
    fun parse_plainBodyUsesFastPathAndPreservesText() {
        val plain =
            "This is paragraph 0 for continuous scroll performance measurement. " +
                "Chinese 中文段落编号 0 with mixed content for realistic layout. " +
                "Lorem ipsum dolor sit amet. Repeat token SCROLL_BODY for density."
        val result = InlineMarkdownParser.parse(plain)
        assertEquals(plain, result.text)
        assertTrue(result.spanStyles.isEmpty())
        val snap = MarkdownPerformance.snapshot()
        assertEquals(recorded(1), snap.inlineParseCount)
        assertEquals(recorded(1), snap.inlineParseFastPathCount)
    }

    @Test
    fun parse_realMarkupDoesNotUseFastPathAndKeepsSpans() {
        val source = "bold **strong** and `code` plus [x](https://example.com)"
        val result = InlineMarkdownParser.parse(source)
        assertTrue(result.spanStyles.isNotEmpty())
        assertTrue(
            result.getStringAnnotations(
                InlineMarkdownParser.LINK_ANNOTATION_TAG,
                0,
                result.length,
            ).isNotEmpty(),
        )
        val snap = MarkdownPerformance.snapshot()
        assertEquals(recorded(1), snap.inlineParseCount)
        assertEquals(0, snap.inlineParseFastPathCount)
    }

    @Test
    fun plainFixture_paragraphsDominateInlineParseWork() {
        val fixture = loadPlainFixture()
        val parsed = MarkdownParser.parse(fixture)
        val typeCounts = parsed.blocks.groupingBy { it.blockContentType() }.eachCount()
        assertTrue(
            "expected paragraph-heavy fixture, got $typeCounts",
            (typeCounts["paragraph"] ?: 0) >= 50,
        )
        assertTrue(
            "expected heading-heavy fixture, got $typeCounts",
            (typeCounts["heading"] ?: 0) >= 50,
        )

        MarkdownPerformance.reset()
        var plainParses = 0
        var markupParses = 0
        for (block in parsed.blocks) {
            val text = when (block) {
                is MarkdownBlock.Paragraph -> block.text
                is MarkdownBlock.Heading -> block.text
                is MarkdownBlock.Quote -> block.text
                is MarkdownBlock.ListBlock -> block.items.joinToString("\n")
                is MarkdownBlock.CodeBlock -> block.code
                else -> continue
            }
            if (InlineMarkdownParser.mayContainInlineMarkup(text)) {
                markupParses++
            } else {
                plainParses++
            }
            // Drive the real shipped entry point used by paragraph/heading views.
            val annotated: AnnotatedString = InlineMarkdownParser.parse(text)
            assertNotNull(annotated)
        }
        val snap = MarkdownPerformance.snapshot()
        assertTrue("plain body should dominate first-entry text", plainParses > markupParses)
        assertEquals(recorded(plainParses.toLong()), snap.inlineParseFastPathCount)
        assertEquals(recorded((plainParses + markupParses).toLong()), snap.inlineParseCount)
        // Paragraph + heading text work is the dominant population for this fixture.
        val contentBlocks = (typeCounts["paragraph"] ?: 0) + (typeCounts["heading"] ?: 0)
        assertTrue(contentBlocks > (typeCounts["table"] ?: 0) * 10)
    }

    @Test
    fun blockComposeAttribution_recordsDominantType() {
        MarkdownPerformance.reset()
        MarkdownPerformance.traceBlockCompose("paragraph") {
            InlineMarkdownParser.parse("plain SCROLL_BODY text without markup markers except mid_word")
        }
        MarkdownPerformance.traceBlockCompose("paragraph") {
            InlineMarkdownParser.parse("another plain body line with SCROLL_BODY token")
        }
        MarkdownPerformance.traceBlockCompose("heading") {
            InlineMarkdownParser.parse("Section 1")
        }
        MarkdownPerformance.traceBlockCompose("table") {
            // Simulate a heavier table compose slice with real parse work.
            repeat(20) {
                InlineMarkdownParser.parse("cell **bold** `$it`")
            }
        }
        val snap = MarkdownPerformance.snapshot()
        assertEquals(recorded(23), snap.inlineParseCount)
        val summary = MarkdownPerformance.formatBlockComposeSummary()
        if (recordsDiagnostics) {
            assertEquals(2, snap.blockCompose.first { it.type == "paragraph" }.count)
            assertEquals(1, snap.blockCompose.first { it.type == "heading" }.count)
            assertEquals(1, snap.blockCompose.first { it.type == "table" }.count)
            assertNotNull(snap.dominantBlockType())
            assertTrue(summary.contains("dominant="))
            assertTrue(summary.contains("paragraph:"))
        } else {
            assertTrue(snap.blockCompose.isEmpty())
            assertNull(snap.dominantBlockType())
            assertTrue(summary.contains("dominant=null"))
            assertTrue(summary.contains("blocks=[]"))
        }
    }

    private fun loadPlainFixture(): String {
        val candidates = listOf(
            Paths.get("dist/r9-markdown-scroll-perf/fixtures/PERF_PLAIN.md"),
            Paths.get("../dist/r9-markdown-scroll-perf/fixtures/PERF_PLAIN.md"),
            Paths.get("../../dist/r9-markdown-scroll-perf/fixtures/PERF_PLAIN.md"),
        )
        for (path in candidates) {
            if (Files.isRegularFile(path)) {
                return String(Files.readAllBytes(path), Charsets.UTF_8)
            }
        }
        // Fallback embedded sample matching Large Search Document structure.
        return buildString {
            appendLine("# Perf Plain Scroll Fixture")
            repeat(60) { section ->
                appendLine("## Section $section")
                appendLine()
                appendLine(
                    "This is paragraph $section for continuous scroll performance measurement. " +
                        "Chinese 中文段落编号 $section with mixed content for realistic layout. " +
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                        "Repeat token SCROLL_BODY for density.",
                )
                appendLine()
            }
        }
    }
}
