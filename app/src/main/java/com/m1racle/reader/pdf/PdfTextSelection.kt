package com.m1racle.reader.pdf

import android.content.Context
import android.net.Uri
import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/** Glyphs are in displayed crop-box coordinates; line order comes from PDFBox text extraction. */
data class PdfSelectedText(val anchor: DocumentAnchor.PdfPage, val excerpt: String)

data class PdfSelectableGlyph(val text: String, val rect: NormalizedRect)
data class PdfSelectableLine(
    val glyphs: List<PdfSelectableGlyph>,
    val flowLeft: Float,
    val flowRight: Float,
)
data class PdfSelectablePage(
    val page: Int,
    val width: Float,
    val height: Float,
    val lines: List<PdfSelectableLine>,
    val unavailableReason: String? = null,
) {
    data class Hit(val line: Int, val glyph: Int)

    fun hit(x: Float, y: Float, tolerance: Float = 0f): Hit? {
        var best: Hit? = null
        var distance = Float.POSITIVE_INFINITY
        lines.forEachIndexed { line, value -> value.glyphs.forEachIndexed { glyph, g ->
            if (g.text.isBlank()) return@forEachIndexed
            val r = g.rect
            val dx = max(max(r.x - x, x - r.x - r.width), 0f) * width
            val dy = max(max(r.y - y, y - r.y - r.height), 0f) * height
            val d = dx * dx + dy * dy
            if (d < distance && (tolerance < 0 || d <= tolerance * tolerance)) {
                best = Hit(line, glyph); distance = d
            }
        } }
        return best
    }

    fun selection(start: Hit, end: Hit): PdfSelectedText {
        val forward = start.line < end.line || (start.line == end.line && start.glyph <= end.glyph)
        val a = if (forward) start else end
        val b = if (forward) end else start
        val left = minOf(lines[a.line].flowLeft, lines[b.line].flowLeft)
        val right = maxOf(lines[a.line].flowRight, lines[b.line].flowRight)
        val rects = mutableListOf<NormalizedRect>()
        val excerpts = mutableListOf<String>()
        for (line in a.line..b.line) {
            val value = lines[line]
            // A neighboring column must not enter a selection confined to this column.
            if (value.flowLeft >= right || value.flowRight <= left) continue
            val from = if (line == a.line) a.glyph else 0
            val to = if (line == b.line) b.glyph else value.glyphs.lastIndex
            val selected = value.glyphs.subList(from, to + 1).dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
            if (selected.isEmpty()) continue
            rects += bounds(selected.map { it.rect })
            excerpts += selected.joinToString("") { it.text }
        }
        return PdfSelectedText(DocumentAnchor.PdfPage(page, rects), excerpts.joinToString("\n"))
    }

    companion object {
        fun bounds(rects: List<NormalizedRect>): NormalizedRect {
            val x = rects.minOf { it.x }; val y = rects.minOf { it.y }
            return NormalizedRect(x, y, rects.maxOf { it.x + it.width } - x, rects.maxOf { it.y + it.height } - y)
        }
    }
}

/** Existing PDFBox extracts text only. System PdfRenderer remains the only page renderer. */
object PdfTextSelectionReader {
    suspend fun read(context: Context, uri: String, page: Int): PdfSelectablePage = withContext(Dispatchers.IO) {
        check(PdfBoxRuntime.ensureInit(context))
        val cancellation = coroutineContext
        context.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
            requireNotNull(input) { "无法读取 PDF 文字" }
            PDDocument.load(input, PdfBoxRuntime.memorySetting()).use { document ->
                extract(document, page) { cancellation.ensureActive() }
            }
        }
    }

    internal fun extract(document: PDDocument, pageIndex: Int, checkActive: () -> Unit = {}): PdfSelectablePage {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val displayW = if (rotation % 180 == 0) box.width else box.height
        val displayH = if (rotation % 180 == 0) box.height else box.width
        if (!document.currentAccessPermission.canExtractContent()) {
            return PdfSelectablePage(pageIndex, displayW, displayH, emptyList(), "此 PDF 不允许选择文字")
        }
        val lines = mutableListOf<PdfSelectableLine>()
        var current = mutableListOf<PdfSelectableGlyph>()
        var flowLeft = 0f; var flowRight = 0f; var baseline = Float.NaN; var glyphHeight = 0f
        fun flush() {
            if (current.isNotEmpty()) lines += PdfSelectableLine(current.toList(), flowLeft, flowRight)
            current = mutableListOf(); baseline = Float.NaN
        }
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                checkActive()
                super.processTextPosition(text)
            }
            override fun writeLineSeparator() { flush() }
            override fun writeParagraphSeparator() { flush() }
            override fun writeString(text: String, positions: MutableList<TextPosition>) {
                for (p in positions) {
                    checkActive()
                    // Vertical/skewed text needs oriented quads, never guess a large rectangular mark.
                    if (p.dir != 0f || p.font.isVertical || abs(p.textMatrix.shearY) > 0.01f || abs(p.textMatrix.shearX) > 0.01f) {
                        flush(); continue
                    }
                    val x = p.xDirAdj; val y = p.yDirAdj
                    val w = p.widthDirAdj; val h = p.heightDir.coerceAtLeast(p.fontSizeInPt * 0.5f)
                    if (!listOf(x,y,w,h).all { it.isFinite() } || w <= 0f || h <= 0f || p.unicode.isNullOrEmpty()) continue
                    if (current.isNotEmpty() && (abs(y-baseline) > max(h,glyphHeight)*0.45f || x-flowRight > max(h,glyphHeight)*1.8f || x < flowLeft - h)) flush()
                    if (current.isEmpty()) { flowLeft = x; baseline = y }
                    val l = (x / box.width).coerceIn(0f,1f)
                    val t = ((y-h) / box.height).coerceIn(0f,1f)
                    val r = ((x+w) / box.width).coerceIn(0f,1f)
                    val b = ((y+h*0.18f) / box.height).coerceIn(0f,1f)
                    if (r <= l || b <= t) continue
                    val rect = when(rotation) {
                        90 -> NormalizedRect(1-b,l,b-t,r-l)
                        180 -> NormalizedRect(1-r,1-b,r-l,b-t)
                        270 -> NormalizedRect(t,1-r,b-t,r-l)
                        else -> NormalizedRect(l,t,r-l,b-t)
                    }
                    // PDFBox may emit an implied word separator without an explicit space glyph.
                    if (current.isNotEmpty() && x-flowRight > h*0.2f && !current.last().text.endsWith(' ') && !p.unicode.startsWith(' ')) {
                        current[current.lastIndex] = current.last().let { it.copy(text = it.text + " ") }
                    }
                    current += PdfSelectableGlyph(p.unicode,rect)
                    flowRight = x+w; glyphHeight = h
                }
            }
        }.apply { startPage = pageIndex+1; endPage = pageIndex+1; sortByPosition = true }
        stripper.getText(document)
        flush()
        return PdfSelectablePage(pageIndex, displayW, displayH, lines,
            if (lines.isEmpty()) "此页没有可选择的横排文字，扫描页暂不支持文字高亮" else null)
    }
}
