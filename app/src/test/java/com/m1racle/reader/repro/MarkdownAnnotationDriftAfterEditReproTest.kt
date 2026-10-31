package com.m1racle.reader.repro

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.MarkdownBlockAnchorResolver
import com.m1racle.reader.markdown.MarkdownAnnotationRemapper
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * REPRO 20261031-md-repro F1：编辑保存后 Markdown 批注漂移。
 * saveDocumentEdits 只换 uri/hash，批注行原样保留；Markdown 锚点只有 blockIndex。
 * 预期：在修复前失败。
 */
class MarkdownAnnotationDriftAfterEditReproTest {

    private fun text(b: MarkdownBlock): String = when (b) {
        is MarkdownBlock.Paragraph -> b.text
        is MarkdownBlock.Heading -> b.text
        else -> b.toString()
    }

    @Test
    fun highlightStaysOnSameParagraphAfterInsertingParagraphBefore() {
        val before = "# 标题\n\n第一段。\n\n目标段落：这里被高亮。\n"
        val after = "# 标题\n\n新插入的一段。\n\n第一段。\n\n目标段落：这里被高亮。\n"
        val v1 = MarkdownParser.parse(before).blocks
        val target = v1.indexOfFirst { text(it).startsWith("目标段落") }
        val stored = MarkdownBlockAnchorResolver.project(DocumentAnchor.MarkdownBlock(target, 0, 4))
        val v2 = MarkdownParser.parse(after).blocks
        // 修复后 saveDocumentEdits → rebaseAnnotations 用同一重定位函数改写批注行，再按新行解析
        val old = MarkdownBlockAnchorResolver.resolve(stored.locationKind, stored.location, stored.encoded)
            as DocumentAnchor.MarkdownBlock
        val moved = MarkdownAnnotationRemapper.remap(v1, v2, old.blockIndex, old.charStart, old.charEnd)!!
        val rewritten = MarkdownBlockAnchorResolver.project(DocumentAnchor.MarkdownBlock(moved.blockIndex, moved.charStart, moved.charEnd))
        val anchor = MarkdownBlockAnchorResolver.resolve(rewritten.locationKind, rewritten.location, rewritten.encoded)
            as DocumentAnchor.MarkdownBlock
        assertEquals(
            "编辑保存后高亮应仍落在原段落上（F1 批注漂移）",
            "目标段落：这里被高亮。",
            v2.getOrNull(anchor.blockIndex)?.let(::text),
        )
    }

    @Test
    fun saveDocumentEditsRebasesOrRemapsMarkdownAnnotations() {
        val src = File("src/main/java/com/m1racle/reader/data/repository/DocumentRepository.kt").readText()
        val start = src.indexOf("suspend fun saveDocumentEdits(")
        val next = Regex("""\n\s*(private |internal )?(suspend )?fun """).find(src, start + 30)?.range?.first ?: src.length
        val body = src.substring(start, next)
        val touches = listOf("highlightDao", "noteDao", "remapMarkdown", "rebaseAnnotations").any { body.contains(it) }
        assertFalse("saveDocumentEdits 未处理批注行（F1：Markdown 批注不随正文修订重定位）", !touches)
    }
}
