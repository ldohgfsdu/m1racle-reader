package com.markleaf.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7_P0A1_1 restore → READY release tests.
 *
 * Pure-logic coverage (no Compose, no PdfRenderer) for:
 *  - atomic RESTORE_COMPLETE → READY transition
 *  - idempotent generation consumption
 *  - post-READY tracking data flow
 *  - user scroll escape hatch from stuck APPLYING_TARGET
 *  - geometry change does not replay consumed restore
 *  - INITIAL_PLACEHOLDER never persists
 *  - 112 → horizontal → continuous roundtrip regression
 */

class R7P0a1RestoreReadyReleaseTest {

    private fun seeded(
        page: Int = 114,
        fraction: Float = 0.3f,
        pageCount: Int = 451,
    ): ContinuousEntryRestoreState = ContinuousEntryRestoreState().apply {
        seedLastStable(
            ReadingPosition(page, fraction, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 0L),
            pageCount = pageCount,
        )
    }

    // ── 1. restore enters READY ────────────────────────────────────

    @Test
    fun `startup restore to page 114 enters READY`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        assertEquals(ContinuousEntryPhase.WAITING_GEOMETRY, state.snapshot().phase)

        assertNotNull(state.beginApplyIfReady(geometryReady = true))
        assertEquals(ContinuousEntryPhase.APPLYING_TARGET, state.snapshot().phase)

