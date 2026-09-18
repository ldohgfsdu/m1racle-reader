package com.m1racle.reader.pdf.edit

import com.m1racle.reader.pdf.PdfBoxRuntime
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.*
import com.tom_roush.pdfbox.contentstream.operator.text.*
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.math.abs

data class PdfTextKey(val page: Int, val operator: Int)

/** Bounds are fractions of the unrotated crop box, with a top-left origin. */
data class PdfEditableText(
    val key: PdfTextKey,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val fontName: String,
    val reason: String? = null,
)

data class PdfEditablePage(
    val index: Int,
    val pageCount: Int,
    val runs: List<PdfEditableText>,
    val reason: String? = null,
)

class PdfEditRejected(message: String) : IOException(message)

/** Stateless IO engine. The caller serializes work and always supplies an immutable source. */
object PdfBodyEditor {
    fun inspect(source: File, pageIndex: Int): PdfEditablePage = load(source).use { doc ->
        checkDocument(doc)
        inspect(doc, pageIndex).first
    }

    /** True content replacement; the original file is never opened for writing. */
    fun writeCopy(source: File, output: File, edits: Map<PdfTextKey, String>) {
        require(source.canonicalFile != output.canonicalFile)
        try {
            load(source).use { doc ->
                checkDocument(doc)
                for ((pageIndex, pageEdits) in edits.entries.groupBy { it.key.page }) {
                    val (page, nativeRuns) = inspect(doc, pageIndex)
                    page.reason?.let { throw PdfEditRejected(it) }
                    val replacements = pageEdits.associate { (key, text) ->
                        val run = nativeRuns.singleOrNull { it.view.key == key }
                            ?: throw PdfEditRejected("所选文字已失效，请重新打开编辑器")
                        key.operator to run.replacement(text)
                    }
                    val pdfPage = doc.getPage(pageIndex)
                    val tokens = PDFStreamParser(pdfPage).also { it.parse() }.tokens
                    val rewritten = ArrayList<Any>()
                    val operands = ArrayList<Any>()
                    var ordinal = 0
                    for (token in tokens) {
                        if (token is Operator) {
                            val replacement = replacements[ordinal++]
                            if (replacement == null) {
                                rewritten.addAll(operands)
                                rewritten.add(token)
                            } else {
                                rewritten.add(replacement)
                                rewritten.add(Operator.getOperator("TJ"))
                            }
                            operands.clear()
                        } else operands.add(token)
                    }
                    if (operands.isNotEmpty()) throw PdfEditRejected("页面内容不完整，无法安全保存")
                    // A new stream isolates pages that share their original /Contents object.
                    val stream = PDStream(doc)
                    stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(rewritten) }
                    pdfPage.setContents(stream)
                }
                doc.save(output)
            }
            // Verify actual serialized text, not a UI overlay or an in-memory mutation.
            load(output).use { saved ->
                for ((pageIndex, expected) in edits.entries.groupBy { it.key.page }) {
                    val runs = inspect(saved, pageIndex).first.runs.associateBy { it.key }
                    for ((key, text) in expected) {
                        if (runs[key]?.text != text) throw PdfEditRejected("保存后的文字校验失败，未输出副本")
                    }
                }
            }
        } catch (e: Exception) {
            output.delete()
            throw e
        }
    }

    private fun load(file: File): PDDocument = try {
        PDDocument.load(file, PdfBoxRuntime.memorySetting())
    } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        throw PdfEditRejected("暂不支持编辑受保护的 PDF")
    }

    private fun checkDocument(doc: PDDocument) {
        if (doc.isEncrypted || !doc.currentAccessPermission.canModify()) {
            throw PdfEditRejected("暂不支持编辑受保护的 PDF")
        }
        if (doc.signatureDictionaries.isNotEmpty()) throw PdfEditRejected("此 PDF 含数字签名，暂不支持正文编辑")
    }

    private fun inspect(doc: PDDocument, index: Int): Pair<PdfEditablePage, List<NativeRun>> {
        if (index !in 0 until doc.numberOfPages) throw PdfEditRejected("页码超出范围")
        val page = doc.getPage(index)
        if (page.rotation != 0 || page.userUnit != 1f) {
            return PdfEditablePage(index, doc.numberOfPages, emptyList(), "此页带旋转或特殊缩放，暂不支持编辑") to emptyList()
        }
        // Bound the uncompressed page before PDFStreamParser materializes its tokens.
        page.contents.use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > 8L * 1024 * 1024) throw PdfEditRejected("此页内容过大，暂不支持编辑")
            }
        }
        val engine = RunReader(page, index)
        engine.processPage(page)
        val reason = engine.pageReason ?: if (engine.runs.isEmpty()) "此页没有可编辑的原生文字，可能是扫描页" else null
        return PdfEditablePage(index, doc.numberOfPages, engine.runs.map { it.view }, reason) to engine.runs
    }

    private class NativeRun(
        val view: PdfEditableText,
        val font: PDFont,
        val fontSize: Float,
        val charSpacing: Float,
        val wordSpacing: Float,
        val originalAdvance: Float,
    ) {
        fun replacement(text: String): COSArray {
            view.reason?.let { throw PdfEditRejected(it) }
            if (text.isBlank() || text.length > 2048 || text.any { it.isISOControl() }) {
                throw PdfEditRejected("请输入 1–2048 个单行字符；此版本暂不支持删除整段")
            }
            if (!simpleCharacters(text)) throw PdfEditRejected("此版本暂不支持需要复杂排版的字符")
            val bytes = try { font.encode(text) } catch (_: Exception) {
                throw PdfEditRejected("原字体不包含输入的字，请使用原字体可显示的字符")
            }
            val decoded = decode(font, bytes)
            if (decoded != text) throw PdfEditRejected("原字体无法准确保存这些字符")
            val advance = advance(font, bytes, fontSize, charSpacing, wordSpacing)
            if (advance > originalAdvance + 0.001f) {
                throw PdfEditRejected("新文字超出原有宽度，请缩短内容；此版本不自动压缩字号")
            }
            return COSArray().apply {
                add(COSString(bytes))
                // TJ moves backwards for positive values. Preserve the old text-matrix
                // advance so following Tj, Td and T* operations remain in place.
                add(COSFloat((advance - originalAdvance) * 1000f / fontSize))
            }
        }
    }

    private class RunReader(private val page: PDPage, private val pageIndex: Int) : PDFStreamEngine() {
        val runs = ArrayList<NativeRun>()
        var pageReason: String? = null
        private var ordinal = 0
        private var depth = 0
        private var capturing = false
        private val text = StringBuilder()
        private var left = Float.POSITIVE_INFINITY
        private var right = Float.NEGATIVE_INFINITY
        private var top = Float.NEGATIVE_INFINITY
        private var bottom = Float.POSITIVE_INFINITY
        private var runReason: String? = null

        init {
            listOf(BeginText(), EndText(), Concatenate(), Save(), Restore(), SetGraphicsStateParameters(),
                SetMatrix(), NextLine(), MoveText(), MoveTextSetLeading(), SetFontAndSize(),
                SetCharSpacing(), SetWordSpacing(), SetTextHorizontalScaling(), SetTextLeading(),
                SetTextRenderingMode(), SetTextRise(), ShowText(), ShowTextAdjusted(),
                ShowTextLine(), ShowTextLineAndSpace()).forEach { addOperator(it) }
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (depth > 0) { super.processOperator(operator, operands); return }
            val id = ordinal++
            val name = operator.name
            // No recursive Form traversal or text with clipping/alternate semantics in v1.
            if (name in setOf("W", "W*", "BI", "DP") ||
                (name == "BDC" && unsupportedMarkedContent(operands)) ||
                (name == "Tr" && ((operands.firstOrNull() as? COSNumber)?.intValue() ?: 0) >= 4) ||
                (name == "Do" && (operands.firstOrNull() as? COSName)?.let { resources.getXObject(it) } !is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject)) {
                pageReason = "此页含嵌套对象、裁剪或特殊文字结构，暂不支持编辑"
            }
            capturing = name == "Tj" || name == "TJ"
            text.clear(); left = Float.POSITIVE_INFINITY; right = Float.NEGATIVE_INFINITY
            top = Float.NEGATIVE_INFINITY; bottom = Float.POSITIVE_INFINITY; runReason = null
            val state = graphicsState.textState
            val font = state.font
            val size = state.fontSize
            val cs = state.characterSpacing
            val ws = state.wordSpacing
            val hs = state.horizontalScaling
            if (capturing && (font == null || font.isVertical || font is PDType3Font || size <= 0f || hs <= 0f)) {
                runReason = "这段文字的字体或方向暂不支持编辑"
            }
            if (capturing && state.renderingMode.intValue() != 0) runReason = "描边、隐形或裁剪文字暂不支持编辑"
            depth++
            try { super.processOperator(operator, operands) } finally { depth-- }
            if (capturing && text.isNotBlank() && font != null && left.isFinite()) {
                val crop = page.cropBox
                val bounds = listOf(left, bottom, right, top)
                if (bounds.any { !it.isFinite() } || left < crop.lowerLeftX || right > crop.upperRightX ||
                    bottom < crop.lowerLeftY || top > crop.upperRightY) runReason = "文字超出页面可见区域，暂不支持编辑"
                val view = PdfEditableText(PdfTextKey(pageIndex, id), text.toString(),
                    (left - crop.lowerLeftX) / crop.width, (crop.upperRightY - top) / crop.height,
                    (right - crop.lowerLeftX) / crop.width, (crop.upperRightY - bottom) / crop.height,
                    font.name ?: "原字体", runReason)
                val originalAdvance = when (val operand = operands.singleOrNull()) {
                    is COSString -> advance(font, operand.bytes, size, cs, ws)
                    is COSArray -> operand.sumOf { part ->
                        when (part) {
                            is COSString -> advance(font, part.bytes, size, cs, ws).toDouble()
                            is COSNumber -> (-part.floatValue() / 1000f * size).toDouble()
                            else -> throw PdfEditRejected("无效的文字间距结构")
                        }
                    }.toFloat()
                    else -> throw PdfEditRejected("无效的文字指令")
                }
                runs += NativeRun(view, font, size, cs, ws, originalAdvance)
            }
            capturing = false
        }

        private fun unsupportedMarkedContent(operands: List<COSBase>): Boolean {
            if ((operands.firstOrNull() as? COSName)?.name == "OC") return true
            val properties = when (val value = operands.getOrNull(1)) {
                is COSDictionary -> value
                is COSName -> (resources.cosObject.getDictionaryObject(COSName.PROPERTIES) as? COSDictionary)
                    ?.getDictionaryObject(value) as? COSDictionary
                else -> null
            } ?: return true
            // Ordinary tagged-document markers do not change drawing or replace extracted text.
            return properties.keySet().any { it.name !in setOf("MCID","Lang") }
        }

        override fun showGlyph(matrix: Matrix, font: PDFont, code: Int, displacement: Vector) {
            if (!capturing) return
            val unicode = font.toUnicode(code)
            if (unicode == null || unicode.length != 1 || !simpleCharacters(unicode)) {
                runReason = "这段文字的字符映射暂不支持准确编辑"
            }
            text.append(unicode ?: "�")
            if (abs(matrix.shearX) > 0.001f || abs(matrix.shearY) > 0.001f || matrix.scaleX <= 0 || matrix.scaleY <= 0) {
                runReason = "旋转或倾斜文字暂不支持编辑"
            }
            val ascent = (font.fontDescriptor?.ascent ?: 800f).takeIf { it > 0 } ?: 800f
            val descent = (font.fontDescriptor?.descent ?: -200f).takeIf { it < 0 } ?: -200f
            val start = matrix.transformPoint(0f, descent / 1000f)
            val end = matrix.transformPoint(displacement.x, ascent / 1000f)
            left = minOf(left, start.x); right = maxOf(right, end.x)
            bottom = minOf(bottom, start.y); top = maxOf(top, end.y)
        }

        override fun operatorException(operator: Operator, operands: MutableList<COSBase>, exception: IOException) {
            throw exception
        }
    }

    private fun simpleCharacters(text: String): Boolean = text.all { char ->
        !char.isISOControl() && !char.isSurrogate() && Character.getType(char) !in setOf(
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(), Character.FORMAT.toInt(),
        ) && Character.UnicodeScript.of(char.code) in setOf(
            Character.UnicodeScript.COMMON, Character.UnicodeScript.LATIN, Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.GREEK, Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL,
        )
    }

    private fun decode(font: PDFont, bytes: ByteArray): String = buildString {
        ByteArrayInputStream(bytes).use { input ->
            while (input.available() > 0) append(font.toUnicode(font.readCode(input)) ?: throw PdfEditRejected("字体缺少字符映射"))
        }
    }

    /** Text-space advance before horizontal scaling, identical for original and replacement. */
    private fun advance(font: PDFont, bytes: ByteArray, size: Float, cs: Float, ws: Float): Float {
        var width = 0f
        ByteArrayInputStream(bytes).use { input ->
            while (input.available() > 0) {
                val before = input.available()
                val code = font.readCode(input)
                width += font.getDisplacement(code).x * size + cs
                if (before - input.available() == 1 && code == 32) width += ws
            }
        }
        return width
    }
}
