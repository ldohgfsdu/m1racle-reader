package com.m1racle.reader.epub

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.DocumentEntity

/** Characters of normalized source text stored with an anchor for relocation. */
const val EPUB_QUOTE_CHARS = 32

/** Leading quote characters used by the second text matching round. */
const val EPUB_QUOTE_VERIFY_CHARS = 12

/** Raw characters scanned to build a quote; whitespace runs collapse, so allow slack. */
private const val QUOTE_SCAN_CHARS = EPUB_QUOTE_CHARS * 8

/** A character of the book in parser coordinates: spine item, in-chapter block, in-block offset. */
data class EpubLocation(val spineIndex: Int, val blockIndex: Int, val charOffset: Int)

/** The stored EPUB reading fields of a document. The anchor is the only restore authority. */
data class EpubSavedPosition(
    val anchor: DocumentAnchor.EpubText?,
    val lastScrollIndex: Int,
    val lastScrollOffset: Int,
    val readingProgress: Float,
) {
    companion object {
        val Start = EpubSavedPosition(null, 0, 0, 0f)

        fun of(document: DocumentEntity) = EpubSavedPosition(
            anchor = AnchorCodec.decode(document.readingAnchor) as? DocumentAnchor.EpubText,
            lastScrollIndex = document.lastScrollIndex,
            lastScrollOffset = document.lastScrollOffset,
            readingProgress = document.readingProgress,
        )
    }
}

/** Which restore tier produced the position. */
enum class EpubRestoreTier {
    /** Same block rules, block and offset in range, source text still matches the quote. */
    Exact,

    /** Block number untrusted; the quote was found in the same chapter (nearest occurrence). */
    Quote,

    /** Full quote missing; its first 12 characters were found in the same chapter. */
    QuotePrefix,

    /** Neither held: degraded to the chapter start (logged), never the book start. */
    ChapterStart,

    /** Anchored chapter missing (book replaced, or not in the reading order): saved percentage. */
    Progress,

    /** Pre-anchor record: legacy flat row + pixel offset, converted once by the reader view. */
    LegacyPosition,

    /** Nothing saved yet. */
    BookStart,
}

/** Result of [EpubReadingAnchors.restore]; exactly the field matching [tier] is set. */
data class EpubRestoreResult(
    val tier: EpubRestoreTier,
    /** Set for text matches and chapter-start fallback. */
    val location: EpubLocation? = null,
    /** Set for [EpubRestoreTier.Progress]: 0f..1f of the whole reading order. */
    val progress: Float? = null,
    /** Set for [EpubRestoreTier.LegacyPosition]: legacy flat block row and pixel offset in it. */
    val legacyRow: Int? = null,
    val legacyPixelOffset: Int? = null,
) {
    /** Protect the original anchor until the user navigates after a non-exact restore. */
    val degraded: Boolean
        get() = tier == EpubRestoreTier.Quote || tier == EpubRestoreTier.QuotePrefix || needsDegradedNotice

    /** Only the chapter/progress fallback needs a visible notice; prefix matches stay silent. */
    val needsDegradedNotice: Boolean
        get() = tier == EpubRestoreTier.ChapterStart || tier == EpubRestoreTier.Progress
}

/**
 * Layout-independent EPUB reading anchors on the parsed [EpubBook] alone: no
 * pager, Compose or measured page is involved, so paged and scrolling readers
 * share it. Offsets count characters of [readingText].
 *
 * Only chapters of [EpubBook.readingOrder] can be shown, so an anchor whose href
 * names a non-linear spine item is treated like a missing chapter.
 */
object EpubReadingAnchors {

    /** Anchor for [location]; null when the chapter or block does not exist. */
    fun anchorAt(book: EpubBook, location: EpubLocation): DocumentAnchor.EpubText? {
        val chapter = book.chapters.firstOrNull { it.spineIndex == location.spineIndex } ?: return null
        val block = chapter.blocks.getOrNull(location.blockIndex) ?: return null
        val text = block.readingText()
        val start = location.charOffset.coerceIn(0, text.length)
        return DocumentAnchor.EpubText(
            spineHref = chapter.href,
            blockIndex = location.blockIndex,
            charStart = start,
            parserVersion = EpubXhtmlParser.BLOCK_RULES_VERSION,
            quote = epubQuoteAt(text, start),
        )
    }

