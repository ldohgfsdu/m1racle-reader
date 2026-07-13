package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRenderQualityTest {

    @Test
    fun preview_medium_high_caps() {
        // viewport 1080
        assertEquals(540, PdfRenderQuality.targetWidthPx(1080, QualityTier.SCROLL_PREVIEW))
        assertEquals(1080, PdfRenderQuality.targetWidthPx(1080, QualityTier.PREVIEW))
        assertEquals(2160, PdfRenderQuality.targetWidthPx(1080, QualityTier.MEDIUM)) // 1080*2.0
        assertEquals(3024, PdfRenderQuality.targetWidthPx(1080, QualityTier.HIGH)) // 1080*2.8
    }

    @Test
    fun caps_enforce_max_no_4096() {
        assertEquals(1600, PdfRenderQuality.targetWidthPx(2000, QualityTier.PREVIEW))
        assertEquals(2304, PdfRenderQuality.targetWidthPx(2000, QualityTier.MEDIUM))
        assertEquals(3072, PdfRenderQuality.targetWidthPx(2000, QualityTier.HIGH))
        assertTrue(PdfRenderQuality.MAX_CONTINUOUS_WIDTH_PX <= 3072)
        assertTrue(PdfRenderQuality.targetWidthPx(4000, QualityTier.HIGH) <= 3072)
    }

    @Test
    fun hysteresis_preview_to_medium() {
        assertEquals(
            QualityTier.PREVIEW,
            PdfRenderQuality.resolveTier(1.40f, QualityTier.PREVIEW),
        )
        assertEquals(
            QualityTier.MEDIUM,
            PdfRenderQuality.resolveTier(1.45f, QualityTier.PREVIEW),
        )
    }

    @Test
    fun hysteresis_medium_to_preview() {
        assertEquals(
            QualityTier.MEDIUM,
            PdfRenderQuality.resolveTier(1.30f, QualityTier.MEDIUM),
        )
        assertEquals(
            QualityTier.PREVIEW,
            PdfRenderQuality.resolveTier(1.25f, QualityTier.MEDIUM),
        )
    }

    @Test
    fun hysteresis_medium_high() {
        assertEquals(
            QualityTier.HIGH,
            PdfRenderQuality.resolveTier(2.15f, QualityTier.MEDIUM),
        )
        assertEquals(
            QualityTier.MEDIUM,
            PdfRenderQuality.resolveTier(1.85f, QualityTier.HIGH),
        )
    }

    @Test
    fun role_caps_neighbors_to_preview() {
        assertEquals(
            QualityTier.SCROLL_PREVIEW,
            PdfRenderQuality.cappedTier(QualityTier.HIGH, PageRenderRole.PRELOAD),
        )
        assertEquals(
            QualityTier.MEDIUM,
            PdfRenderQuality.cappedTier(QualityTier.HIGH, PageRenderRole.VISIBLE_OTHER),
        )
        assertEquals(
            QualityTier.HIGH,
            PdfRenderQuality.cappedTier(QualityTier.HIGH, PageRenderRole.PRIMARY),
        )
    }

    @Test
    fun keepRange_plusMinusOne() {
        assertEquals(4..6, PdfRenderQuality.keepRange(5, 100))
        assertEquals(0..1, PdfRenderQuality.keepRange(0, 100))
    }

    @Test
    fun estimatedBytes_high_under_budget() {
        val bytes = PdfRenderQuality.estimatedBytes(3072, 1.414f)
        // ~3072 * 4344 * 4 ≈ 53MB theoretical; we care about not using 4096.
        assertTrue(bytes > 0)
        val old4096 = PdfRenderQuality.estimatedBytes(4096, 1.414f)
        assertTrue(bytes < old4096)
    }

    @Test
    fun clampZoom_max_3() {
        assertEquals(1.0f, PdfRenderQuality.clampZoom(0.5f))
        assertEquals(3f, PdfRenderQuality.clampZoom(5f))
        assertEquals(2.0f, PdfRenderQuality.clampZoom(2f))
    }

    @Test
    fun cacheKey_includesWidthBucket() {
        val k1 = PdfRenderQuality.cacheKey("doc", 10, 1350)
        val k2 = PdfRenderQuality.cacheKey("doc", 10, 2160)
        assertTrue(k1 != k2)
    }

    @Test
    fun maxHighPages_isOne() {
        assertEquals(1, PdfRenderQuality.MAX_HIGH_PAGES)
    }
}
