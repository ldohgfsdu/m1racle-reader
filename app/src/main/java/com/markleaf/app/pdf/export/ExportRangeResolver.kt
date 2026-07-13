package com.markleaf.app.pdf.export

import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.pdf.PrintPageResolver
import com.markleaf.app.pdf.TextbookOutlineResolver

enum class EndPageSource {
    /** 下一同级节点起始页 - 1 */
    NEXT_SIBLING,
    /** 父节点结束页 */
    PARENT_END,
    /** 祖先同级下一节点起始页 - 1 */
    ANCESTOR_NEXT_SIBLING,
    /** 文档最后一页 */
    DOCUMENT_END,
    /** 用户在导出确认中手工修改 */
    USER_OVERRIDE,
    ;

    fun labelZh(): String = when (this) {
        NEXT_SIBLING -> "下一同级节点起始页 - 1"
        PARENT_END -> "父节点结束页"
        ANCESTOR_NEXT_SIBLING -> "祖先同级下一节点起始页 - 1"
        DOCUMENT_END -> "PDF 最后一页"
        USER_OVERRIDE -> "用户手工修正"
    }
}

/**
 * Resolved export range for a textbook outline node (0-based inclusive).
 */
data class ExportRangePlan(
    val node: TextbookOutlineNodeEntity,
    val pathTitles: List<String>,
    /** Inclusive 0-based. */
    val startPageIndex: Int,
    /** Inclusive 0-based. */
    val endPageIndex: Int,
    val endSource: EndPageSource,
    val pageCount: Int,
    val printPageStart: Int?,
    val printPageEnd: Int?,
) {
    val pdfPageStart1Based: Int get() = startPageIndex + 1
    val pdfPageEnd1Based: Int get() = endPageIndex + 1
}

object ExportRangeResolver {

    fun siblingsOf(
        node: TextbookOutlineNodeEntity,
        nodes: List<TextbookOutlineNodeEntity>,
    ): List<TextbookOutlineNodeEntity> {
        return nodes
            .filter { it.parentId == node.parentId }
            .sortedWith(
                compareBy<TextbookOutlineNodeEntity> { it.sortOrder }
                    .thenBy { it.startPageIndex }
                    .thenBy { it.title },
            )
    }

    /**
     * Compute end page by outline sibling/parent rules (ignores stored end for source label,
     * then clamps so children do not exceed parent).
     */
    fun computeEndPage(
        node: TextbookOutlineNodeEntity,
        nodes: List<TextbookOutlineNodeEntity>,
        documentPageCount: Int,
    ): Pair<Int, EndPageSource> {
        if (documentPageCount <= 0) return 0 to EndPageSource.DOCUMENT_END
        val last = documentPageCount - 1
        val start = node.startPageIndex.coerceIn(0, last)

        val siblings = siblingsOf(node, nodes)
        val idx = siblings.indexOfFirst { it.id == node.id }
        if (idx >= 0 && idx < siblings.lastIndex) {
            val next = siblings[idx + 1]
            val end = (next.startPageIndex - 1).coerceAtLeast(start)
            return end.coerceAtMost(last) to EndPageSource.NEXT_SIBLING
        }

        // Climb ancestors for next-sibling boundary
        val byId = nodes.associateBy { it.id }
        var parent = node.parentId?.let { byId[it] }
        while (parent != null) {
            val pSiblings = siblingsOf(parent, nodes)
            val pIdx = pSiblings.indexOfFirst { it.id == parent!!.id }
            if (pIdx >= 0 && pIdx < pSiblings.lastIndex) {
                val next = pSiblings[pIdx + 1]
                val end = (next.startPageIndex - 1).coerceAtLeast(start)
                return end.coerceAtMost(last) to EndPageSource.ANCESTOR_NEXT_SIBLING
            }
            // Use this parent's end, but keep climbing only if we need better bound
            parent = parent.parentId?.let { byId[it] }
        }

        // Direct parent end (if any)
        val directParent = node.parentId?.let { byId[it] }
        if (directParent != null) {
            val end = directParent.endPageIndex.coerceAtLeast(start).coerceAtMost(last)
            return end to EndPageSource.PARENT_END
        }

        return last.coerceAtLeast(start) to EndPageSource.DOCUMENT_END
    }

    /**
     * Build export plan; optionally override start/end (0-based).
     * Child ranges are clamped so they never exceed parent bounds when parent exists.
     */
    fun plan(
        node: TextbookOutlineNodeEntity,
        nodes: List<TextbookOutlineNodeEntity>,
        documentPageCount: Int,
        printAnchors: List<PdfPrintPageAnchorEntity> = emptyList(),
        overrideStartIndex: Int? = null,
        overrideEndIndex: Int? = null,
        endSourceOverride: EndPageSource? = null,
    ): ExportRangePlan {
        val last = (documentPageCount - 1).coerceAtLeast(0)
        val path = TextbookOutlineResolver.pathToRoot(node, nodes).map { it.title }

        val (computedEnd, computedSource) = computeEndPage(node, nodes, documentPageCount)
        var start = (overrideStartIndex ?: node.startPageIndex).coerceIn(0, last)
        var end = (overrideEndIndex ?: computedEnd).coerceIn(0, last)
        if (start > end) {
            val t = start
            start = end
            end = t
        }

        // Clamp to parent range if parent exists
        val parent = node.parentId?.let { pid -> nodes.find { it.id == pid } }
        if (parent != null) {
            val pStart = parent.startPageIndex.coerceIn(0, last)
            val pEnd = parent.endPageIndex.coerceIn(0, last)
            start = start.coerceIn(pStart, pEnd)
            end = end.coerceIn(pStart, pEnd)
            if (start > end) {
                start = pStart
                end = pStart
            }
        }

        val source = when {
            endSourceOverride != null -> endSourceOverride
            overrideEndIndex != null && overrideEndIndex != computedEnd -> EndPageSource.USER_OVERRIDE
            else -> computedSource
        }

        val printStart = PrintPageResolver.resolve(start, printAnchors)
        val printEnd = PrintPageResolver.resolve(end, printAnchors)

        return ExportRangePlan(
            node = node,
            pathTitles = path,
            startPageIndex = start,
            endPageIndex = end,
            endSource = source,
            pageCount = end - start + 1,
            printPageStart = printStart,
            printPageEnd = printEnd,
        )
    }

    fun validateRange(
        startIndex0: Int,
        endIndex0: Int,
        documentPageCount: Int,
    ): String? {
        if (documentPageCount <= 0) return "文档无有效页"
        if (startIndex0 < 0 || endIndex0 < 0) return "页码不能为负"
        if (startIndex0 >= documentPageCount || endIndex0 >= documentPageCount) {
            return "页码超出文档范围 (1–$documentPageCount)"
        }
        if (startIndex0 > endIndex0) return "起始页不能大于结束页"
        return null
    }

    /** Sanitize for filesystem / SAF display names. */
    fun sanitizeFileName(raw: String, maxLen: Int = 80): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .trim('_', '.', ' ')
        val base = cleaned.ifBlank { "chapter" }
        return if (base.length <= maxLen) base else base.take(maxLen)
    }

    fun defaultZipName(documentDisplayName: String, pathTitles: List<String>): String {
        val docBase = documentDisplayName
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .let { sanitizeFileName(it, 40) }
        val pathPart = pathTitles
            .takeLast(2)
            .joinToString("_") { sanitizeFileName(it, 30) }
            .ifBlank { "export" }
        return sanitizeFileName("${docBase}_$pathPart", 100) + ".zip"
    }
}
