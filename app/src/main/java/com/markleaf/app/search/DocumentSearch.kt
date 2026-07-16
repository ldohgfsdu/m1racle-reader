package com.markleaf.app.search

import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.blockId
import java.util.Locale

/**
 * In-document search over **visible** block text (R9 P0).
 * No index, no schema, no Compose.
 *
 * Matching: non-overlapping case-insensitive substring on UTF-16 [String] indices.
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

        val out = ArrayList<SearchMatch>(4)
        var from = 0
        while (from <= haystack.length - needle.length && out.size < limit) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) break
            val end = idx + needle.length
            val matched = text.substring(idx, end)
            out.add(
                SearchMatch(
                    documentKey = seg.documentKey,
                    blockId = seg.blockId,
                    blockIndex = seg.blockIndex,
                    matchStart = idx,
                    matchEnd = end,
                    matchedText = matched,
                    preview = previewAround(text, idx, end),
                    listItemIndex = seg.listItemIndex,
                ),
            )
            // Non-overlapping: advance by match length (at least 1).
            from = idx + needle.length.coerceAtLeast(1)
        }
        return out
    }

    private fun hasMoreMatches(
        text: String,
        needle: String,
        already: List<SearchMatch>,
    ): Boolean {
        if (already.isEmpty()) return false
        val haystack = foldCase(text)
        val lastEnd = already.last().matchEnd
        if (lastEnd > haystack.length - needle.length) return false
        return haystack.indexOf(needle, lastEnd) >= 0
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
