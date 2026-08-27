package com.m1racle.reader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAnchorRegistryTest {

    @Test
    fun parse_buildsStableChineseAndDuplicateAnchors() {
        val result = MarkdownParser.parse(
            """
            # 总览
            [跳到重复标题](#检查结果-2)
            ## 检查结果
            正文
            ## 检查结果
            结尾
            ## 检查结果
            """.trimIndent(),
        )

        assertEquals(listOf("总览", "检查结果", "检查结果-2", "检查结果-3"), result.anchorRegistry.targets.map { it.anchorId })
        assertEquals(0, result.anchorRegistry.resolve("总览")?.blockIndex)
        assertEquals(2, result.anchorRegistry.resolve("#检查结果")?.blockIndex)
        assertEquals(4, result.anchorRegistry.resolve("检查结果-2")?.blockIndex)
        assertEquals(6, result.anchorRegistry.resolve("检查结果-3")?.blockIndex)
        assertNull(result.anchorRegistry.resolve("不存在"))
        assertEquals(result.blocks.size, result.blockIds.size)
        assertTrue(result.blockIds.distinct().size == result.blockIds.size)
    }

    @Test
    fun inlineLinks_keepTheirDestinationsAsAnnotations() {
        val parsed = InlineMarkdownParser.parse("[目录](#总览) 与 [网页](https://example.com)")
        val links = parsed.getStringAnnotations(
            tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
            start = 0,
            end = parsed.length,
        )

        assertEquals(listOf("#总览", "https://example.com"), links.map { it.item })
    }

    @Test
    fun authoredFragmentWithPunctuation_resolvesGeneratedSlug() {
        val result = MarkdownParser.parse("# 一、心电图纸与正常波形")

        assertEquals("一心电图纸与正常波形", result.anchorRegistry.targets.single().anchorId)
        assertEquals(
            0,
            result.anchorRegistry.resolve("#一、心电图纸与正常波形")?.blockIndex,
        )
    }

    @Test
    fun linkedHeading_slugUsesPlainTitleNotRawMarkdown() {
        val result = MarkdownParser.parse("## [检查结果](#check-result)")

        assertEquals("检查结果", result.toc.single().title)
        assertEquals("检查结果", result.toc.single().anchor)
        assertEquals(0, result.anchorRegistry.resolve("检查结果")?.blockIndex)
        assertEquals(0, result.anchorRegistry.resolve("check-result")?.blockIndex)
        // Must not slug the raw brackets / fragment into the primary id.
        assertNull(
            result.anchorRegistry.targets
                .firstOrNull { it.anchorId.contains("check-result") && it.anchorId != "check-result" },
        )
    }
}
