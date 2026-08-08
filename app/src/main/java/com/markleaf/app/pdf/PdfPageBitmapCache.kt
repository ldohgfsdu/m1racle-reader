package com.markleaf.app.pdf

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Per-page bitmap slot: keep showing [image] until a successful replace.
 * Layout must use [aspectHw], never bitmap pixel size.
 */
data class PageBitmapEntry(
    val image: ImageBitmap,
    val widthBucket: Int,
    val tier: QualityTier,
    val generation: Long,
    val aspectHw: Float,
)

/**
 * Continuous-mode page cache (lazy window only).
 *
 * Budget: at most [PdfRenderQuality.MAX_HIGH_PAGES] HIGH entry (the primary page).
 * Neighbors are PREVIEW. Never clears an entry solely because a re-render started.
 */
class PdfPageBitmapCache {
    private val map = linkedMapOf<Int, PageBitmapEntry>()

    fun get(pageIndex: Int): PageBitmapEntry? = map[pageIndex]

    fun image(pageIndex: Int): ImageBitmap? = map[pageIndex]?.image

    fun aspect(pageIndex: Int): Float? = map[pageIndex]?.aspectHw

    fun snapshotImages(): Map<Int, ImageBitmap> =
        map.mapValues { it.value.image }

    fun snapshotAspects(): Map<Int, Float> =
        map.mapValues { it.value.aspectHw }

    fun put(
        pageIndex: Int,
        entry: PageBitmapEntry,
        primaryIndex: Int? = null,
        protectedPages: Set<Int> = emptySet(),
    ) {
        map.remove(pageIndex)
        map[pageIndex] = entry
        enforceHighBudget(primaryIndex, protectedPages)
    }

    /**
     * Install only if [generation] is still current.
     * @param allowDowngrade when true, a lower-tier result may replace HIGH (neighbor demote).
     */
    fun putIfCurrent(
        pageIndex: Int,
        entry: PageBitmapEntry,
        currentGeneration: Long,
        allowDowngrade: Boolean = false,
        primaryIndex: Int? = null,
        protectedPages: Set<Int> = emptySet(),
    ): Boolean {
        if (entry.generation != currentGeneration) return false
        val existing = map[pageIndex]
        if (existing != null && existing.generation > entry.generation) {
            return false
        }
        if (!allowDowngrade &&
            existing != null &&
            existing.generation == entry.generation &&
            existing.tier.ordinal > entry.tier.ordinal &&
            existing.widthBucket > entry.widthBucket
        ) {
            return false
        }
        put(pageIndex, entry, primaryIndex, protectedPages)
        return true
    }

    fun retainOnly(keep: Set<Int>) {
        val victims = map.keys.filter { it !in keep }
        victims.forEach { map.remove(it) }
    }

    /** Pages currently tagged HIGH. */
    fun highPages(): List<Int> =
        map.filter { it.value.tier == QualityTier.HIGH }.keys.toList()

    fun clear() {
        map.clear()
    }

    /**
     * OOM recovery hook: drop every non-HIGH entry (previews and lower tiers),
     * keeping high-quality primary pages on screen. Never throws.
     */
    fun trimMemory() {
        val victims = map.filter { it.value.tier != QualityTier.HIGH }.keys.toList()
        victims.forEach { map.remove(it) }
    }

    fun keys(): Set<Int> = map.keys.toSet()

    /**
     * Keep at most one HIGH. Prefer [primaryIndex]; drop other HIGH entries entirely
     * only when a replacement is not available — callers should demote with preview first.
     * If forced, drop non-primary HIGH images (last resort).
     */
    private fun enforceHighBudget(primaryIndex: Int?, protectedPages: Set<Int>) {
        val highs = map.entries.filter { it.value.tier == QualityTier.HIGH }
        if (highs.size <= PdfRenderQuality.MAX_HIGH_PAGES) return
        // Drop HIGH that are not primary first.
        val overflow = highs.size - PdfRenderQuality.MAX_HIGH_PAGES
        var dropped = 0
        val ordered = highs.sortedBy { (page, _) ->
            when {
                page in protectedPages -> 2
                primaryIndex != null && page == primaryIndex -> 1 // drop last
                else -> 0
            }
        }
        for ((page, _) in ordered) {
            if (dropped >= overflow) break
            if (page in protectedPages || (primaryIndex != null && page == primaryIndex)) continue
            map.remove(page)
            dropped++
        }
        // A visible last-good bitmap is more important than the numeric HIGH budget.
        // If every remaining HIGH is protected, temporarily exceed the budget.
        if (map.values.count { it.tier == QualityTier.HIGH } > PdfRenderQuality.MAX_HIGH_PAGES) {
            val iter = map.entries.iterator()
            while (iter.hasNext() &&
                map.values.count { it.tier == QualityTier.HIGH } > PdfRenderQuality.MAX_HIGH_PAGES
            ) {
                val e = iter.next()
                if (e.key in protectedPages) {
                    continue
                } else if (e.value.tier == QualityTier.HIGH &&
                    (primaryIndex == null || e.key != primaryIndex)
                ) {
                    iter.remove()
                } else if (e.value.tier == QualityTier.HIGH) {
                    // only primary left extras — remove non-matching
                    iter.remove()
                }
            }
        }
    }
}
