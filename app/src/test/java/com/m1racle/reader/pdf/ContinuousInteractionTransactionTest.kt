package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousInteractionTransactionTest {
    private val doc = ContinuousDocumentState(scale = 2f, scrollX = 30f, scrollY = 400f)

    @Test fun `rapid pinch reuses one interaction id`() {
        val first = ContinuousInteractionReducer.pinchStart(
            ContinuousInteractionTransaction(), setOf(4, 5, 6), mapOf(5 to 101), doc,
        )
        val ended = ContinuousInteractionReducer.pinchEnd(first, doc, 1_000L)
        val reused = ContinuousInteractionReducer.pinchStart(ended, setOf(8), mapOf(8 to 202), doc)
        assertEquals(first.interactionId, reused.interactionId)
        assertEquals(setOf(4, 5, 6), reused.stableVisiblePages)
    }

    @Test fun `short release does not settle and new pinch cancels settling`() {
        val start = ContinuousInteractionReducer.pinchStart(
            ContinuousInteractionTransaction(), setOf(1), emptyMap(), doc,
        )
        val end = ContinuousInteractionReducer.pinchEnd(start, doc, 1_000L)
        assertFalse(ContinuousInteractionReducer.canSettle(end, 1_449L))
        assertEquals(ContinuousInteractionPhase.PINCHING,
            ContinuousInteractionReducer.pinchStart(end, setOf(2), emptyMap(), doc).phase)
    }

    @Test fun `ten pinches have one final settle commit`() {
        var transaction = ContinuousInteractionTransaction()
        repeat(10) { index ->
            transaction = ContinuousInteractionReducer.pinchStart(transaction, setOf(1, 2), emptyMap(), doc)
            transaction = ContinuousInteractionReducer.pinchEnd(transaction, doc, index * 100L)
        }
        transaction = ContinuousInteractionReducer.settleCommit(transaction, 1_350L)
        assertEquals(1, transaction.settleCommitCount)
        assertEquals(ContinuousInteractionPhase.IDLE, transaction.phase)
    }

    @Test fun `visible pages only append during transaction`() {
        val start = ContinuousInteractionReducer.pinchStart(
            ContinuousInteractionTransaction(), setOf(10, 11, 12), emptyMap(), doc,
        )
        assertEquals(setOf(10, 11, 12, 13),
            ContinuousInteractionReducer.appendPages(start, setOf(12, 13)).stableVisiblePages)
    }

    @Test fun `transform preserves frozen page and bitmap collection identity`() {
        val start = ContinuousInteractionReducer.pinchStart(
            ContinuousInteractionTransaction(), setOf(3, 4), mapOf(3 to 99), doc,
        )
        val moved = ContinuousInteractionReducer.transform(
            start,
            doc.copy(scale = 2.5f, scrollY = 800f),
        )
        assertSame(start.stableVisiblePages, moved.stableVisiblePages)
        assertSame(start.stableDisplayedBitmapIds, moved.stableDisplayedBitmapIds)
    }

    @Test fun `background bitmap remains pending until settle`() {
        val old = Any()
        val next = Any()
        val pending = StableBitmapSlot(displayed = old).receive(next)
        assertSame(old, pending.displayed)
        assertSame(next, pending.pending)
        val settled = pending.settle()
        assertSame(next, settled.displayed)
        assertTrue(settled.pending == null)
    }

    @Test fun `pending never nulls last good bitmap`() {
        val old = Any()
        assertSame(old, StableBitmapSlot(displayed = old).receive(Any()).displayed)
    }

    @Test fun `continuous quality hysteresis does not flap near one point five`() {
        assertEquals(QualityTier.PREVIEW, ContinuousQualityHysteresis.resolve(1.49f, QualityTier.PREVIEW))
        assertEquals(QualityTier.PREVIEW, ContinuousQualityHysteresis.resolve(1.51f, QualityTier.PREVIEW))
        assertEquals(QualityTier.HIGH, ContinuousQualityHysteresis.resolve(1.50f, QualityTier.HIGH))
        assertEquals(QualityTier.PREVIEW, ContinuousQualityHysteresis.resolve(1.34f, QualityTier.HIGH))
    }
}
