package com.m1racle.reader.data.anchor

/**
 * 锚点的确定性文本编解码（存储形态）。零依赖、顺序固定、纯 JVM 可测。
 *
 * 格式（矩形列表使用分号分隔，已有单矩形是长度为 1 的列表）：
 * - `pdf:<page>|-` 或 `pdf:<page>|<x>,<y>,<w>,<h>[;<x>,<y>,<w>,<h>...]`
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
            if (anchor.rects.isEmpty()) append('-')
            else append(anchor.rects.joinToString(";") { r ->
                "${r.x},${r.y},${r.width},${r.height}"
            })
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
        val rects = if (parts[1] == "-") emptyList() else parts[1].split(';').map { encoded ->
            val f = encoded.split(',').map { it.toFloatOrNull() ?: return null }
            if (f.size != 4 || f.any { !it.isFinite() }) return null
            NormalizedRect(f[0], f[1], f[2], f[3]).normalized()
        }
        return DocumentAnchor.PdfPage(page = page, rects = rects)
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

}
