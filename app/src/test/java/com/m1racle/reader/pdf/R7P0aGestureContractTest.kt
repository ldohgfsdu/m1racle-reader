package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification: R7_PURE_LOGIC. No claim about Compose/runtime input delivery. */
class R7P0aGestureContractTest {
    private val geometry = ContinuousDocumentGeometry.create(
        pageCount = 30,
        basePageWidth = 1000f,
        aspects = emptyMap(),
        pageGap = 20f,
    )

    private fun payload(
        pages: Set<Int>,
        primary: Int = pages.firstOrNull() ?: 0,
        scale: Float = 1f,
    ): ContinuousGestureSnapshotPayload {
        val state = ContinuousDocumentState(scale = scale, scrollY = primary * 100f)
        return ContinuousGestureSnapshotPayload(
            documentId = "r7-test",
            composedPages = pages,
            visiblePages = pages,
            viewportPage = primary,
            geometry = geometry,
            documentState = state,
            viewportWidth = 1000,
            viewportHeight = 1600,
            displayedBitmapIds = pages.associateWith { it + 100 },
            pageBounds = pages.associateWith { geometry.pageBoundsInViewport(state, it) },
        )
    }

    @Test fun `updated snapshot capture always returns latest page collection`() {
        val registry = ContinuousGestureSnapshotRegistry()
        val first = registry.publish(payload(setOf(1, 2), primary = 1))
        val latest = registry.publish(payload(setOf(18, 19), primary = 18))

        assertTrue(latest.version > first.version)
        assertEquals(setOf(18, 19), registry.capture()!!.payload.composedPages)
        assertEquals(18, registry.capture()!!.payload.viewportPage)
    }

    @Test fun `transaction begins with latest published snapshot`() {
        val coordinator = ContinuousGestureCoordinator { }
        coordinator.snapshots.publish(payload(setOf(2, 3), primary = 2))
        coordinator.snapshots.publish(payload(setOf(20, 21), primary = 20))

        val capture = coordinator.beginCapture()!!
        val transaction = ContinuousInteractionReducer.pinchStart(
            ContinuousInteractionTransaction(),
            capture.snapshot.payload.composedPages,
            capture.snapshot.payload.displayedBitmapIds,
            capture.snapshot.payload.documentState,
        )

        assertEquals(setOf(20, 21), transaction.stableVisiblePages)
        assertEquals(mapOf(20 to 120, 21 to 121), transaction.stableDisplayedBitmapIds)
    }

    @Test fun `normal settle emits false exactly once and commits once`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        coordinator.snapshots.publish(payload(setOf(4, 5)))
        val capture = coordinator.beginCapture()!!
        val finalState = ContinuousDocumentState(scale = 2.75f, scrollX = 90f, scrollY = 850f)
        val commits = mutableListOf<ContinuousDocumentState>()

        coordinator.finishAndCommit(capture.generation, finalState, commits::add)
        coordinator.finishAndCommit(capture.generation, ContinuousDocumentState(), commits::add)

        assertEquals(listOf(true, false), events)
        assertEquals(listOf(finalState), commits)
    }

    @Test fun `rapid repinch invalidates old settle without toggling active false`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        coordinator.snapshots.publish(payload(setOf(6, 7)))
        val first = coordinator.beginCapture()!!
        val second = coordinator.beginCapture()!!

        assertFalse(coordinator.finish(first.generation))
        assertEquals(listOf(true), events)
        assertTrue(coordinator.finish(second.generation))
        assertEquals(listOf(true, false), events)
    }

    @Test fun `dispose document change and repeated cleanup restore false once`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        coordinator.snapshots.publish(payload(setOf(8, 9)))
        coordinator.beginCapture()

        assertTrue(coordinator.cleanup())
        val cleanedGeneration = coordinator.currentGeneration
        assertFalse(coordinator.cleanup())
        assertEquals(listOf(true, false), events)
        assertEquals(cleanedGeneration, coordinator.currentGeneration)
    }

    @Test fun `exception cleanup cannot leave gesture active`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        coordinator.snapshots.publish(payload(setOf(10, 11)))

        try {
            coordinator.beginCapture()
            error("synthetic gesture failure")
        } catch (_: IllegalStateException) {
            // Production pointer coroutine uses the same finally cleanup contract.
        } finally {
            coordinator.cleanup()
        }

        assertFalse(coordinator.isActive)
        assertEquals(listOf(true, false), events)
    }

    @Test fun `unusable snapshot is rejected without activating VM gesture state`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(onGestureActiveChanged = events::add)
        coordinator.snapshots.publish(
            payload(emptySet()).copy(viewportWidth = 0, viewportHeight = 0),
        )

        assertEquals(null, coordinator.beginCapture())
        assertTrue(events.isEmpty())
    }

    @Test fun `stable session restore and reset use one through four scale contract`() {
        val session = ContinuousDocumentSession()
        val committed = session.commit(
            ContinuousDocumentState(scale = 9f, scrollX = 120f, scrollY = 900f, gestureActive = true),
        )

        assertEquals(MAX_CONTINUOUS_SCALE, committed.scale, 0f)
        assertFalse(committed.gestureActive)
        assertEquals(MAX_CONTINUOUS_SCALE, PdfRenderQuality.clampContinuousZoom(9f), 0f)
        assertEquals(QualityTier.HIGH, PdfRenderQuality.resolveContinuousTier(9f, QualityTier.PREVIEW))
        assertEquals(QualityTier.HIGH, ContinuousQualityHysteresis.resolve(committed.scale, QualityTier.PREVIEW))
        assertEquals(committed, session.stableState)
        assertEquals(ContinuousDocumentState(), session.reset())
    }

    @Test fun `mode round trip preserves the shared document session scale`() {
        val session = ContinuousDocumentSession()
        val verticalState = session.commit(
            ContinuousDocumentState(scale = 2.4f, scrollX = 90f, scrollY = 1_200f),
            pageIndex = 12,
        )

        val horizontal = ContinuousModeZoomContract.horizontalEntry(session.stableState)
        assertEquals(verticalState.scale, horizontal.scale, 0f)
        assertEquals(QualityTier.HIGH, horizontal.qualityTier)

        val restored = ContinuousModeZoomContract.verticalEntry(session.stableState)
        assertEquals(verticalState.scale, restored.scale, 0f)
        assertEquals(QualityTier.HIGH, restored.qualityTier)
        assertEquals(verticalState, session.stableState)
        assertEquals(12, session.stablePageIndex)
    }

    @Test fun `snapshot from another document owner is rejected without activating`() {
        val events = mutableListOf<Boolean>()
        val coordinator = ContinuousGestureCoordinator(
            ownerDocumentId = "document-a",
            onGestureActiveChanged = events::add,
        )
        coordinator.snapshots.publish(payload(setOf(1, 2)).copy(documentId = "document-b"))

        assertEquals(null, coordinator.beginCapture())
        assertTrue(events.isEmpty())
    }
}
