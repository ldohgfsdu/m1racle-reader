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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Main-thread work allowed per slice while a book is paginated. Compose text
 * measurement has to happen on the main thread, so the cost is bounded per slice
 * instead of being paid as one long frame. Yielding only redistributes the work;
 * it never changes the resulting pages.
 */
internal const val MEASURE_SLICE_NANOS = 8_000_000L

/**
 * Rows whose measured layout stays resident. A long book is measured once to
 * build the global page list, but its layouts are not all kept: the opening
 * window is pinned and the rest is a bounded rolling cache, so memory no longer
 * grows with book length. A row outside the window is re-measured on demand when
 * its page is actually shown.
 */
internal const val RETAINED_ROW_WINDOW = 64

internal data class EpubTextRow(
    val spineIndex: Int,
    val chapterTitle: String,
    val chapterStart: Boolean,
    val text: AnnotatedString,
    val style: TextStyle,
    val shaded: Boolean = false,
    val imagePath: String? = null,
)

/**
 * Bounded store of measured layouts. The window around the opening anchor is
 * pinned; everything else is a rolling cache, so a long book never holds a
 * layout per row. Evicted rows are re-measured on demand by [EpubTextLayout].
 */
internal class EpubLayoutCache(private val anchorRow: Int) {
    private val pinnedRadius = RETAINED_ROW_WINDOW / 4 - 1
    private val pinnedRows = (anchorRow - pinnedRadius)..(anchorRow + pinnedRadius)
    private val recentCapacity = RETAINED_ROW_WINDOW - pinnedRows.count()
    private val pinned = LinkedHashMap<Int, TextLayoutResult>()
    private val recent = object : LinkedHashMap<Int, TextLayoutResult>(recentCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, TextLayoutResult>?) =
            size > recentCapacity
    }

    val size: Int get() = pinned.size + recent.size

    operator fun get(row: Int): TextLayoutResult? = pinned[row] ?: recent[row]

    fun put(row: Int, layout: TextLayoutResult) {
        if (row in pinnedRows) pinned[row] = layout else recent[row] = layout
    }
}

/**
 * Whole-book pagination result: rows, per-row line metrics, and the page list.
 *
 * [EpubLayoutCache] is a bounded cache, not the measurement output. Page
 * boundaries come from [rowMetrics], which is what makes global "page N of M" and
 * TOC page numbers exact while only a window of layouts stays resident.
 */
internal class EpubTextLayout(
    val rows: List<EpubTextRow>,
    val rowMetrics: List<EpubRowMetrics>,
    val pages: List<EpubPage>,
    private val cache: EpubLayoutCache,
    private val measurer: TextMeasurer,
    private val width: Int,
    private val shadedInset: Int,
) {
    /** Rows currently holding a measured layout. Bounded by [RETAINED_ROW_WINDOW]. */
    val retainedLayoutCount: Int get() = cache.size

    /** Layout for [row], measured on demand when it is not resident. */
    fun layoutFor(row: Int): TextLayoutResult =
        cache[row] ?: measureRow(row).also { cache.put(row, it) }

    private fun measureRow(row: Int): TextLayoutResult {
        val textRow = rows[row]
        val constraints = Constraints(
            maxWidth = if (textRow.imagePath != null) width.coerceAtLeast(1)
            else (width - if (textRow.shaded) shadedInset * 2 else 0).coerceAtLeast(1),
        )
        return measurer.measure(
            if (textRow.imagePath != null && textRow.text.isEmpty()) AnnotatedString(" ") else textRow.text,
            style = textRow.style,
            constraints = constraints,
            skipCache = true,
        )
    }

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

/**
 * Page for every TOC node, resolved once per layout instead of once per
 * recomposition per row. Keyed by [tocKey] so the drawer only does a map lookup.
 */
internal fun epubTocPages(book: EpubBook, layout: EpubTextLayout): Map<String, Int> = buildMap {
    fun visit(nodes: List<EpubTocNode>) {
        nodes.forEach { node ->
            layout.pageForToc(book, node)?.let { put(tocKey(node), it) }
            visit(node.children)
        }
    }
    visit(book.toc)
}

internal fun tocKey(node: EpubTocNode): String = "${node.spineIndex ?: -1}#${node.href}"

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

/** Image intrinsic sizes, probed off the main thread before pagination starts. */
internal suspend fun probeEpubImageBounds(rows: List<EpubTextRow>): Map<Int, Pair<Int, Int>> =
    withContext(Dispatchers.IO) {
        buildMap {
            rows.forEachIndexed { index, row ->
                val path = row.imagePath ?: return@forEachIndexed
                LocalImageLoader.probeBounds(File(path))?.let { put(index, it) }
            }
        }
    }

/**
 * Measure every row once to build the global page list, keeping at most
 * [RETAINED_ROW_WINDOW] layouts resident. Yields to the frame every
 * [sliceNanos] so input, drawing, and recomposition keep the main thread.
 */
internal suspend fun measureEpub(
    rows: List<EpubTextRow>,
    measurer: TextMeasurer,
    width: Int,
    height: Int,
    gap: Float,
    shadedInset: Int,
    imageBounds: Map<Int, Pair<Int, Int>> = emptyMap(),
    anchorRow: Int = 0,
    sliceNanos: Long = MEASURE_SLICE_NANOS,
): EpubTextLayout {
    val metrics = ArrayList<EpubRowMetrics>(rows.size)
    val cache = EpubLayoutCache(anchorRow)
    var sliceStart = System.nanoTime()
    rows.forEachIndexed { index, row ->
        if (System.nanoTime() - sliceStart >= sliceNanos) {
            yield()
            sliceStart = System.nanoTime()
        }
        val isImage = row.imagePath != null
        val bounds = imageBounds[index]
        val fitted = if (isImage) {
            epubFitImage(bounds?.first ?: 0, bounds?.second ?: 0, width, height)
        } else {
            null
        }
        val textWidth = if (isImage) {
            width.coerceAtLeast(1)
        } else {
            (width - if (row.shaded) shadedInset * 2 else 0).coerceAtLeast(1)
        }
        val layout = measurer.measure(
            if (isImage && row.text.isEmpty()) AnnotatedString(" ") else row.text,
            style = row.style,
            constraints = Constraints(maxWidth = textWidth),
            skipCache = true,
        )
        metrics.add(EpubRowMetrics(row.spineIndex, row.chapterStart, if (fitted != null) {
            listOf(EpubLine(0f, fitted.height.toFloat(), 0, 1))
        } else {
            (0 until layout.lineCount).map { line ->
                EpubLine(layout.getLineTop(line), layout.getLineBottom(line),
                    layout.getLineStart(line), layout.getLineEnd(line))
            }
        }))
        cache.put(index, layout)
    }
    return EpubTextLayout(
        rows = rows,
        rowMetrics = metrics,
        pages = paginateEpub(metrics, height.toFloat(), gap),
        cache = cache,
        measurer = measurer,
        width = width,
        shadedInset = shadedInset,
    )
}
