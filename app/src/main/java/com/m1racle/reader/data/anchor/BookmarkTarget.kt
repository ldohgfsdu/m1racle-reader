package com.m1racle.reader.data.anchor

import com.m1racle.reader.pdf.PdfReadingMode
import com.m1racle.reader.pdf.PdfViewportSessionState
import org.json.JSONObject

/** A bookmark owns its captured position; later reading progress cannot move it. */
data class BookmarkTarget(
    val anchor: DocumentAnchor,
    val scrollOffset: Int = 0,
    val viewport: PdfViewportSessionState? = null,
) {
    fun applyTo(document: com.m1racle.reader.data.local.DocumentEntity): com.m1racle.reader.data.local.DocumentEntity {
        val page = (anchor as? DocumentAnchor.PdfPage)?.page ?: return document
        val saved = viewport
        return document.copy(
            lastScrollIndex = page,
            lastScrollOffset = com.m1racle.reader.pdf.PdfReadingPositionCodec.encodeFraction(saved?.pageAnchorYFraction ?: 0f),
            pdfViewportSessionVersion = saved?.version,
            pdfViewportReadingMode = saved?.readingMode?.name,
            pdfViewportPageIndex = saved?.pageIndex,
            pdfPageAnchorXFraction = saved?.pageAnchorXFraction,
            pdfPageAnchorYFraction = saved?.pageAnchorYFraction,
            pdfViewportAnchorXFraction = saved?.viewportAnchorXFraction,
            pdfViewportAnchorYFraction = saved?.viewportAnchorYFraction,
            pdfViewportScale = saved?.scale,
            pdfViewportGeneration = saved?.generation,
        )
    }

    fun encode(): String = JSONObject().apply {
        put("anchor", AnchorCodec.encode(anchor))
        put("offset", scrollOffset.coerceAtLeast(0))
        viewport?.let { s -> put("viewport", JSONObject().apply {
            put("mode", s.readingMode.name); put("page", s.pageIndex)
            put("x", s.pageAnchorXFraction); put("y", s.pageAnchorYFraction)
            put("vx", s.viewportAnchorXFraction); put("vy", s.viewportAnchorYFraction)
            put("scale", s.scale)
        }) }
    }.toString()

    companion object {
        fun decode(raw: String?): BookmarkTarget? = runCatching {
            if (raw.isNullOrBlank()) return null
            // Preserve existing canonical bookmarks created before snapshots were available.
            if (!raw.startsWith("{")) return AnchorCodec.decode(raw)?.let(::BookmarkTarget)
            val json = JSONObject(raw)
            val anchor = AnchorCodec.decode(json.getString("anchor")) ?: return null
            val viewport = json.optJSONObject("viewport")?.let { v ->
                PdfViewportSessionState(
                    readingMode = PdfReadingMode.valueOf(v.getString("mode")),
                    pageIndex = v.getInt("page").coerceAtLeast(0),
                    pageAnchorXFraction = v.getDouble("x").toFloat(),
                    pageAnchorYFraction = v.getDouble("y").toFloat(),
                    viewportAnchorXFraction = v.getDouble("vx").toFloat(),
                    viewportAnchorYFraction = v.getDouble("vy").toFloat(),
                    scale = v.getDouble("scale").toFloat(), generation = 0,
                ).also { require(it.scale.isFinite() && it.scale > 0) }
            }
            BookmarkTarget(anchor, json.optInt("offset").coerceAtLeast(0), viewport)
        }.getOrNull()
    }
}
