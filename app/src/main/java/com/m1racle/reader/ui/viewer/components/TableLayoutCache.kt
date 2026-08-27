package com.m1racle.reader.ui.viewer.components

import com.m1racle.reader.markdown.MarkdownPerformance

/** Full identity of one immutable R4 table-layout solution. Raw scroll offsets are excluded. */
internal data class TableLayoutCacheKey(
    val documentId: String,
    val stableTableId: String,
    val tableContentHash: Int,
    val viewportWidthPx: Float,
    val density: Float,
    val systemFontScale: Float,
    val textSizeSetting: Float,
    val lineHeightSetting: Float,
    val pageMarginSetting: String,
    val typographyIdentity: Int,
    val layoutDirection: String,
)

/** Immutable cached result used directly by Compose after a table re-enters the viewport. */
internal data class CachedTableLayout(
    val metrics: TableColumnMetrics,
    val measuredCellCount: Long,
)

/**
 * Document-scoped LRU. It outlives individual LazyColumn table item compositions, so scrolling a
 * table out and back in cannot rerun the width solver under an unchanged rendering environment.
 */
class TableLayoutCache(
    private val documentId: String,
    private val maximumEntries: Int = 48,
) {
    private val values = object : LinkedHashMap<TableLayoutCacheKey, CachedTableLayout>(
        maximumEntries,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TableLayoutCacheKey, CachedTableLayout>?,
        ): Boolean = size > maximumEntries
    }
    private val horizontalOffsets = mutableMapOf<String, Int>()

    internal fun key(
        stableTableId: String,
        tableContentHash: Int,
        viewportWidthPx: Float,
        density: Float,
        systemFontScale: Float,
        textSizeSetting: Float,
        lineHeightSetting: Float,
        pageMarginSetting: String,
        typographyIdentity: Int,
        layoutDirection: String,
    ): TableLayoutCacheKey = TableLayoutCacheKey(
        documentId = documentId,
        stableTableId = stableTableId,
        tableContentHash = tableContentHash,
        viewportWidthPx = viewportWidthPx,
        density = density,
        systemFontScale = systemFontScale,
        textSizeSetting = textSizeSetting,
        lineHeightSetting = lineHeightSetting,
        pageMarginSetting = pageMarginSetting,
        typographyIdentity = typographyIdentity,
        layoutDirection = layoutDirection,
    )

    internal fun getOrSolve(
        key: TableLayoutCacheKey,
        solve: () -> CachedTableLayout,
    ): CachedTableLayout {
        values[key]?.let {
            MarkdownPerformance.recordCacheHit()
            return it
        }
        MarkdownPerformance.recordCacheMiss()
        val started = System.nanoTime()
        val result = MarkdownPerformance.trace("MarkdownTableSolve", solve)
        MarkdownPerformance.recordTableSolve(
            durationNanos = System.nanoTime() - started,
            measuredCells = result.measuredCellCount,
        )
        values[key] = result
        return result
    }

    internal fun horizontalOffset(stableTableId: String): Int =
        horizontalOffsets[stableTableId] ?: 0

    internal fun saveHorizontalOffset(stableTableId: String, value: Int) {
        horizontalOffsets[stableTableId] = value.coerceAtLeast(0)
    }

    fun clear() {
        values.clear()
        horizontalOffsets.clear()
    }

    internal fun entryCount(): Int = values.size
}
