package com.markleaf.app.pdf

import com.markleaf.app.data.local.OutlineNodeSource
import com.markleaf.app.data.local.TextbookOutlineNodeType

/**
 * One entry from a PDF document outline (bookmarks), before Room persistence.
 */
data class EmbeddedOutlineEntry(
    val title: String,
    /** 0-based PDF page index. */
    val pageIndex: Int,
    val depth: Int,
    /** Document-order among all entries (DFS). */
    val order: Int,
    /** Parent entry order, or -1 for root. */
    val parentOrder: Int,
    val childrenOrders: List<Int> = emptyList(),
)

/**
 * After end-page inference.
 */
data class EmbeddedOutlineNodeDraft(
    val title: String,
    val pageIndex: Int,
    val endPageIndex: Int,
    val depth: Int,
    val order: Int,
    val parentOrder: Int,
    val type: TextbookOutlineNodeType,
    /** Stable idempotent key for this document. */
    val importKey: String,
)

enum class OutlineImportMode {
    REPLACE,
    MERGE,
}

object EmbeddedOutlineKeys {
    fun importKey(
        depth: Int,
        order: Int,
        parentOrder: Int,
        title: String,
        pageIndex: Int,
    ): String {
        val t = title.trim().replace('|', '/')
        return "PDF_EMBEDDED|$depth|$order|$parentOrder|$pageIndex|$t"
    }
}

object EmbeddedOutlineTypeMapper {
    /**
     * Map bookmark depth + title hints to textbook node type.
     * Manual edits can still change type later.
     */
    fun map(depth: Int, title: String): TextbookOutlineNodeType {
        val t = title.trim()
        if (t.contains("附录") || t.startsWith("Appendix", ignoreCase = true)) {
            return TextbookOutlineNodeType.APPENDIX
        }
        if (t.contains("篇") && depth <= 1) {
            return TextbookOutlineNodeType.PART
        }
        return when (depth) {
            0 -> if (t.contains("章")) TextbookOutlineNodeType.CHAPTER else TextbookOutlineNodeType.PART
            1 -> TextbookOutlineNodeType.CHAPTER
            2 -> TextbookOutlineNodeType.SECTION
            3 -> TextbookOutlineNodeType.SUBSECTION
            else -> TextbookOutlineNodeType.ITEM
        }
    }
}

/**
 * Pure transforms for embedded outline → drafts (unit-testable without PDFBox).
 */
object EmbeddedOutlineBuilder {

    /**
     * Infer inclusive [endPageIndex] from next sibling start - 1, else parent end, else doc end.
     * Entries must be in DFS document order.
     */
    fun inferEndPages(
        entries: List<EmbeddedOutlineEntry>,
        pageCount: Int,
    ): List<EmbeddedOutlineNodeDraft> {
        if (entries.isEmpty() || pageCount <= 0) return emptyList()
        val lastPage = pageCount - 1
        val byParent = entries.groupBy { it.parentOrder }
        val byOrder = entries.associateBy { it.order }

        fun siblingsOf(parentOrder: Int): List<EmbeddedOutlineEntry> =
            byParent[parentOrder].orEmpty().sortedBy { it.order }

        fun endFor(e: EmbeddedOutlineEntry): Int {
            val sibs = siblingsOf(e.parentOrder)
            val idx = sibs.indexOfFirst { it.order == e.order }
            val nextSib = if (idx >= 0 && idx < sibs.lastIndex) sibs[idx + 1] else null
            if (nextSib != null) {
                return (nextSib.pageIndex - 1).coerceAtLeast(e.pageIndex)
            }
            // Climb to parent boundary
            var pOrder = e.parentOrder
            while (pOrder >= 0) {
                val parent = byOrder[pOrder] ?: break
                val pSibs = siblingsOf(parent.parentOrder)
                val pIdx = pSibs.indexOfFirst { it.order == parent.order }
                val parentNext = if (pIdx >= 0 && pIdx < pSibs.lastIndex) pSibs[pIdx + 1] else null
                if (parentNext != null) {
                    return (parentNext.pageIndex - 1).coerceAtLeast(e.pageIndex)
                }
                pOrder = parent.parentOrder
            }
            return lastPage.coerceAtLeast(e.pageIndex)
        }

        return entries.map { e ->
            val end = endFor(e).coerceIn(e.pageIndex, lastPage)
            EmbeddedOutlineNodeDraft(
                title = e.title.ifBlank { "未命名" },
                pageIndex = e.pageIndex.coerceIn(0, lastPage),
                endPageIndex = end,
                depth = e.depth,
                order = e.order,
                parentOrder = e.parentOrder,
                type = EmbeddedOutlineTypeMapper.map(e.depth, e.title),
                importKey = EmbeddedOutlineKeys.importKey(
                    depth = e.depth,
                    order = e.order,
                    parentOrder = e.parentOrder,
                    title = e.title,
                    pageIndex = e.pageIndex.coerceIn(0, lastPage),
                ),
            )
        }
    }
}

data class OutlineImportPlan(
    val drafts: List<EmbeddedOutlineNodeDraft>,
    val mode: OutlineImportMode,
    val source: OutlineNodeSource = OutlineNodeSource.PDF_EMBEDDED,
)
