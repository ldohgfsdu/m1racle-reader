package com.m1racle.reader.data.anchor

/**
 * 锚点的确定性文本编解码（存储形态）。零依赖、顺序固定、纯 JVM 可测。
 *
 * 格式（版本化前缀，未来演进以新增前缀实现）：
 * - `pdf:<page>|-|<x>,<y>,<w>,<h>`
 * - `md:<block>|-|<start>,<end>`
 *
 * 任何无法解析的输入 → null（恢复端永不抛异常）。
 */
object AnchorCodec {

    private const val PDF_PREFIX = "pdf:"
    private const val MD_PREFIX = "md:"

    fun encode(anchor: DocumentAnchor): String = when (anchor) {
        is DocumentAnchor.PdfPage -> buildString {
            append(PDF_PREFIX)
            append(anchor.page)
            append('|')
            val r = anchor.rect
            if (r == null) {
                append('-')
            } else {
                appendFloat(r.x); append(',')
                appendFloat(r.y); append(',')
                appendFloat(r.width); append(',')
                appendFloat(r.height)
            }
        }
        is DocumentAnchor.MarkdownBlock -> buildString {
            append(MD_PREFIX)
            append(anchor.blockIndex)
            append('|')
            val s = anchor.charStart
            val e = anchor.charEnd
            if (s == null || e == null) {
                append('-')
            } else {
                append(s); append(','); append(e)
            }
        }
    }

    fun decode(raw: String?): DocumentAnchor? {
        if (raw.isNullOrBlank()) return null
        return try {
            when {
                raw.startsWith(PDF_PREFIX) -> decodePdf(raw.removePrefix(PDF_PREFIX))
                raw.startsWith(MD_PREFIX) -> decodeMd(raw.removePrefix(MD_PREFIX))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePdf(body: String): DocumentAnchor? {
        val parts = body.split('|')
        if (parts.size != 2) return null
        val page = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val rect = if (parts[1] == "-") {
            null
        } else {
            val f = parts[1].split(',')
            if (f.size != 4) return null
            val x = f[0].toFloatOrNull() ?: return null
            val y = f[1].toFloatOrNull() ?: return null
            val w = f[2].toFloatOrNull() ?: return null
            val h = f[3].toFloatOrNull() ?: return null
            NormalizedRect(x, y, w, h).normalized()
        }
        return DocumentAnchor.PdfPage(page = page, rect = rect)
    }

    private fun decodeMd(body: String): DocumentAnchor? {
        val parts = body.split('|')
        if (parts.size != 2) return null
        val block = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return if (parts[1] == "-") {
            DocumentAnchor.MarkdownBlock(blockIndex = block)
        } else {
            val c = parts[1].split(',')
            if (c.size != 2) return null
            val s = c[0].toIntOrNull() ?: return null
            val e = c[1].toIntOrNull() ?: return null
            DocumentAnchor.MarkdownBlock(blockIndex = block, charStart = s, charEnd = e)
        }
    }

    /** 统一浮点输出（Float.toString 已是确定性的最短往返表示）。 */
    private fun StringBuilder.appendFloat(v: Float) {
        append(v.toString())
    }
}
