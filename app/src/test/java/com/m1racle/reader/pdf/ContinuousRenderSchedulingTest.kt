package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousRenderSchedulingTest {
    private val scroll = ContinuousRenderKey("doc", 7, QualityTier.SCROLL_PREVIEW, 540)
    private val high = ContinuousRenderKey("doc", 7, QualityTier.HIGH, 3024)

    @Test fun draggingAndFlingingOnlyAllowScrollPreview() {
        assertTrue(ContinuousRenderScheduling.allowedTierDuring(ContinuousScrollPhase.DRAGGING, QualityTier.SCROLL_PREVIEW))
        assertFalse(ContinuousRenderScheduling.allowedTierDuring(ContinuousScrollPhase.DRAGGING, QualityTier.PREVIEW))
        assertFalse(ContinuousRenderScheduling.allowedTierDuring(ContinuousScrollPhase.FLINGING, QualityTier.HIGH))
    }

    @Test fun smallViewportChangeKeepsValuableScrollRequest() {
        assertTrue(ContinuousRenderScheduling.shouldKeepRunning(scroll, setOf(6, 7, 8), ContinuousScrollPhase.FLINGING))
        assertFalse(ContinuousRenderScheduling.shouldKeepRunning(scroll, setOf(20), ContinuousScrollPhase.FLINGING))
        assertFalse(ContinuousRenderScheduling.shouldKeepRunning(high, setOf(7), ContinuousScrollPhase.FLINGING))
    }

    @Test fun renderKeyDeduplicatesByDocumentPageTierAndWidth() {
        assertEquals(1, setOf(scroll, scroll.copy()).size)
        assertEquals(2, setOf(scroll, scroll.copy(widthBucket = 640)).size)
    }

    @Test fun idleReadableForVisibleAndHighOnlyForPrimary() {
        val snapshot = ContinuousViewportSnapshot(setOf(6, 7, 8), 7, ContinuousScrollDirection.IDLE, ContinuousScrollPhase.IDLE)
        val upgrades = ContinuousRenderScheduling.idleUpgradePages(snapshot)
        assertEquals(setOf(QualityTier.PREVIEW), upgrades[6])
        assertEquals(setOf(QualityTier.PREVIEW, QualityTier.HIGH), upgrades[7])
        assertEquals(setOf(QualityTier.PREVIEW), upgrades[8])
    }
}