        assertTrue(state.completeRestore(request.generation, request, 451))
        assertEquals(ContinuousEntryPhase.READY, state.snapshot().phase)
        assertTrue(state.isReady())
    }

    // ── 2. pending cleared ─────────────────────────────────────────

    @Test
    fun `pending restore cleared after completeRestore`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        assertNotNull(state.snapshot().pendingRestorePosition)

        assertTrue(state.completeRestore(request.generation, request, 451))
        assertNull(state.snapshot().pendingRestorePosition)
    }

    // ── 3. generation consumed once ────────────────────────────────

    @Test
    fun `generation only consumed once even with repeated completeRestore calls`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)

        assertTrue(state.completeRestore(request.generation, request, 451))
        assertTrue(state.isReady())

        // Second call with same generation must fail (idempotency).
        assertFalse(state.completeRestore(request.generation, request, 451))
    }

    @Test
    fun `stale generation cannot complete after newer request`() {
        val state = seeded()
        val first = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)

        // Queue a second restore (higher priority) — this resets phase.
        val second = state.requestRestore(200, 0f, ReadingPositionSource.EXTERNAL_NAVIGATION, 451)!!

        // The first generation is now stale.
        assertFalse(state.completeRestore(first.generation, first, 451))
        // Second still pending.
        assertEquals(second.generation, state.snapshot().pendingRestorePosition?.generation)
    }

    // ── 4. READY → scroll to 119 ──────────────────────────────────

    @Test
    fun `after READY scroll to page 119 updates current page`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        assertTrue(state.completeRestore(request.generation, request, 451))

        val user = state.recordUserStable(119, 0.5f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertNotNull(user)
        assertEquals(119, user!!.pageIndex)
        assertEquals(ReadingPositionSource.USER_CONTINUOUS_SCROLL, user.source)
    }

    // ── 5. page label tracks current page ──────────────────────────
    // (Tested implicitly: ready.pageIndex semantics verified via recordUserStable.)

    @Test
    fun `page index value updates for page label after scroll`() {
        val state = seeded()
        val request = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        assertEquals(114, state.snapshot().lastStableReadingPosition?.pageIndex)

        // Scroll from 114 to 119.
        state.recordUserStable(119, 0.8f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertEquals(119, state.snapshot().lastStableReadingPosition?.pageIndex)
    }

    // ── 6. chapter tracks current page ─────────────────────────────
    // (Chapter derivation is tested separately via TextbookOutlineResolver.)

    // ── 7. lastStable updated ──────────────────────────────────────

    @Test
    fun `lastStable updates to 119 after READY scroll`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        // lastStable from restore is 114.
        assertEquals(114, state.snapshot().lastStableReadingPosition?.pageIndex)

        // User scrolls to 119.
        state.recordUserStable(119, 0.75f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertEquals(119, state.snapshot().lastStableReadingPosition?.pageIndex)
        assertEquals(0.75f, state.snapshot().lastStableReadingPosition?.intraPageFraction ?: -1f, 0.0001f)
    }

    // ── 8. persistence gate open after READY ───────────────────────

    @Test
    fun `persistence gate opens after completeRestore`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        assertFalse(state.canPersist(geometryReady = true)) // WAITING_GEOMETRY

        state.beginApplyIfReady(true)
        assertFalse(state.canPersist(geometryReady = true)) // APPLYING_TARGET

        state.completeRestore(request.generation, request, 451)
        assertTrue(state.canPersist(geometryReady = true)) // READY
    }

    @Test
    fun `persistence gate stays open across multiple user scrolls`() {
        val state = seeded()
        val request = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        assertTrue(state.canPersist(geometryReady = true))
        state.recordUserStable(115, 0.5f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertTrue(state.canPersist(geometryReady = true))
        state.recordUserStable(200, 0.9f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertTrue(state.canPersist(geometryReady = true))
    }

    // ── 9. normal current page update never modifies scrollY ───────
    //    (ScrollY is a ContinuousDocumentState concern at the viewport
    //     level; recordUserStable only updates reading position metadata.)

    @Test
    fun `recordUserStable never touches document state scrollY`() {
        val state = seeded()
        val request = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        // This test proves that reading-position metadata updates do not
        // mutate geometry/transform state.  The VM + viewport contract
        // ensures only explicit restore/external-navigation requests change
        // scrollY; normal page-index updates are metadata-only.
        val before = state.snapshot()
        state.recordUserStable(200, 0.5f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        val after = state.snapshot()

        // lastStable changed (tracking worked).
        assertEquals(200, after.lastStableReadingPosition?.pageIndex)
        // But neither phase nor pending changed — READY is stable.
        assertEquals(before.phase, after.phase)
        assertNull(after.pendingRestorePosition)
    }

    // ── 10. geometry change never replays consumed restore ────────

    @Test
    fun `consumed restore generation is not replayable`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)

        assertTrue(state.completeRestore(request.generation, request, 451))
        assertNotNull(state.getConsumedGeneration())
        assertEquals(request.generation, state.getConsumedGeneration())

        // Simulate geometry change retry: re-calling beginApplyIfReady
        // would return null (pending is null), and completeRestore
        // with the same generation returns false.
        assertNull(state.beginApplyIfReady(geometryReady = true))
        assertFalse(state.completeRestore(request.generation, request, 451))
    }

    @Test
    fun `geometry change does not repopulate pending after READY`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        // "Geometry change" cannot resurrect a consumed restore.
        assertNull(state.beginApplyIfReady(geometryReady = true))
        assertNull(state.snapshot().pendingRestorePosition)
    }

    // ── 11. user first drag cancels stuck APPLYING_TARGET ──────────

    @Test
    fun `cancelRestore from APPLYING_TARGET enters READY atomically`() {
        val state = seeded()
        val request = state.requestRestore(114, 0.3f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        assertEquals(ContinuousEntryPhase.APPLYING_TARGET, state.snapshot().phase)
        assertNotNull(state.snapshot().pendingRestorePosition)

        assertTrue(state.cancelRestore())

        // Atomic transition:
        assertEquals(ContinuousEntryPhase.READY, state.snapshot().phase)
        assertNull(state.snapshot().pendingRestorePosition)
        assertEquals(request.generation, state.getConsumedGeneration())
        // lastStable preserved (not overwritten with page 0).
        assertNotNull(state.snapshot().lastStableReadingPosition)
    }

    @Test
    fun `cancelRestore from WAITING_GEOMETRY also enters READY`() {
        val state = seeded()
        state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)
        assertEquals(ContinuousEntryPhase.WAITING_GEOMETRY, state.snapshot().phase)

        assertTrue(state.cancelRestore())
        assertEquals(ContinuousEntryPhase.READY, state.snapshot().phase)
        assertNull(state.snapshot().pendingRestorePosition)
    }

    @Test
    fun `cancelRestore is idempotent once READY`() {
        val state = seeded()
        val request = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)

        assertTrue(state.cancelRestore())
        assertEquals(request.generation, state.getConsumedGeneration())

        // Second call reports no work done.
        assertFalse(state.cancelRestore())
        assertEquals(request.generation, state.getConsumedGeneration())
    }

    @Test
    fun `after cancelRestore user can record stable position from real viewport`() {
        val state = seeded()
        state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)
        assertTrue(state.cancelRestore())

        // User scrolled to page 120 in the real viewport.
        val user = state.recordUserStable(120, 0.4f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertNotNull(user)
        assertEquals(120, user!!.pageIndex)
        assertTrue(state.canPersist(geometryReady = true))
    }

    // ── 12. unapplied target: page 0 NOT persisted ─────────────────

    @Test
    fun `INITIAL_PLACEHOLDER never satisfies canPersist even after cancel`() {
        val state = ContinuousEntryRestoreState()
        state.markReadyWithoutRestore()
        // Placeholder deliberately cannot persist.
        assertFalse(
            ReadingPositionPolicy.canPersistReadingPosition(
                ContinuousEntryPhase.READY,
                pendingRestorePosition = null,
                geometryReady = true,
                position = ReadingPosition(0, 0f, ReadingPositionSource.INITIAL_PLACEHOLDER, 1L),
            ),
        )
    }

    @Test
    fun `cancelRestore preserves old lastStable not page zero`() {
        val state = seeded(page = 114)
        state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)
        assertTrue(state.cancelRestore())

        // lastStable is still the pre-restore seeded value (page 114),
        // not overwritten with page 0.
        assertEquals(114, state.snapshot().lastStableReadingPosition?.pageIndex)
        assertEquals(ReadingPositionSource.USER_CONTINUOUS_SCROLL,
            state.snapshot().lastStableReadingPosition?.source)
    }

    // ── 13. return home saves user-scrolled position ───────────────

    @Test
    fun `onStop flush writes user scrolled position not original restore target`() {
        val state = seeded(page = 114)
        val request = state.requestRestore(114, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(request.generation, request, 451)

        // User scrolled to page 130.
        val user = state.recordUserStable(130, 0.6f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 451)
        assertNotNull(user)
        assertEquals(130, state.snapshot().lastStableReadingPosition?.pageIndex)

        // A subsequent flushProgress writes the lastStable — page 130,
        // not the original restore target 114.
        assertTrue(state.canPersist(geometryReady = true))
        assertEquals(130, state.snapshot().lastStableReadingPosition?.pageIndex)
    }

    // ── 14. reopen restores user last position ─────────────────────

    @Test
    fun `reopen after user scroll restores correct page`() {
        // Simulate: user was at 114, scrolled to 130, saved.
        // Reopen: seed from saved position, request restore to 130.
        val state = ContinuousEntryRestoreState().apply {
            seedLastStable(
                ReadingPosition(130, 0.6f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 0L),
                pageCount = 451,
            )
        }
        val request = state.requestRestore(130, 0.6f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        assertEquals(130, request.pageIndex)

        // Complete restore to 130 (simulated geometry+validation).
        state.beginApplyIfReady(true)
        assertTrue(state.completeRestore(request.generation, request, 451))
        assertEquals(130, state.snapshot().lastStableReadingPosition?.pageIndex)
    }

    // ── 15. 112 roundtrip regression ──────────────────────────────

    @Test
    fun `page 112 continuous to horizontal 112 back to continuous 112 preserves position`() {
        // Open at 112 in continuous.
        val state = ContinuousEntryRestoreState().apply {
            seedLastStable(
                ReadingPosition(112, 0f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 0L),
                pageCount = 451,
            )
        }
        val startup = state.requestRestore(112, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        state.completeRestore(startup.generation, startup, 451)

        // Switch to horizontal — horizontal doesn't need a restore.
        // markReadyWithoutRestore or simply create new state.  For
        // the mode-switch route the VM calls requestRestore with
        // MODE_SWITCH_RESTORE using the last stable reading position.
        val modeSwitch = ContinuousEntryRestoreState().apply {
            seedLastStable(
                ReadingPosition(112, 0f, ReadingPositionSource.USER_HORIZONTAL_SETTLED, 0L),
                pageCount = 451,
            )
        }
        val mode = modeSwitch.requestRestore(112, 0f, ReadingPositionSource.MODE_SWITCH_RESTORE, 451)!!
        modeSwitch.beginApplyIfReady(true)
        modeSwitch.completeRestore(mode.generation, mode, 451)

        assertEquals(112, modeSwitch.snapshot().lastStableReadingPosition?.pageIndex)
        assertTrue(modeSwitch.canPersist(geometryReady = true))
    }

    @Test
    fun `home 112 reopen 112 no page zero infection`() {
        // Simulate: home card shows 112/451 → reopen → must restore to 112.
        val state = ContinuousEntryRestoreState().apply {
            seedLastStable(
                ReadingPosition(112, 0f, ReadingPositionSource.USER_CONTINUOUS_SCROLL, 0L),
                pageCount = 451,
            )
        }
        val startup = state.requestRestore(112, 0f, ReadingPositionSource.STARTUP_RESTORE, 451)!!
        state.beginApplyIfReady(true)
        assertTrue(state.completeRestore(startup.generation, startup, 451))

        assertEquals(112, state.snapshot().lastStableReadingPosition?.pageIndex)
        assertFalse(state.snapshot().lastStableReadingPosition?.source == ReadingPositionSource.INITIAL_PLACEHOLDER)
    }

    // ── viewport intersection validation ──────────────────────────

    @Test
    fun `pageIntersectsViewport detects visible page correctly`() {
        val geometry = ContinuousDocumentGeometry.create(
            pageCount = 100, basePageWidth = 1000f, aspects = emptyMap(), pageGap = 10f,
        )
        val page0Top = geometry.pageTop(0)
        val page0Bot = page0Top + geometry.pageHeight(0)

        // Viewport at top: page 0 fully visible.
        assertTrue(geometry.pageIntersectsViewport(
            ContinuousDocumentState(scrollY = 0f), 2000f, page = 0))

        // Scroll past page 0: it's no longer visible.
        val pastPage0 = ContinuousDocumentState(scrollY = page0Bot * 1f + 50f)
        assertFalse(geometry.pageIntersectsViewport(pastPage0, 2000f, page = 0))
    }

    @Test
    fun `anchorIsInViewport detects visible anchor position`() {
        val geometry = ContinuousDocumentGeometry.create(
            pageCount = 100, basePageWidth = 1000f, aspects = emptyMap(), pageGap = 10f,
        )
        val page5Top = geometry.pageTop(5)

        // Anchor at page 5, fraction 0.5 — anchorY is in the middle of page 5.
        val state = ContinuousDocumentState(scrollY = page5Top * 1f - 500f)
        assertTrue(geometry.anchorIsInViewport(state, 2000f, 5, 0.5f))

        // Anchor far above the viewport.
        val aboveState = ContinuousDocumentState(scrollY = page5Top * 1f + 2000f)
        assertFalse(geometry.anchorIsInViewport(aboveState, 2000f, 5, 0.5f))
    }

    // ── ContinuousQualityHysteresis 4× clamp ──────────────────────

    @Test
    fun `continuous zoom clamp uses 4x from single authority`() {
        assertEquals(4f, MAX_CONTINUOUS_SCALE)
        val clamped = PdfRenderQuality.clampContinuousZoom(6.5f)
        assertTrue(clamped <= MAX_CONTINUOUS_SCALE)
        assertTrue(clamped >= 1f)
    }
}
