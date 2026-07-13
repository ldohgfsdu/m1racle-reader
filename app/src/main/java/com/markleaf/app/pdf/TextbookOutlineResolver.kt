package com.markleaf.app.pdf

import com.markleaf.app.data.local.TextbookOutlineNodeEntity

/**
 * Pure helpers for textbook outline trees (not knowledge-point trees).
 */
object TextbookOutlineResolver {

    data class FlatRow(
        val node: TextbookOutlineNodeEntity,
        val depth: Int,
        val pathTitles: List<String>,
    )

    /** Build depth-first flat list for UI. */
    fun flattenTree(nodes: List<TextbookOutlineNodeEntity>): List<FlatRow> {
        if (nodes.isEmpty()) return emptyList()
        val byParent = nodes.groupBy { it.parentId }
        val byId = nodes.associateBy { it.id }
        val out = ArrayList<FlatRow>(nodes.size)

        fun walk(parentId: String?, depth: Int, path: List<String>) {
            val children = (byParent[parentId].orEmpty())
                .sortedWith(
                    compareBy<TextbookOutlineNodeEntity> { it.sortOrder }
                        .thenBy { it.startPageIndex }
                        .thenBy { it.title },
                )
            for (child in children) {
                // Skip orphan cycles: parent missing and not root
                if (child.parentId != null && byId[child.parentId] == null) continue
                val nextPath = path + child.title
                out.add(FlatRow(child, depth, nextPath))
                walk(child.id, depth + 1, nextPath)
            }
        }
        walk(null, 0, emptyList())
        return out
    }

    /**
     * Deepest node containing [pageIndex] (max depth; ties → later start, longer range).
     */
    fun deepestContaining(
        pageIndex: Int,
        nodes: List<TextbookOutlineNodeEntity>,
    ): TextbookOutlineNodeEntity? {
        if (nodes.isEmpty() || pageIndex < 0) return null
        val containing = nodes.filter { it.containsPage(pageIndex) }
        if (containing.isEmpty()) return null
        val depthOf = depthMap(nodes)
        return containing.maxWithOrNull(
            compareBy<TextbookOutlineNodeEntity> { depthOf[it.id] ?: 0 }
                .thenBy { it.startPageIndex }
                .thenByDescending { it.endPageIndex - it.startPageIndex },
        )
    }

    fun pathToRoot(
        node: TextbookOutlineNodeEntity?,
        nodes: List<TextbookOutlineNodeEntity>,
    ): List<TextbookOutlineNodeEntity> {
        if (node == null) return emptyList()
        val byId = nodes.associateBy { it.id }
        val path = ArrayList<TextbookOutlineNodeEntity>()
        var cur: TextbookOutlineNodeEntity? = node
        val guard = HashSet<String>()
        while (cur != null && guard.add(cur.id)) {
            path.add(cur)
            cur = cur.parentId?.let { byId[it] }
        }
        return path.asReversed()
    }

    fun pathLabel(path: List<TextbookOutlineNodeEntity>, separator: String = " / "): String {
        if (path.isEmpty()) return ""
        return path.joinToString(separator) { it.title }
    }

    fun depthMap(nodes: List<TextbookOutlineNodeEntity>): Map<String, Int> {
        val byId = nodes.associateBy { it.id }
        val memo = HashMap<String, Int>()
        fun depth(id: String): Int {
            memo[id]?.let { return it }
            val n = byId[id] ?: return 0
            val d = if (n.parentId == null) 0 else depth(n.parentId) + 1
            memo[id] = d
            return d
        }
        nodes.forEach { depth(it.id) }
        return memo
    }

    /** Collect id + all descendant ids. */
    fun subtreeIds(rootId: String, nodes: List<TextbookOutlineNodeEntity>): Set<String> {
        val children = nodes.groupBy { it.parentId }
        val out = LinkedHashSet<String>()
        fun walk(id: String) {
            if (!out.add(id)) return
            children[id].orEmpty().forEach { walk(it.id) }
        }
        walk(rootId)
        return out
    }

    fun normalizeRange(start: Int, end: Int, pageCount: Int): Pair<Int, Int> {
        if (pageCount <= 0) return 0 to 0
        val s = start.coerceIn(0, pageCount - 1)
        val e = end.coerceIn(0, pageCount - 1)
        return if (s <= e) s to e else e to s
    }
}
