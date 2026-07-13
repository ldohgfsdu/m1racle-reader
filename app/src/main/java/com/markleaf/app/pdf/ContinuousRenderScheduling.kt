package com.markleaf.app.pdf

data class ContinuousRenderKey(
    val documentId: String,
    val pageIndex: Int,
    val tier: QualityTier,
    val widthBucket: Int,
)

object ContinuousRenderScheduling {
    fun allowedTierDuring(phase: ContinuousScrollPhase, tier: QualityTier): Boolean =
        phase == ContinuousScrollPhase.IDLE || tier == QualityTier.SCROLL_PREVIEW

    fun shouldKeepRunning(
        key: ContinuousRenderKey,
        protectedPages: Set<Int>,
        phase: ContinuousScrollPhase,
    ): Boolean = key.pageIndex in protectedPages && allowedTierDuring(phase, key.tier)

    fun idleUpgradePages(snapshot: ContinuousViewportSnapshot): Map<Int, Set<QualityTier>> {
        if (snapshot.phase != ContinuousScrollPhase.IDLE) return emptyMap()
        return snapshot.visiblePages.associateWith { page ->
            if (page == snapshot.primaryPage) setOf(QualityTier.PREVIEW, QualityTier.HIGH)
            else setOf(QualityTier.PREVIEW)
        }
    }
}
