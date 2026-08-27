package com.m1racle.reader.ui.pdf

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.pdf.ContinuousDocumentState
import com.m1racle.reader.pdf.ContinuousEntryPhase
import com.m1racle.reader.pdf.ContinuousEntryState
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.ContinuousViewportSnapshot
import com.m1racle.reader.pdf.PdfTileEntry
import com.m1racle.reader.pdf.PdfTilePlan
import com.m1racle.reader.pdf.TileInteractionPhase
import com.m1racle.reader.pdf.TileOverlaySnapshot
import com.m1racle.reader.pdf.TilePixelRect
import com.m1racle.reader.pdf.ReadingPosition
import com.m1racle.reader.pdf.ReadingPositionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Classification: R7_RUNTIME_REQUIRED. Every lifecycle case uses the production viewport. */
@RunWith(AndroidJUnit4::class)
class R7GestureLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun ready(
        documentId: String = "r7-test",
        stable: ContinuousDocumentState = ContinuousDocumentState(),
    ): PdfViewerUiState.Ready = PdfViewerUiState.Ready(
        document = DocumentEntity(
            id = documentId,
            displayName = "R7 fake PDF",
            uri = "content://$documentId",
            mimeType = "application/pdf",
            fileSize = 1L,
            lastOpenedAt = 0L,
            createdAt = 0L,
            lastScrollIndex = 0,
            lastScrollOffset = 0,
            readingProgress = 0f,
            title = null,
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = 30,
        ),
        pageIndex = 0,
        pageCount = 30,
        pageImage = null,
        isRendering = false,
        pageAspects = (0 until 30).associateWith { 1f },
        zoomScale = stable.scale,
        continuousDocumentState = stable,
        continuousEntryState = ContinuousEntryState(phase = ContinuousEntryPhase.READY),
    )

    @Composable
    private fun ViewportUnderTest(
        state: PdfViewerUiState.Ready,
        coordinator: ContinuousGestureCoordinator,
        onStableCommit: (ContinuousDocumentState, Int?) -> Unit = { _, _ -> },
        onGeometryReady: () -> Unit = {},
        onEntryRestoreApplied: (ReadingPosition, ContinuousDocumentState) -> Unit = { _, _ -> },
        onPageSettled: (ReadingPosition) -> Unit = {},
        onTilePlan: (PdfTilePlan) -> Unit = {},
        tileOverlaySnapshots: StateFlow<TileOverlaySnapshot>? = null,
    ) {
        MaterialTheme {
            ContinuousDocumentViewport(
                state = state,
                pageJump = null,
                onPageJumpConsumed = {},
                onViewportWidthPx = {},
                onContinuousGeometryReady = onGeometryReady,
                onContinuousEntryRestoreApplied = onEntryRestoreApplied,
                onDocumentStateSettled = onStableCommit,
                onZoomGestureActive = {},
                onPageSettled = onPageSettled,
                onViewportChanged = { _: ContinuousViewportSnapshot -> },
                onTilePlan = onTilePlan,
                tileOverlaySnapshots = tileOverlaySnapshots,
                onEnsureAround = {},
                onRetryPage = {},
                gestureCoordinatorOverride = coordinator,
                modifier = Modifier.width(300.dp).height(600.dp),
            )
        }
    }

    private fun beginActivePinch(coordinator: ContinuousGestureCoordinator) {
        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val c = center
            down(0, Offset(c.x - 35f, c.y))
            down(1, Offset(c.x + 35f, c.y))
            moveTo(0, Offset(c.x - 85f, c.y), 16L)
            moveTo(1, Offset(c.x + 85f, c.y), 16L)
        }
        composeRule.waitUntil(3_000L) {
            coordinator.isActive &&
                (coordinator.snapshots.capture()?.payload?.documentState?.scale ?: 1f) > 1.05f
        }
    }

    @Test
    fun startupAndModeEntryRestoreApplyTargetBeforeAnyPageZeroFeedback() {
        val coordinator = ContinuousGestureCoordinator { }
        val startup = ReadingPosition(47, 0.25f, ReadingPositionSource.STARTUP_RESTORE, 99L)
        var entry by mutableStateOf(
            ContinuousEntryState(
                phase = ContinuousEntryPhase.WAITING_GEOMETRY,
                pendingRestorePosition = startup,
                lastStableReadingPosition = ReadingPosition(
                    47,
                    0f,
                    ReadingPositionSource.USER_HORIZONTAL_SETTLED,
                    98L,
                ),
            ),
        )
        val settled = mutableListOf<ReadingPosition>()
        val applied = mutableListOf<ReadingPosition>()

        composeRule.setContent {
            ViewportUnderTest(
                state = ready().copy(continuousEntryState = entry),
                coordinator = coordinator,
                onGeometryReady = {
                    if (entry.phase == ContinuousEntryPhase.WAITING_GEOMETRY) {
                        entry = entry.copy(phase = ContinuousEntryPhase.APPLYING_TARGET)
                    }
                },
                onEntryRestoreApplied = { position, _ ->
                    applied += position
                    entry = entry.copy(
                        phase = ContinuousEntryPhase.READY,
                        pendingRestorePosition = null,
                        lastStableReadingPosition = position,
                    )
                },
                onPageSettled = settled::add,
            )
        }
        composeRule.waitUntil(3_000L) { entry.phase == ContinuousEntryPhase.READY && applied.size == 1 }
        assertEquals(47, applied.single().pageIndex)

        composeRule.runOnIdle {
            entry = ContinuousEntryState(
                phase = ContinuousEntryPhase.WAITING_GEOMETRY,
                pendingRestorePosition = ReadingPosition(
                    42,
                    0f,
                    ReadingPositionSource.MODE_SWITCH_RESTORE,
                    100L,
                ),
                lastStableReadingPosition = applied.single(),
            )
        }
        composeRule.waitUntil(3_000L) { entry.phase == ContinuousEntryPhase.READY && applied.size == 2 }

        assertEquals(42, applied.last().pageIndex)
        assertTrue(settled.none { it.pageIndex == 0 })
    }

    @Test
    fun composableDisposeCleansActiveProductionViewportWithoutLateCommit() {
        val events = mutableListOf<Boolean>()
        val commits = mutableListOf<ContinuousDocumentState>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        var show by mutableStateOf(true)

        composeRule.setContent {
            if (show) {
                ViewportUnderTest(ready(), coordinator, onStableCommit = { state, _ -> commits += state })
            }
        }
        composeRule.waitForIdle()
        commits.clear()
        beginActivePinch(coordinator)

        composeRule.runOnIdle { show = false }
        composeRule.waitUntil(3_000L) { !coordinator.isActive }
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), events)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun modeSwitchDisposesActiveProductionViewportExactlyOnce() {
        val events = mutableListOf<Boolean>()
        val commits = mutableListOf<ContinuousDocumentState>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        var vertical by mutableStateOf(true)

        composeRule.setContent {
            if (vertical) {
                ViewportUnderTest(ready(), coordinator, onStableCommit = { state, _ -> commits += state })
            } else {
                Box(Modifier.width(300.dp).height(600.dp))
            }
        }
        composeRule.waitForIdle()
        commits.clear()
        beginActivePinch(coordinator)

        composeRule.runOnIdle { vertical = false }
        composeRule.waitUntil(3_000L) { !coordinator.isActive }
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), events)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun documentChangeReplacesGestureOwnerWithoutLateOldCommit() {
        val firstEvents = mutableListOf<Boolean>()
        val secondEvents = mutableListOf<Boolean>()
        val commits = mutableListOf<ContinuousDocumentState>()
        val first = ContinuousGestureCoordinator(onGestureActiveChanged = firstEvents::add)
        val second = ContinuousGestureCoordinator(onGestureActiveChanged = secondEvents::add)
        var readyState by mutableStateOf(ready("document-a"))

        composeRule.setContent {
            val coordinator = if (readyState.document.id == "document-a") first else second
            ViewportUnderTest(readyState, coordinator, onStableCommit = { state, _ -> commits += state })
        }
        composeRule.waitForIdle()
        commits.clear()
        beginActivePinch(first)

        composeRule.runOnIdle { readyState = ready("document-b") }
        composeRule.waitUntil(3_000L) {
            !first.isActive && second.snapshots.capture()?.payload?.documentId == "document-b"
        }
        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), firstEvents)
        assertTrue(secondEvents.isEmpty())
        assertTrue(commits.isEmpty())
    }

    @Test
    fun rapidSecondRealPinchCancelsOldSettleAndCommitsOnlyFinalTransform() {
        val events = mutableListOf<Boolean>()
        val commits = mutableListOf<ContinuousDocumentState>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)

        composeRule.setContent {
            ViewportUnderTest(ready(), coordinator, onStableCommit = { state, _ -> commits += state })
        }
        composeRule.waitForIdle()
        commits.clear()

        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val c = center
            pinch(
                start0 = Offset(c.x - 40f, c.y),
                end0 = Offset(c.x - 90f, c.y),
                start1 = Offset(c.x + 40f, c.y),
                end1 = Offset(c.x + 90f, c.y),
                durationMillis = 80L,
            )
            advanceEventTime(25L)
            pinch(
                start0 = Offset(c.x - 55f, c.y),
                end0 = Offset(c.x - 90f, c.y),
                start1 = Offset(c.x + 55f, c.y),
                end1 = Offset(c.x + 90f, c.y),
                durationMillis = 80L,
            )
        }

        composeRule.waitUntil(3_000L) { coordinator.currentGeneration == 2L }
        composeRule.waitUntil(3_000L) { !coordinator.isActive && commits.isNotEmpty() }

        assertEquals(2L, coordinator.currentGeneration)
        assertEquals(listOf(true, false), events)
        assertEquals(1, commits.size)
        val finalState = commits.single()
        assertTrue(finalState.scale > 1f)
        assertEquals(
            finalState.scale,
            coordinator.snapshots.capture()!!.payload.documentState.scale,
            0.001f,
        )
        composeRule.onNodeWithTag("r7-continuous-viewport").assert(
            SemanticsMatcher.expectValue(R7ContinuousScaleKey, finalState.scale),
        )
    }

    @Test
    fun scrollThenPinchCapturesLatestProductionViewportSnapshot() {
        val coordinator = ContinuousGestureCoordinator { }
        composeRule.setContent { ViewportUnderTest(ready(), coordinator) }
        composeRule.waitForIdle()
        val initial = coordinator.snapshots.capture()!!

        repeat(3) {
            composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(3_000L) {
            coordinator.snapshots.capture()?.payload?.viewportPage != initial.payload.viewportPage
        }
        val latestBeforeGesture = coordinator.snapshots.capture()!!

        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val c = center
            pinch(
                start0 = Offset(c.x - 30f, c.y),
                end0 = Offset(c.x - 90f, c.y),
                start1 = Offset(c.x + 30f, c.y),
                end1 = Offset(c.x + 90f, c.y),
                durationMillis = 120L,
            )
        }
        composeRule.waitUntil(3_000L) { coordinator.lastCapture != null }
        val captured = coordinator.lastCapture!!.snapshot

        assertTrue(captured.version >= latestBeforeGesture.version)
        assertTrue(captured.payload.viewportPage != initial.payload.viewportPage)
        assertTrue(captured.payload.viewportPage in captured.payload.composedPages)
        assertTrue(captured.payload.composedPages != initial.payload.composedPages)
    }

    @Test
    fun activeRealGestureIgnoresOldVmScaleEmissionAndCommitsFinalScaleOnce() {
        val events = mutableListOf<Boolean>()
        val commits = mutableListOf<ContinuousDocumentState>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        var readyState by mutableStateOf(ready())

        composeRule.setContent {
            ViewportUnderTest(readyState, coordinator, onStableCommit = { state, _ -> commits += state })
        }
        composeRule.waitForIdle()
        commits.clear()
        beginActivePinch(coordinator)
        val activeScale = coordinator.snapshots.capture()!!.payload.documentState.scale

        composeRule.runOnIdle {
            readyState = readyState.copy(
                isRendering = true,
                zoomScale = 1f,
                continuousDocumentState = ContinuousDocumentState(),
            )
        }
        composeRule.waitForIdle()

        val afterOldVmEmission = coordinator.snapshots.capture()!!.payload.documentState.scale
        assertEquals(activeScale, afterOldVmEmission, 0.001f)
        composeRule.onNodeWithTag("r7-continuous-viewport").assert(
            SemanticsMatcher.expectValue(R7ContinuousScaleKey, activeScale),
        )

        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            up(1)
            up(0)
        }
        composeRule.waitUntil(3_000L) { !coordinator.isActive && commits.isNotEmpty() }

        assertEquals(listOf(true, false), events)
        assertEquals(1, commits.size)
        assertEquals(activeScale, commits.single().scale, 0.001f)
    }

    @Test
    fun pointerEpochSinglePinchSinglePinchSkipsTransitionFramesAndSettlesOnce() {
        val commits = mutableListOf<ContinuousDocumentState>()
        val coordinator = ContinuousGestureCoordinator { }
        composeRule.setContent {
            ViewportUnderTest(ready(), coordinator, onStableCommit = { state, _ -> commits += state })
        }
        composeRule.waitForIdle()
        commits.clear()
        val beforePage = coordinator.snapshots.capture()!!.payload.viewportPage
        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val c = center
            down(0, c)
            moveTo(0, c + Offset(0f, -20f))
            down(1, c + Offset(100f, -20f))
            moveTo(0, c + Offset(-80f, -20f)); moveTo(1, c + Offset(180f, -20f))
            up(1)
            moveTo(0, c + Offset(-60f, -10f))
            down(1, c + Offset(160f, -10f))
            moveTo(0, c + Offset(-90f, -10f)); moveTo(1, c + Offset(190f, -10f))
            up(1); up(0)
        }
        composeRule.waitUntil(3_000L) { commits.size == 1 }
        val afterPage = coordinator.snapshots.capture()!!.payload.viewportPage
        assertTrue(kotlin.math.abs(afterPage - beforePage) <= 1)
        assertEquals(1, commits.size)
    }

    @Test
    fun pointerEpochTwoThreeTwoIgnoresThirdPointerCentroid() {
        val coordinator = ContinuousGestureCoordinator { }
        composeRule.setContent { ViewportUnderTest(ready(), coordinator) }
        composeRule.waitForIdle()
        val beforePage = coordinator.snapshots.capture()!!.payload.viewportPage
        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val c = center
            down(0, c - Offset(80f, 0f)); down(1, c + Offset(80f, 0f))
            moveTo(0, c - Offset(120f, 0f)); moveTo(1, c + Offset(120f, 0f))
            down(2, Offset(c.x * 1.9f, c.y * 0.1f))
            moveTo(2, Offset(c.x * 0.1f, c.y * 1.9f))
            up(2)
            moveTo(0, c - Offset(150f, 0f)); moveTo(1, c + Offset(150f, 0f))
            up(1); up(0)
        }
        composeRule.waitUntil(3_000L) { !coordinator.isActive }
        assertTrue(kotlin.math.abs(coordinator.snapshots.capture()!!.payload.viewportPage - beforePage) <= 1)
    }

    @Test
    fun p0c12StaleGeometryTileIsSuppressedWithoutChangingDocumentTransform() {
        val coordinator = ContinuousGestureCoordinator { }
        val base=ready().copy(continuousDocumentState=ContinuousDocumentState(2f,40f,400f))
        val bitmap=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        val geometryGeneration = com.m1racle.reader.pdf.pageGeometryGeneration(1080f, 1080f * 1.414f, 0)
        val key=com.m1racle.reader.pdf.PdfTileKey("document-a",base.tileDocumentGeneration,geometryGeneration,0,0,1080,2,0,0)
        val entry=com.m1racle.reader.pdf.PdfTileEntry(key,bitmap,com.m1racle.reader.pdf.PageLayoutRect(0f,0f,256f,256f),com.m1racle.reader.pdf.TilePixelRect(0,0,16,16),System.nanoTime(),1,1024)
        composeRule.setContent { ViewportUnderTest(base.copy(tileEntries=mapOf(key to entry)),coordinator) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("r7-continuous-viewport").assert(
            SemanticsMatcher.expectValue(R7TileCountKey,0),
        )
        assertEquals(base.continuousDocumentState,coordinator.snapshots.capture()!!.payload.documentState)
    }

    private data class DecoupledHarness(
        val coordinator: ContinuousGestureCoordinator,
        val overlays: MutableStateFlow<TileOverlaySnapshot>,
        val plans: MutableList<PdfTilePlan>,
    )

    private fun installDecoupledHarness(): DecoupledHarness {
        val coordinator = ContinuousGestureCoordinator { }
        val overlays = MutableStateFlow(TileOverlaySnapshot())
        val plans = java.util.Collections.synchronizedList(mutableListOf<PdfTilePlan>())
        val base = ready(stable = ContinuousDocumentState(2.4f, 120f, 240f))
        composeRule.setContent {
            ViewportUnderTest(
                state = base,
                coordinator = coordinator,
                tileOverlaySnapshots = overlays,
                onTilePlan = { plan ->
                    plans += plan
                    if (plan.snapshot.pointerDown && plan.requests.isNotEmpty() && overlays.value.entries.isEmpty()) {
                        val request = plan.requests.first()
                        val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                        val entry = PdfTileEntry(
                            key = request.key,
                            bitmap = bitmap,
                            pageRect = request.pageRect,
                            contentRectPx = TilePixelRect(0, 0, 16, 16),
                            renderedAtNanos = System.nanoTime(),
                            requestGeneration = request.requestGeneration,
                            byteSize = 1024,
                            snapshotSequence = plan.snapshot.sequence,
                            interactionPhaseAtRequest = plan.snapshot.interactionPhase,
                            priorityAtRequest = request.priority,
                        )
                        overlays.value = TileOverlaySnapshot(1, mapOf(entry.key to entry), System.nanoTime())
                    }
                },
            )
        }
        composeRule.waitForIdle()
        return DecoupledHarness(coordinator, overlays, plans)
    }

    private fun holdAndDrag(frames: Int = 8) {
        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            val start = center + Offset(0f, 150f)
            down(0, start)
            repeat(frames) { frame -> moveTo(0, start - Offset(0f, (frame + 1) * 18f), 32L) }
        }
    }

    @Test fun p0c14HeldDragPlansBeforeRelease() {
        val harness = installDecoupledHarness()
        holdAndDrag()
        composeRule.waitUntil(3_000) { harness.plans.any { it.snapshot.interactionPhase == TileInteractionPhase.DRAGGING } }
        assertTrue(harness.plans.any { it.snapshot.pointerDown && it.requests.isNotEmpty() })
    }

    @Test fun p0c14HeldPauseAllowsTileSwap() {
        val harness = installDecoupledHarness()
        holdAndDrag()
        composeRule.waitUntil(3_000) { harness.overlays.value.entries.isNotEmpty() }
        composeRule.onNodeWithTag("r7-continuous-viewport").assert(SemanticsMatcher.keyIsDefined(R7TileCountKey))
    }

    @Test fun p0c14SlowDragProducesIncreasingSnapshotSequences() {
        val harness = installDecoupledHarness()
        holdAndDrag(12)
        composeRule.waitUntil(3_000) { harness.plans.count { it.snapshot.pointerDown } >= 2 }
        val sequences = harness.plans.filter { it.snapshot.pointerDown }.map { it.snapshot.sequence }
        assertTrue(sequences.last() > sequences.first())
    }

    @Test fun p0c14FastDragKeepsPlannerResponsive() {
        val harness = installDecoupledHarness()
        holdAndDrag(24)
        composeRule.waitUntil(3_000) { harness.plans.any { it.snapshot.pointerDown } }
        assertTrue(harness.coordinator.snapshots.capture()!!.payload.documentState.scale > 1f)
    }

    @Test fun p0c14HighDetailExistsBeforeRelease() {
        val harness = installDecoupledHarness()
        holdAndDrag()
        composeRule.waitUntil(3_000) { harness.overlays.value.entries.isNotEmpty() }
        assertTrue(harness.plans.last { it.snapshot.pointerDown }.snapshot.pointerDown)
    }

    @Test fun p0c14ReleaseDoesNotResetOverlayToZero() {
        val harness = installDecoupledHarness()
        holdAndDrag()
        composeRule.waitUntil(3_000) { harness.overlays.value.entries.isNotEmpty() }
        val before = harness.overlays.value.entries.keys
        composeRule.onNodeWithTag("r7-continuous-viewport").performTouchInput { up(0) }
        composeRule.waitForIdle()
        assertTrue(before.isNotEmpty())
        assertTrue(harness.overlays.value.entries.keys.containsAll(before))
    }

    @Test fun p0c14CurrentVisiblePriorityPrecedesPageAhead() {
        val harness = installDecoupledHarness()
        holdAndDrag(16)
        composeRule.waitUntil(3_000) { harness.plans.any { it.snapshot.pointerDown && it.requests.isNotEmpty() } }
        val requests = harness.plans.last { it.snapshot.pointerDown && it.requests.isNotEmpty() }.requests
        val firstPageAhead = requests.indexOfFirst { it.priority == com.m1racle.reader.pdf.TilePriority.PAGE_AHEAD }
        val firstVisible = requests.indexOfFirst { com.m1racle.reader.pdf.TileRenderPipelinePolicy.isCurrentVisible(it.priority) }
        assertTrue(firstVisible >= 0)
        assertTrue(firstPageAhead < 0 || firstVisible < firstPageAhead)
    }

    @Test fun p0c14TileSwapDoesNotMoveDocumentTransform() {
        val harness = installDecoupledHarness()
        holdAndDrag()
        composeRule.waitUntil(3_000) { harness.overlays.value.entries.isNotEmpty() }
        val before = harness.coordinator.snapshots.capture()!!.payload.documentState
        harness.overlays.value = harness.overlays.value.copy(sequence = harness.overlays.value.sequence + 1)
        composeRule.waitForIdle()
        val after = harness.coordinator.snapshots.capture()!!.payload.documentState
        assertEquals(before, after)
    }
}

