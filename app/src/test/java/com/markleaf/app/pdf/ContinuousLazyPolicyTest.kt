package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Product policy: continuous mode stays on preview while scrolling;
 * hi-res only for primary after idle / pinch end.
 */
class ContinuousLazyPolicyTest {

    @Test
    fun scroll_doesNotNeedHiRes_at1x() {
        assertFalse(PdfRenderQuality.continuousNeedsHiRes(1.0f))
        assertFalse(PdfRenderQuality.continuousNeedsHiRes(1.01f))
    }

    @Test
    fun zoomed_needsHiRes() {
        assertTrue(PdfRenderQuality.continuousNeedsHiRes(1.5f))
        assertTrue(PdfRenderQuality.continuousNeedsHiRes(2.0f))
    }

    @Test
    fun neighbors_always_preview_cap() {
        assertEquals(
            QualityTier.SCROLL_PREVIEW,
            PdfRenderQuality.cappedTier(QualityTier.HIGH, PageRenderRole.PRELOAD),
        )
    }

    @Test
    fun keepWindow_is_primary_plus_minus_one() {
        assertEquals(9..11, PdfRenderQuality.keepRange(10, 100))
    }

    @Test
    fun max_one_high() {
        assertEquals(1, PdfRenderQuality.MAX_HIGH_PAGES)
    }

    @Test
    fun idle_upgrade_debounce_200ms() {
        assertEquals(200L, PdfRenderQuality.SCROLL_IDLE_UPGRADE_MS)
        assertEquals(200L, PdfRenderQuality.ZOOM_SETTLE_DEBOUNCE_MS)
    }

    @Test
    fun primary_upgrade_at_zoom_uses_medium_or_high_not_preview() {
        val t = PdfRenderQuality.continuousPrimaryUpgradeTier(2.0f, QualityTier.PREVIEW)
        assertTrue(t == QualityTier.MEDIUM || t == QualityTier.HIGH)
        assertEquals(
            QualityTier.PREVIEW,
            PdfRenderQuality.continuousPrimaryUpgradeTier(1.0f, QualityTier.PREVIEW),
        )
    }
}
