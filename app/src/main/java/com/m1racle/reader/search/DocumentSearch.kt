package com.m1racle.reader.search

import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.blockId
import java.util.Locale

/**
 * In-document search over **visible** block text (R9 P0).
 * No index, no schema, no Compose.
 *
 * Matching: non-overlapping case-insensitive substring. All [SearchMatch]
 * offsets are UTF-16 indices into the **original** [String] of each segment
 * (same space as `substring`/Compose); matches found in folded space are
 * mapped back via [buildFoldOriginMap].
 * Cross-block matches are intentionally unsupported.
 */
object DocumentSearch {

    /**
     * Soft cap against pathological documents (tiny repeated needles).
     * When exceeded, [SearchResult.truncated] is true and further hits are dropped.
     */
    const val MAX_MATCHES: Int = 2_000

    data class SearchResult(
        val matches: List<SearchMatch>,
        val truncated: Boolean = false,
        val documentKey: String = "",
    ) {
        companion object {
            val Empty = SearchResult(emptyList())
        }
    }

    fun search(
        documentKey: String,
        blocks: List<MarkdownBlock>,
        rawQuery: String,
        blockIds: List<String> = emptyList(),
    ): SearchResult {
        // Empty query → no scan (caller keeps typed query in UI).
        if (rawQuery.isEmpty() || blocks.isEmpty() || documentKey.isEmpty()) {
            return SearchResult(emptyList(), truncated = false, documentKey = documentKey)
        }

        val segments = MarkdownSearchTextExtractor.extract(documentKey, blocks, blockIds)
        if (segments.isEmpty()) {
            return SearchResult(emptyList(), truncated = false, documentKey = documentKey)
        }

        // Preserve query characters including spaces; only fold case for comparison.
        val needle = foldCase(rawQuery)
        if (needle.isEmpty()) {
            return SearchResult(emptyList(), truncated = false, documentKey = documentKey)
        }

        val matches = ArrayList<SearchMatch>(minOf(64, segments.size))
        var truncated = false

        for (seg in segments) {
            if (matches.size >= MAX_MATCHES) {
                truncated = true
                break
            }
            val remaining = MAX_MATCHES - matches.size
            val added = collectMatches(seg, rawQuery, needle, remaining)
            matches.addAll(added)
            if (added.size >= remaining && hasMoreMatches(seg.visibleText, needle, added)) {
                truncated = true
                break
            }
        }

        return SearchResult(
            matches = matches,
            truncated = truncated,
            documentKey = documentKey,
        )
    }

    /**
     * Convenience for tests that only have blocks (uses synthetic document key).
     */
    fun search(blocks: List<MarkdownBlock>, rawQuery: String): List<SearchMatch> =
        search(
            documentKey = "test",
            blocks = blocks,
            rawQuery = rawQuery,
            blockIds = blocks.mapIndexed { i, b -> b.blockId(i) },
        ).matches

    private fun collectMatches(
        seg: MarkdownSearchTextExtractor.SearchableSegment,
        rawQuery: String,
        needle: String,
        limit: Int,
    ): List<SearchMatch> {
        val text = seg.visibleText
        if (text.isEmpty() || limit <= 0) return emptyList()
        val haystack = foldCase(text)
        if (haystack.length < needle.length) return emptyList()
        val origin = buildFoldOriginMap(text, haystack)

        val out = ArrayList<SearchMatch>(4)
        var from = 0
        while (from <= haystack.length - needle.length && out.size < limit) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) break
            // Folded-space match -> original-text UTF-16 range: start = origin of
            // the first folded unit; end = end of the code point that produced the
            // last folded unit (covers the whole expanded code point, never more).
            val start = origin[idx]
            val lastOrigin = origin[idx + needle.length - 1]
            val end = lastOrigin + Character.charCount(text.codePointAt(lastOrigin))
            val matched = text.substring(start, end)
            out.add(
                SearchMatch(
                    documentKey = seg.documentKey,
                    blockId = seg.blockId,
                    blockIndex = seg.blockIndex,
                    matchStart = start,
                    matchEnd = end,
                    matchedText = matched,
                    preview = previewAround(text, start, end),
                    listItemIndex = seg.listItemIndex,
                ),
            )
            // Non-overlapping in folded space: advance by needle length (at least 1).
            from = idx + needle.length.coerceAtLeast(1)
        }
        return out
    }

    /**
     * For every UTF-16 unit of [folded], the index in [text] (UTF-16, start of a
     * code point) that produced it. Monotone: folded units never move backwards
     * in the original text.
     *
     * Built per code point: ROOT lowercase expands at most one input code point
     * (U+0130 `İ` -> `i` + U+0307, 1 -> 2 units); every other lowercase mapping
     * is length-preserving, so the fold length is additive over code points.
     * That invariant is machine-checked by the [require] below and pinned by
     * tests (fold-length additivity over a corpus).
     */
    private fun buildFoldOriginMap(text: String, folded: String): IntArray {
        val origin = IntArray(folded.length)
        var foldedCursor = 0
        var textCursor = 0
        while (textCursor < text.length) {
            val cpLength = Character.charCount(text.codePointAt(textCursor))
            val foldedCpLength =
                foldCase(text.substring(textCursor, textCursor + cpLength)).length
            for (i in 0 until foldedCpLength) {
                origin[foldedCursor + i] = textCursor
            }
            foldedCursor += foldedCpLength
            textCursor += cpLength
        }
        require(foldedCursor == folded.length) {
            "fold length not additive over code points: text=$textCursor folded=${folded.length} mapped=$foldedCursor"
        }
        return origin
    }

    private fun hasMoreMatches(
        text: String,
        needle: String,
        already: List<SearchMatch>,
    ): Boolean {
        if (already.isEmpty()) return false
        val haystack = foldCase(text)
        // matchEnd is an ORIGINAL-text offset; resume in folded space from the
        // folded length of the original prefix up to that offset.
        val lastHaystackEnd = foldCase(text.substring(0, already.last().matchEnd)).length
        if (lastHaystackEnd > haystack.length - needle.length) return false
        return haystack.indexOf(needle, lastHaystackEnd) >= 0
    }

    /** Locale-stable case fold for English; Chinese/emoji unaffected. */
    fun foldCase(s: String): String = s.lowercase(Locale.ROOT)

    private fun previewAround(text: String, start: Int, end: Int, radius: Int = 24): String {
        val from = (start - radius).coerceAtLeast(0)
        val to = (end + radius).coerceAtMost(text.length)
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        return prefix + text.substring(from, to).replace('\n', ' ') + suffix
    }
}