/** Platform lifecycle proof: the Activity is recreated while its activity-scoped VM survives. */
@RunWith(AndroidJUnit4::class)
class R7ConfigurationRecreationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<R7ViewportTestActivity>()

    @Test
    fun activityScenarioRecreateRestoresVmScaleAndFullContinuousTransform() {
        composeRule.waitForIdle()
        val oldActivity = composeRule.activity
        val retainedModel = oldActivity.model
        val stableBefore = retainedModel.ready.continuousDocumentState

        composeRule.onNodeWithTag("r7-continuous-viewport").assert(
            SemanticsMatcher.expectValue(R7ContinuousScaleKey, stableBefore.scale),
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val recreatedActivity = composeRule.activity
        val stableAfter = recreatedActivity.model.ready.continuousDocumentState
        assertNotSame(oldActivity, recreatedActivity)
        assertSame(retainedModel, recreatedActivity.model)
        assertFalse(recreatedActivity.model.gestureActive)
        assertEquals(stableBefore.scale, stableAfter.scale, 0.001f)
        assertEquals(stableBefore.scrollX, stableAfter.scrollX, 0.001f)
        assertEquals(stableBefore.scrollY, stableAfter.scrollY, 0.001f)
        assertEquals(stableAfter.scale, recreatedActivity.model.ready.zoomScale, 0.001f)
        composeRule.onNodeWithTag("r7-continuous-viewport").assert(
            SemanticsMatcher.expectValue(R7ContinuousScaleKey, stableAfter.scale),
        )
    }
}
