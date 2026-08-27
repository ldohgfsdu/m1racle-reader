package com.m1racle.reader.ui.viewer.components

import com.m1racle.reader.markdown.MarkdownPerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TableLayoutCacheTest {

    @Test
    fun unchangedTableReentry_reusesSolvedLayout() {
        MarkdownPerformance.reset()
        val cache = TableLayoutCache("document-1")
        val key = cacheKey(cache)
        var solves = 0

        repeat(3) {
            cache.getOrSolve(key) {
                solves++
                CachedTableLayout(sampleMetrics(), measuredCellCount = 8)
            }
        }

        val counters = MarkdownPerformance.snapshot()
        assertEquals(1, solves)
        assertEquals(1, counters.tableSolveCount)
        assertEquals(2, counters.cacheHitCount)
        assertEquals(1, cache.entryCount())
    }

    @Test
    fun configurationChange_invalidatesButScrollOffsetDoesNot() {
        val cache = TableLayoutCache("document-1")
        val defaultKey = cacheKey(cache)
        val largeTextKey = cacheKey(cache, textSizeSetting = 1.2f)

        assertNotEquals(defaultKey, largeTextKey)
        cache.saveHorizontalOffset("table-3", 137)
        assertEquals(defaultKey, cacheKey(cache))
        assertEquals(137, cache.horizontalOffset("table-3"))
    }

    private fun cacheKey(
        cache: TableLayoutCache,
        textSizeSetting: Float = 1f,
    ): TableLayoutCacheKey = cache.key(
        stableTableId = "table-3",
        tableContentHash = 42,
        viewportWidthPx = 1080f,
        density = 3f,
        systemFontScale = 1f,
        textSizeSetting = textSizeSetting,
        lineHeightSetting = 1f,
        pageMarginSetting = "STANDARD",
        typographyIdentity = 7,
        layoutDirection = "Ltr",
    )

    private fun sampleMetrics() = TableColumnMetrics(
        widthsPx = listOf(240f, 600f),
        totalWidthPx = 840f,
        viewportWidthPx = 840f,
        horizontallyScrollable = false,
    )
}
