package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.DocumentEntity
import java.net.URLDecoder
import org.json.JSONObject

/** One-time data conversion on opening books saved by versions 45–50. No SDK runtime remains. */
internal fun rollbackEpubPosition(book: EpubBook, document: DocumentEntity): DocumentEntity {
    val stored = document.readingAnchor ?: return document
    val prefix = "epub-locator:"
    if (!stored.startsWith(prefix)) return document
    val json = if (stored.length <= 64 * 1024) runCatching {
        JSONObject(stored.removePrefix(prefix))
    }.getOrNull() else null
    val href = json?.optString("href")?.substringBefore('#')?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }
    val locations = json?.optJSONObject("locations")
    val progress = locations?.optDouble("totalProgression", Double.NaN)
        ?.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 1f) ?: document.readingProgress
    val anchor = href?.let {
        // SDK locators use paths from the EPUB root; native spine hrefs are
        // relative to the OPF directory. A unique complete suffix identifies
        // the same chapter without guessing when multiple paths match.
        val chapter = book.readingOrder.firstOrNull { chapter -> chapter.href == href }
            ?: book.readingOrder.filter { chapter -> href.endsWith("/" + chapter.href) }.singleOrNull()
        val nativeHref = chapter?.href ?: href
        val fraction = locations?.optDouble("progression", 0.0)
            ?.takeIf { value -> value.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val hint = ((chapter?.blocks?.lastIndex ?: 0) * fraction).toInt().coerceAtLeast(0)
        val quote = epubQuoteAt(json?.optJSONObject("text")?.optString("highlight").orEmpty(), 0)
        val source = DocumentAnchor.EpubText(nativeHref, hint, 0, parserVersion = 0, quote = quote)
        val restored = EpubReadingAnchors.resolve(book, source)
        // Unresolved anchors retain the existing fallback notice and save protection.
        if (restored?.tier == EpubRestoreTier.Quote || restored?.tier == EpubRestoreTier.QuotePrefix) {
            restored.location?.let { location -> EpubReadingAnchors.anchorAt(book, location) } ?: source
        } else source
    }
    return document.copy(readingAnchor = anchor?.let(AnchorCodec::encode),
        lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = progress)
}
