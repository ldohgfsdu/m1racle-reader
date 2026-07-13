package com.markleaf.app.pdf

import org.junit.Assert.*
import org.junit.Test

class R7P0b11PointerEpochTest {
    private val geometry = ContinuousDocumentGeometry.create(100, 1000f, emptyMap(), 10f)
    private fun frame(epoch: ContinuousPointerEpoch?, pointers: List<PointerSample>, state: ContinuousDocumentState) =
        ContinuousPointerEpochReducer.frame(epoch, pointers, state, 1000f, 1800f, geometry)

    @Test fun `one to two rebase frame does not transform`() {
        val state = ContinuousDocumentState(2f, 300f, 20_000f, true)
        val one = frame(null, listOf(PointerSample(1, 400f, 800f)), state)
        val two = frame(one.epoch, listOf(PointerSample(1, 390f, 800f), PointerSample(2, 610f, 800f)), state)
        assertTrue(two.rebased); assertEquals(state, two.state)
    }

    @Test fun `two to one rebase frame does not transform or settle`() {
        val state = ContinuousDocumentState(2f, 300f, 20_000f, true)
        val two = frame(null, listOf(PointerSample(1, 390f, 800f), PointerSample(2, 610f, 800f)), state)
        val one = frame(two.epoch, listOf(PointerSample(1, 450f, 820f)), state)
        assertTrue(one.rebased); assertFalse(one.shouldSettle); assertEquals(state, one.state)
    }

    @Test fun `third pointer keeps locked pair and skips frame`() {
        val state = ContinuousDocumentState(2f, 300f, 20_000f, true)
        val two = frame(null, listOf(PointerSample(5, 300f, 800f), PointerSample(9, 700f, 800f)), state)
        val three = frame(two.epoch, listOf(PointerSample(5, 300f, 800f), PointerSample(9, 700f, 800f), PointerSample(12, 1000f, 100f)), state)
        assertEquals(5L, three.epoch!!.primaryPointerId); assertEquals(9L, three.epoch!!.secondaryPointerId)
        assertEquals(state, three.state)
    }

    @Test fun `pair replacement frame only rebases`() {
        val state = ContinuousDocumentState(2f, 300f, 20_000f, true)
        val before = frame(null, listOf(PointerSample(1, 300f, 800f), PointerSample(2, 700f, 800f)), state)
        val replaced = frame(before.epoch, listOf(PointerSample(2, 700f, 800f), PointerSample(3, 900f, 800f)), state)
        assertTrue(replaced.rebased); assertEquals(state, replaced.state); assertEquals(2L, replaced.epoch!!.primaryPointerId)
    }

    @Test fun `fixed pair uses span ratio from epoch baseline`() {
        val state = ContinuousDocumentState(1f, 0f, 20_000f, true)
        val base = frame(null, listOf(PointerSample(1, 400f, 800f), PointerSample(2, 600f, 800f)), state)
        val zoom = frame(base.epoch, listOf(PointerSample(1, 300f, 800f), PointerSample(2, 700f, 800f)), state)
        assertEquals(2f, zoom.state.scale, 0.0001f)
    }

    @Test fun `all pointers up settles once without modifying transform`() {
        val state = ContinuousDocumentState(2.4f, 500f, 61_000f, true)
        val base = frame(null, listOf(PointerSample(1, 300f, 800f), PointerSample(2, 700f, 800f)), state)
        val up = frame(base.epoch, emptyList(), state)
        assertTrue(up.shouldSettle); assertEquals(state, up.state)
        assertFalse(frame(up.epoch, emptyList(), state).shouldSettle)
    }
}
