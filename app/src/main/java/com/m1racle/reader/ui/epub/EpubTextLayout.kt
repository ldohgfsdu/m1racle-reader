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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.viewer.components.ReaderTypography
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled
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
internal const val MAX_MEASURE_ROW_CHARS = 2_048

internal data class EpubTextRow(
    val spineIndex: Int,
    val chapterTitle: String,
    val chapterStart: Boolean,
    val text: AnnotatedString,
    val style: TextStyle,
    val shaded: Boolean = false,
    val imagePath: String? = null,
    val sourceRow: Int = 0,
    val sourceTextStart: Int = 0,
    val continuation: Boolean = sourceTextStart > 0,
    val blockIndex: Int = 0,
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

    // A navigation section can start inside one spine XHTML, not just at its file boundary.
    private val sectionStarts = pages.indices.filter { index ->
        val first = pages[index].slices.first()
        first.start == 0 && rows[first.row].chapterStart
    }

    fun sectionPages(page: Int): IntRange {
        if (page !in pages.indices) return IntRange.EMPTY
        val found = sectionStarts.binarySearch(page)
        val section = if (found >= 0) found else -found - 2
        return sectionStarts.getOrElse(section) { 0 } until
            sectionStarts.getOrElse(section + 1) { pages.size }
    }

    fun pageForToc(book: EpubBook, node: EpubTocNode): Int? {
        val chapter = book.chapters.firstOrNull { it.spineIndex == node.spineIndex } ?: return null
        val block = epubTocBlock(chapter, node) ?: return null
        val row = rows.indexOfFirst { it.spineIndex == node.spineIndex && it.blockIndex == block }
        if (row < 0) return null
        return pages.pageForText(row, 0)
    }

    fun pageForText(sourceRow: Int, offset: Int): Int {
        if (rows.isEmpty()) return 0
        val row = rows.indexOfLast { it.sourceRow == sourceRow && it.sourceTextStart <= offset }.coerceAtLeast(0)
        return pages.pageForText(row, (offset - rows[row].sourceTextStart).coerceAtLeast(0))
    }

    fun pageForPosition(sourceRow: Int, pixelOffset: Int): Int {
        var remaining = pixelOffset.toFloat()
        val candidates = rows.indices.filter { rows[it].sourceRow == sourceRow }
        for (row in candidates) {
            val height = rowMetrics[row].lines.lastOrNull()?.bottom ?: 0f
            if (remaining < height || row == candidates.last()) return pages.pageForPosition(row, remaining.toInt())
            remaining -= height
        }
        return 0
    }

    /** Persist original block rows, so splitting a paragraph does not shift saved identities. */
    fun positionAt(slice: EpubPageSlice): Triple<Int, Int, Int> {
        val row = rows[slice.row]
        var top = slice.sourceTop
        var previous = slice.row - 1
        while (previous >= 0 && rows[previous].sourceRow == row.sourceRow) {
            top += rowMetrics[previous].lines.lastOrNull()?.bottom ?: 0f
            previous--
        }
        return Triple(row.sourceRow, kotlin.math.ceil(top).toInt(), row.sourceTextStart + slice.start)
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

/** Shared target resolution for page breaks and TOC jumps; invalid fragments never mean chapter start. */
private fun epubTocBlock(chapter: com.m1racle.reader.epub.EpubChapter, node: EpubTocNode): Int? {
    if (chapter.spineIndex != node.spineIndex) return null
    val fragment = node.href.substringAfter('#', "")
    val block = if (fragment.isEmpty()) 0 else chapter.idAnchors[fragment] ?: return null
    return block.takeIf { it in chapter.blocks.indices }
}

internal fun epubTextRows(book: EpubBook, settings: ReaderSettings, base: TextStyle, checkpoint: () -> Unit = {}): List<EpubTextRow> {
    val bodySize = ReaderTypography.bodyFontSize.scaled(settings.fontScale)
    val body = base.copy(fontSize = bodySize, lineHeight = ReaderTypography.bodyLineHeight(settings),
        fontWeight = ReaderTypography.bodyWeight, lineBreak = LineBreak.Paragraph)
    val chapters = book.readingOrder.associateBy { it.spineIndex }
    val sectionTitles = mutableMapOf<Pair<Int, Int>, String>()
    fun visit(nodes: List<EpubTocNode>) {
        nodes.forEach { node ->
            checkpoint()
            val chapter = chapters[node.spineIndex]
            if (chapter != null) epubTocBlock(chapter, node)?.let { block ->
                sectionTitles.putIfAbsent(chapter.spineIndex to block, node.title)
            }
            visit(node.children)
        }
    }
    visit(book.toc)
    return buildList {
        var sourceRow = 0
        book.readingOrder.forEach { chapter ->
            var title = chapter.title ?: "第 ${chapter.spineIndex + 1} 章"
            chapter.blocks.forEachIndexed { index, block ->
                checkpoint()
                val sectionTitle = sectionTitles[chapter.spineIndex to index]
                    ?: (block as? EpubBlock.Heading)?.takeIf { it.level == 1 }?.text
                if (sectionTitle != null) title = sectionTitle
                val startsSection = index == 0 || sectionTitle != null
                val style = when (block) {
                    is EpubBlock.Heading -> {
                        val size = ReaderTypography.headingSize(block.level, settings.fontScale)
                        body.copy(fontSize = size, lineHeight = ReaderTypography.headingLineHeight(size, settings),
                            fontWeight = if (block.level <= 3) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    is EpubBlock.Code -> body.copy(fontFamily = FontFamily.Monospace,
                        fontSize = ReaderTypography.codeBlockSize(settings.fontScale),
                        lineHeight = ReaderTypography.codeBlockLine(settings.fontScale))
                    is EpubBlock.Paragraph -> body.copy(textIndent = TextIndent(firstLine = bodySize * 2))
                    else -> body
                }
                val imagePath = (block as? EpubBlock.Image)?.resolvedPath
                val text = if (imagePath != null) {
                    // One image stays one row, even when its alternative text is huge.
                    AnnotatedString(block.alt.orEmpty().take(MAX_MEASURE_ROW_CHARS))
                } else {
                    epubBlockText(block)
                }
                // Keep original block/character coordinates while giving each list item
                // its own paragraph gap. A long item's measurement chunks remain continuations.
                val sections = if (block is EpubBlock.ListBlock && block.items.isNotEmpty()) {
                    var offset = 0
                    block.items.mapIndexed { itemIndex, item ->
                        val prefix = if (block.ordered) "${itemIndex + 1}. " else "• "
                        val end = offset + prefix.length + item.sumOf { it.text.length }
                        val itemStyle = style.copy(textIndent = TextIndent(
                            firstLine = 0.sp,
                            restLine = (if (block.ordered) prefix.length * 0.55f else 1.1f).em,
                        ))
                        Triple(offset, end, itemStyle).also { offset = end + 1 }
                    }
                } else listOf(Triple(0, text.length, style))
                sections.forEach { (sectionStart, sectionEnd, sectionStyle) ->
                    var start = sectionStart
                    do {
                        checkpoint()
                        var end = minOf(sectionEnd, start + MAX_MEASURE_ROW_CHARS)
                        // Do not split a UTF-16 surrogate pair.
                        if (end < sectionEnd && end > start && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
                        val restIndent = sectionStyle.textIndent?.restLine ?: 0.sp
                        add(EpubTextRow(chapter.spineIndex, title, startsSection && start == 0,
                            text.subSequence(start, end),
                            if (start == sectionStart) sectionStyle else sectionStyle.copy(
                                textIndent = TextIndent(firstLine = restIndent, restLine = restIndent)),
                            shaded = block is EpubBlock.Quote || block is EpubBlock.Code,
                            imagePath = imagePath, sourceRow = sourceRow, sourceTextStart = start,
                            continuation = start > sectionStart, blockIndex = index))
                        start = end
                    } while (start < sectionEnd)
                }
                sourceRow++
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
        is EpubBlock.Heading -> append(block.text)
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
        }, continuation = row.continuation))
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
