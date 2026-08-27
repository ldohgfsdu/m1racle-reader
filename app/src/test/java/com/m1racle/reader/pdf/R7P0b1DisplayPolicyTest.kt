package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7_P0B1 display merge policy tests.
 *
 * Pure-logic coverage (no Compose, no PdfRenderer) for:
 *  - tier monotonicity (HIGH not covered by PREVIEW/SCROLL_PREVIEW)
 *  - widthBucket monotonicity within same tier
 *  - DRAGGING: new page accepts SCROLL_PREVIEW, displayed page rejects downgrade
 *  - PINCHING: existing identity frozen, new results go to pending
 *  - SETTLING: accumulate best pending
 *  - SETTLE COMMIT: best-of(displayed, pending), no downgrade-then-upgrade
 */
class R7P0b1DisplayPolicyTest {

    // Helper: create metadata
    private fun meta(tier: QualityTier, widthBucket: Int) = BitmapMetadata(tier, widthBucket)

    // Helper: create a dummy "bitmap" identity
    private fun bitmap(id: Int): Any = "bitmap-$id"

    // ── 1. HIGH not covered by PREVIEW ──

    @Test
    fun `HIGH not covered by PREVIEW in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.PREVIEW, 1600),
        )
        assertEquals(DisplayMergeResult.REJECT, result)
    }

    @Test
    fun `HIGH not covered by SCROLL_PREVIEW in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.REJECT, result)
    }

    // ── 2. PREVIEW not covered by SCROLL_PREVIEW ──

    @Test
    fun `PREVIEW not covered by SCROLL_PREVIEW in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.PREVIEW, 1600))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.REJECT, result)
    }

    // ── 3. SCROLL_PREVIEW can replace PLACEHOLDER (null displayed) ──

    @Test
    fun `SCROLL_PREVIEW accepted when no displayed bitmap exists`() {
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = null,
            newBitmap = bitmap(1),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.ACCEPT, result)
    }

    // ── 4. same tier lower widthBucket rejected ──

    @Test
    fun `same tier lower widthBucket rejected in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 2048),
        )
        assertEquals(DisplayMergeResult.REJECT, result)
    }

    // ── 5. same tier higher widthBucket accepted ──

    @Test
    fun `same tier higher widthBucket accepted in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 2048))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.ACCEPT, result)
    }

    // ── 6. higher tier accepted (upgrade) ──

    @Test
    fun `PREVIEW upgraded to HIGH accepted in IDLE`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.PREVIEW, 1600))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.ACCEPT, result)
    }

    // ── 7. DRAGGING: new page accepts SCROLL_PREVIEW ──

    @Test
    fun `DRAGGING new page accepts SCROLL_PREVIEW`() {
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.DRAGGING,
            displayed = null,
            newBitmap = bitmap(1),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.ACCEPT, result)
    }

    // ── 8. DRAGGING: displayed page rejects downgrade ──

    @Test
    fun `DRAGGING displayed page rejects SCROLL_PREVIEW over PREVIEW`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.PREVIEW, 1600))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.DRAGGING,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.REJECT, result)
    }

    // ── 9. DRAGGING: higher tier can upgrade ──

    @Test
    fun `DRAGGING higher tier upgrade accepted`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.SCROLL_PREVIEW, 640))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.DRAGGING,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.ACCEPT, result)
    }

    // ── 10. PINCHING: existing identity frozen, new result goes to pending ──

    @Test
    fun `PINCHING new result for displayed page goes to pending`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.SCROLL_PREVIEW, 640))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.PINCHING,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.PENDING, result)
    }

    @Test
    fun `PINCHING new page goes to pending`() {
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.PINCHING,
            displayed = null,
            newBitmap = bitmap(1),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.PENDING, result)
    }

    // ── 11. SETTLING: new result goes to pending ──

    @Test
    fun `SETTLING new result goes to pending`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.SETTLING,
            displayed = displayed,
            newBitmap = bitmap(2),
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.PENDING, result)
    }

    @Test
    fun `SETTLING new page goes to pending`() {
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.SETTLING,
            displayed = null,
            newBitmap = bitmap(1),
            newMetadata = meta(QualityTier.SCROLL_PREVIEW, 640),
        )
        assertEquals(DisplayMergeResult.PENDING, result)
    }

    @Test
    fun `pending accumulation keeps best result for a page`() {
        val high = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val preview = DisplayedBitmap(bitmap(2), meta(QualityTier.PREVIEW, 1600))
        assertEquals(high, DisplayMergePolicy.bestPending(high, preview))
    }

    @Test
    fun `settle commit excludes pending pages outside required composition`() {
        val displayed = mapOf(4 to DisplayedBitmap(bitmap(1), meta(QualityTier.PREVIEW, 1600)))
        val pending = mapOf(
            4 to DisplayedBitmap(bitmap(2), meta(QualityTier.HIGH, 3072)),
            99 to DisplayedBitmap(bitmap(3), meta(QualityTier.HIGH, 3072)),
        )

        val result = DisplayMergePolicy.settleCommit(displayed, pending, requiredPages = setOf(4))

        assertEquals(setOf(4), result.keys)
        assertEquals(bitmap(2), result.getValue(4).bitmap)
    }

    // ── 12. SETTLE COMMIT: best-of(displayed, pending) ──

    @Test
    fun `settle commit picks higher tier pending over displayed`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.SCROLL_PREVIEW, 640))
        val pending = DisplayedBitmap(bitmap(2), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.settle(displayed, pending)
        assertEquals(bitmap(2), result!!.bitmap)
    }

    @Test
    fun `settle commit keeps displayed when pending is lower tier`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val pending = DisplayedBitmap(bitmap(2), meta(QualityTier.SCROLL_PREVIEW, 640))
        val result = DisplayMergePolicy.settle(displayed, pending)
        assertEquals(bitmap(1), result!!.bitmap)
    }

    @Test
    fun `settle commit returns displayed when no pending`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.settle(displayed, null)
        assertEquals(bitmap(1), result!!.bitmap)
    }

    @Test
    fun `settle commit returns pending when no displayed`() {
        val pending = DisplayedBitmap(bitmap(1), meta(QualityTier.SCROLL_PREVIEW, 640))
        val result = DisplayMergePolicy.settle(null, pending)
        assertEquals(bitmap(1), result!!.bitmap)
    }

    // ── 13. same bitmap identity skip ──

    @Test
    fun `same bitmap identity returns SKIP`() {
        val sameBitmap = bitmap(1)
        val displayed = DisplayedBitmap(sameBitmap, meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.merge(
            phase = ContinuousInteractionPhase.IDLE,
            displayed = displayed,
            newBitmap = sameBitmap,
            newMetadata = meta(QualityTier.HIGH, 3072),
        )
        assertEquals(DisplayMergeResult.SKIP, result)
    }

    // ── 14. settle commit same tier picks higher widthBucket ──

    @Test
    fun `settle commit same tier picks higher widthBucket`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 2048))
        val pending = DisplayedBitmap(bitmap(2), meta(QualityTier.HIGH, 3072))
        val result = DisplayMergePolicy.settle(displayed, pending)
        assertEquals(bitmap(2), result!!.bitmap)
    }

    @Test
    fun `settle commit same tier keeps displayed when pending lower widthBucket`() {
        val displayed = DisplayedBitmap(bitmap(1), meta(QualityTier.HIGH, 3072))
        val pending = DisplayedBitmap(bitmap(2), meta(QualityTier.HIGH, 2048))
        val result = DisplayMergePolicy.settle(displayed, pending)
        assertEquals(bitmap(1), result!!.bitmap)
    }
}