    /**
     * Restore order: exact anchor -> nearest full quote -> nearest 12-char prefix -> chapter start
     * -> saved progress when the chapter is gone. A record without an anchor is a
     * pre-anchor record (legacy row/pixel) unless it is at the very start.
     */
    fun restore(book: EpubBook, saved: EpubSavedPosition): EpubRestoreResult {
        val anchor = saved.anchor
        if (anchor != null) {
            return resolve(book, anchor)
                ?: EpubRestoreResult(EpubRestoreTier.Progress, progress = saved.readingProgress.coerceIn(0f, 1f))
        }
        if (saved.lastScrollIndex > 0 || saved.lastScrollOffset > 0) {
            return EpubRestoreResult(
                EpubRestoreTier.LegacyPosition,
                legacyRow = saved.lastScrollIndex,
                legacyPixelOffset = saved.lastScrollOffset,
            )
        }
        return EpubRestoreResult(EpubRestoreTier.BookStart)
    }

    /** Convenience for a stored document row. */
    fun restore(book: EpubBook, document: DocumentEntity): EpubRestoreResult =
        restore(book, EpubSavedPosition.of(document))

    /** Text match or ChapterStart; null only when the anchored chapter is not readable. */
    fun resolve(book: EpubBook, anchor: DocumentAnchor.EpubText): EpubRestoreResult? {
        val chapter = book.readingOrder.firstOrNull { it.href == anchor.spineHref } ?: return null
        val block = chapter.blocks.getOrNull(anchor.blockIndex)
        if (block != null && anchor.parserVersion == EpubXhtmlParser.BLOCK_RULES_VERSION) {
            val text = block.readingText()
            if (anchor.charStart <= text.length &&
                epubQuoteAt(text, anchor.charStart) == anchor.quote
            ) {
                return EpubRestoreResult(
                    EpubRestoreTier.Exact,
                    location = EpubLocation(chapter.spineIndex, anchor.blockIndex, anchor.charStart),
                )
            }
        }
        findQuote(chapter, anchor, anchor.quote)?.let { (blockIndex, offset) ->
            return EpubRestoreResult(EpubRestoreTier.Quote, location = EpubLocation(chapter.spineIndex, blockIndex, offset))
        }
        findQuote(chapter, anchor, anchor.quote.take(EPUB_QUOTE_VERIFY_CHARS))?.let { (blockIndex, offset) ->
            return EpubRestoreResult(EpubRestoreTier.QuotePrefix, location = EpubLocation(chapter.spineIndex, blockIndex, offset))
        }
        return EpubRestoreResult(EpubRestoreTier.ChapterStart, location = EpubLocation(chapter.spineIndex, 0, 0))
    }

    private fun findQuote(chapter: EpubChapter, anchor: DocumentAnchor.EpubText, quote: String): Pair<Int, Int>? {
        if (quote.isBlank()) return null
        var best: Pair<Int, Int>? = null
        var bestScore = Long.MAX_VALUE
        chapter.blocks.forEachIndexed { blockIndex, block ->
            val (normalized, rawIndex) = normalizeWithMap(block.readingText())
            var at = normalized.indexOf(quote)
            while (at >= 0) {
                val offset = rawIndex[at]
                val blockDistance = kotlin.math.abs(blockIndex - anchor.blockIndex).toLong()
                val charDistance = kotlin.math.abs(offset - anchor.charStart).toLong()
                val score = (blockDistance shl 32) + charDistance
                if (score < bestScore) {
                    bestScore = score
                    best = blockIndex to offset
                }
                at = normalized.indexOf(quote, at + 1)
            }
        }
        return best
    }
}

/** Normalized source text from [start]: whitespace runs collapse, leading space dropped. */
fun epubQuoteAt(text: String, start: Int): String {
    val from = start.coerceIn(0, text.length)
    val window = text.substring(from, minOf(text.length, from + QUOTE_SCAN_CHARS))
    var quote = normalizeWithMap(window).first.trimStart().take(EPUB_QUOTE_CHARS)
    // Never store half of a surrogate pair; it cannot survive UTF-8 encoding.
    if (quote.isNotEmpty() && quote.last().isHighSurrogate()) quote = quote.dropLast(1)
    return quote
}

/** Collapse whitespace runs to one space, with the raw index of every normalized char. */
private fun normalizeWithMap(raw: String): Pair<String, IntArray> {
    val out = StringBuilder(raw.length)
    val map = IntArray(raw.length)
    var previousSpace = false
    raw.forEachIndexed { i, c ->
        if (c.isWhitespace()) {
            if (!previousSpace) {
                map[out.length] = i
                out.append(' ')
            }
            previousSpace = true
        } else {
            map[out.length] = i
            out.append(c)
            previousSpace = false
        }
    }
    return out.toString() to map
}
