package com.m1racle.reader.ui.epub

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBlock
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubInline
import com.m1racle.reader.epub.EpubTocNode
import com.m1racle.reader.image.LocalImageLoader
import java.io.File
import kotlinx.coroutines.yield

internal data class EpubTextRow(
    val spineIndex: Int,
    val chapterTitle: String,
    val chapterStart: Boolean,
    val text: AnnotatedString,
    val style: TextStyle,
    val shaded: Boolean = false,
    val imagePath: String? = null,
)

internal class EpubTextLayout(
    val rows: List<EpubTextRow>,
    val layouts: List<TextLayoutResult>,
    val pages: List<EpubPage>,
) {
    fun pageForToc(book: EpubBook, node: EpubTocNode): Int? {
        val start = rows.indexOfFirst { it.chapterStart && it.spineIndex == node.spineIndex }
        if (start < 0) return null
        val fragment = node.href.substringAfter('#', "")
        val chapter = book.chapters.firstOrNull { it.spineIndex == node.spineIndex }
        val block = chapter?.idAnchors?.get(fragment)
        val row = if (block != null && block in chapter.blocks.indices) start + block else start
        return pages.pageForText(row, 0)
    }
}

internal fun epubTextRows(book: EpubBook, settings: ReaderSettings, base: TextStyle): List<EpubTextRow> {
    val bodySize = 16.sp * settings.fontScale
    val body = base.copy(fontSize = bodySize, lineHeight = bodySize * settings.lineHeightScale)
    return buildList {
        book.readingOrder.forEach { chapter ->
            val title = chapter.title ?: "第 ${chapter.spineIndex + 1} 章"
            chapter.blocks.forEachIndexed { index, block ->
                val style = when (block) {
                    is EpubBlock.Heading -> {
                        val size = (22 - block.level.coerceIn(1, 4)).sp * settings.fontScale
                        body.copy(fontSize = size, lineHeight = size * settings.lineHeightScale, fontWeight = FontWeight.Bold)
                    }
                    is EpubBlock.Code -> body.copy(fontFamily = FontFamily.Monospace)
                    is EpubBlock.Paragraph -> body.copy(textIndent = TextIndent(firstLine = bodySize * 2))
                    else -> body
                }
                val imagePath = (block as? EpubBlock.Image)?.resolvedPath
                val text = if (imagePath != null) {
                    AnnotatedString(block.alt.orEmpty())
                } else {
                    epubBlockText(block)
                }
                add(EpubTextRow(chapter.spineIndex, title, index == 0, text, style,
                    shaded = block is EpubBlock.Quote || block is EpubBlock.Code,
                    imagePath = imagePath))
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlines(inlines: List<EpubInline>) {
    inlines.forEach { inline ->
        withStyle(SpanStyle(
            fontWeight = if (inline.bold) FontWeight.Bold else null,
            fontStyle = if (inline.italic) FontStyle.Italic else null,
            fontFamily = if (inline.code) FontFamily.Monospace else null,
        )) { append(inline.text) }
    }
}

private fun epubBlockText(block: EpubBlock): AnnotatedString = buildAnnotatedString {
    when (block) {
        is EpubBlock.Heading -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(block.text) }
        is EpubBlock.Paragraph -> appendInlines(block.inlines)
        is EpubBlock.ListBlock -> block.items.forEachIndexed { index, item ->
            if (index > 0) append('\n')
            append(if (block.ordered) "${index + 1}. " else "• ")
            appendInlines(item)
        }
        is EpubBlock.Quote -> block.blocks.forEachIndexed { index, child ->
            if (index > 0) append('\n')
            append(epubBlockText(child))
        }
        is EpubBlock.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(block.text) }
        is EpubBlock.Image -> append(block.alt ?: if (block.resolvedPath == null) "本书图片暂不支持显示" else "")
        is EpubBlock.Divider -> append("────────")
        is EpubBlock.Unsupported -> append(block.label)
    }
}

internal suspend fun measureEpub(
    rows: List<EpubTextRow>,
    measurer: TextMeasurer,
    width: Int,
    height: Int,
    gap: Float,
    shadedInset: Int,
): EpubTextLayout {
    val layouts = ArrayList<TextLayoutResult>(rows.size)
    val metrics = ArrayList<EpubRowMetrics>(rows.size)
    rows.forEachIndexed { index, row ->
        // Cooperatively cancel/recompose while a large book or new settings are laid out.
        if (index % 16 == 0) yield()
        if (row.imagePath != null) {
            val bounds = LocalImageLoader.probeBounds(File(row.imagePath))
            val fitted = epubFitImage(bounds?.first ?: 0, bounds?.second ?: 0, width, height)
            val layout = measurer.measure(
                if (row.text.isEmpty()) AnnotatedString(" ") else row.text,
                style = row.style,
                constraints = Constraints(maxWidth = width.coerceAtLeast(1)), skipCache = true)
            layouts.add(layout)
            metrics.add(EpubRowMetrics(row.spineIndex, row.chapterStart,
                listOf(EpubLine(0f, fitted.height.toFloat(), 0, 1))))
        } else {
            val layout = measurer.measure(row.text, style = row.style,
                constraints = Constraints(maxWidth = (width - if (row.shaded) shadedInset * 2 else 0).coerceAtLeast(1)),
                skipCache = true)
            layouts.add(layout)
            metrics.add(EpubRowMetrics(row.spineIndex, row.chapterStart,
                (0 until layout.lineCount).map { line ->
                    EpubLine(layout.getLineTop(line), layout.getLineBottom(line),
                        layout.getLineStart(line), layout.getLineEnd(line))
                }))
        }
    }
    return EpubTextLayout(rows, layouts, paginateEpub(metrics, height.toFloat(), gap))
}
