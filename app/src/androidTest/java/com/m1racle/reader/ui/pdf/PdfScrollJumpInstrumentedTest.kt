package com.m1racle.reader.ui.pdf

import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.core.model.PdfReadingMode
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.pdf.ContinuousGestureCoordinator
import com.m1racle.reader.pdf.ContinuousScrollPhase
import com.m1racle.reader.pdf.PdfReadingPrefs
import com.m1racle.reader.ui.pdf.PdfMotionAspectHarnessActivity.LateAspectTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Native regressions for vertical PDF motion. The motion cases intentionally
 * publish a new page dimension while the shipped pointer/fling transform is
 * active; the real-PDF case verifies that document-wide known aspects survive
 * the bitmap window moving far away and back.
 */
@RunWith(AndroidJUnit4::class)
class PdfScrollJumpInstrumentedTest {

    @get:Rule
    val rule = createAndroidComposeRule<PdfMotionAspectHarnessActivity>()

    @Test
    fun latePreviousPageAspectPreservesTheExplicitRestoredShortPageAnchor() {
        rule.activity.prepareRestoredShortPage()
        rule.waitUntil(10_000) {
            rule.activity.isRestoredShortPageApplied() &&
                rule.activity.capturedPageAspect(1)?.let { abs(it - 431f / 997f) < 0.000001f } == true
        }
        rule.waitForIdle()
        fun centerAnchor() = rule.runOnIdle {
            val frame = requireNotNull(rule.activity.capturePayload())
            frame.geometry.viewportSessionAtAnchor(
                frame.documentState, frame.viewportWidth.toFloat(), frame.viewportHeight.toFloat(), 0L,
            )
        }
        val before = centerAnchor()
        assertEquals(1, before.pageIndex)
        assertEquals(0.5f, before.pageAnchorYFraction, 0.0005f)
        assertEquals(1.414f, requireNotNull(rule.activity.capturedPageAspect(0)), 0.000001f)

        // The viewport top is on page 0, while the explicit reading point is
        // in short page 1. A top-only remap changes the latter's fraction.
        rule.activity.publishPageAspect(0, 1.3f)
        rule.waitUntil(10_000) {
            rule.activity.capturedPageAspect(0)?.let { abs(it - 1.3f) < 0.000001f } == true
        }
        rule.waitForIdle()
        val after = centerAnchor()
        assertEquals(1, after.pageIndex)
        assertEquals(0.5f, after.pageAnchorYFraction, 0.0005f)
    }

    @Test
    fun jitteredTapDoesNotLaunchFlingOrJumpTheReadingPosition() {
        jumpAndAwaitPrimaryPage(1)
        val before = rule.runOnIdle { requireNotNull(rule.activity.capturePayload()).documentState }
        val frameCount = rule.activity.snapshots().size
        rule.onNodeWithTag("r7-continuous-viewport").performTouchInput {
            down(center)
            moveTo(center + Offset(1f, 1f), 30L)
            up()
        }
        rule.waitForIdle()
        rule.waitUntil(10_000) {
            rule.activity.capturePayload()?.documentState?.gestureActive == false &&
                rule.activity.latestPhase() == ContinuousScrollPhase.IDLE
        }
        val after = rule.runOnIdle { requireNotNull(rule.activity.capturePayload()).documentState }
        assertEquals(before.scale, after.scale, 0f)
        assertEquals("one-pixel tap jitter must not jump horizontally", before.scrollX, after.scrollX, 1.5f)
        assertEquals("one-pixel tap jitter must not jump vertically", before.scrollY, after.scrollY, 1.5f)
        assertTrue(
            "tap jitter must never start the platform fling",
            rule.activity.snapshots().drop(frameCount).none {
                it.snapshot.phase == ContinuousScrollPhase.FLINGING
            },
        )
    }

    @Test
    fun lateAspectDuringDragDoesNotJumpOrReboundAndKeepsTopReadingLine() {
        jumpAndAwaitPrimaryPage(1)
        rule.activity.armLateAspectInjection(
            trigger = LateAspectTrigger.DRAGGING,
            pageIndex = 0,
            aspect = 0.55f,
            minimumMotionFrames = 3,
        )

        val node = rule.onNodeWithTag("r7-continuous-viewport")
        val stepPx = with(rule.density) { 8.dp.toPx() }
        node.performTouchInput { down(center) }
        repeat(5) {
            node.performTouchInput { moveBy(Offset(0f, -stepPx)) }
            rule.waitForIdle()
        }
        rule.waitUntil(10_000) {
            rule.activity.injectedPhase() == ContinuousScrollPhase.DRAGGING
        }
        repeat(5) {
            node.performTouchInput { moveBy(Offset(0f, -stepPx)) }
            rule.waitForIdle()
        }

        assertContinuousMotionWithoutRebound(
            frames = rule.activity.snapshots(),
            injectionFrame = rule.activity.injectionFrameOrdinal(),
            expectedPhase = ContinuousScrollPhase.DRAGGING,
            expectedPrimaryPage = 1,
        )
        assertFrozenBeforeLateAspectCommit()
        val motionFinalAnchor = rule.runOnIdle {
            requireNotNull(rule.activity.captureTopAnchor())
        }

        // A long quiet period before lift keeps this path drag-only and prevents
        // an unrelated fling from continuing after the geometric epoch commits.
        node.performTouchInput {
            advanceEventTime(200)
            up()
        }
        awaitLateAspectCommit(pageIndex = 0, aspect = 0.55f)

        val committedAnchor = rule.runOnIdle {
            requireNotNull(rule.activity.captureTopAnchor())
        }
        assertSameReadingLine(motionFinalAnchor, committedAnchor)
        assertEquals(1, rule.activity.settledPage())
    }

    @Test
    fun lateAspectDuringFlingDoesNotJumpOrReboundAndKeepsTopReadingLine() {
        jumpAndAwaitPrimaryPage(1)
        rule.activity.armLateAspectInjection(
            trigger = LateAspectTrigger.FLINGING,
            pageIndex = 0,
            aspect = 0.55f,
            minimumMotionFrames = 3,
        )

        val node = rule.onNodeWithTag("r7-continuous-viewport")
        val interruptionPoint = viewportCenterOnScreen()
        node.performTouchInput {
            // Keep the six measured fling frames on page 1, so a page switch
            // cannot reset the content-anchor baseline under this assertion.
            swipe(
                start = center,
                end = Offset(center.x, center.y - height * 0.24f),
                durationMillis = 150,
            )
        }

        rule.waitUntil(10_000) {
            rule.activity.injectedPhase() == ContinuousScrollPhase.FLINGING
        }
        rule.waitUntil(10_000) {
            val injectedFrame = rule.activity.injectionFrameOrdinal()
            rule.activity.snapshots().withIndex().count { (index, timed) ->
                index > injectedFrame &&
                    timed.snapshot.phase == ContinuousScrollPhase.FLINGING &&
                    timed.snapshot.contentAnchorDeltaY != 0f
            } >= 3
        }

        assertContinuousMotionWithoutRebound(
            frames = rule.activity.snapshots(),
            injectionFrame = rule.activity.injectionFrameOrdinal(),
            expectedPhase = ContinuousScrollPhase.FLINGING,
            expectedPrimaryPage = 1,
        )

        // Inject directly through Android while the fling is live. Fetching a
        // Compose node here can wait for animation idleness before sending DOWN,
        // which would test an already-finished fling instead of interruption.
        val interruptionDownTime = SystemClock.uptimeMillis()
        injectPointer(MotionEvent.ACTION_DOWN, interruptionPoint, interruptionDownTime)
        val motionFinalAnchor = try {
            rule.waitUntil(10_000) {
                rule.activity.latestPhase() == ContinuousScrollPhase.DRAGGING
            }
            rule.waitForIdle()
            assertFrozenBeforeLateAspectCommit()
            rule.runOnIdle {
                requireNotNull(rule.activity.captureTopAnchor())
            }
        } finally {
            SystemClock.sleep(200)
            injectPointer(MotionEvent.ACTION_UP, interruptionPoint, interruptionDownTime)
        }
        awaitLateAspectCommit(pageIndex = 0, aspect = 0.55f)

        val committedAnchor = rule.runOnIdle {
            requireNotNull(rule.activity.captureTopAnchor())
        }
        assertSameReadingLine(motionFinalAnchor, committedAnchor)
    }

    private fun viewportCenterOnScreen(): Offset {
        val bounds = rule.onNodeWithTag("r7-continuous-viewport")
            .fetchSemanticsNode().boundsInRoot
        val origin = IntArray(2)
        rule.runOnUiThread { rule.activity.window.decorView.getLocationOnScreen(origin) }
        return bounds.center + Offset(origin[0].toFloat(), origin[1].toFloat())
    }

    private fun injectPointer(action: Int, point: Offset, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            assertTrue(
                "Android must accept the native fling interruption event",
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true),
            )
        } finally {
            event.recycle()
        }
    }

    private fun jumpAndAwaitPrimaryPage(pageIndex: Int) {
        rule.activity.requestPageJump(pageIndex)
        rule.waitUntil(10_000) {
            rule.activity.snapshots().any {
                it.snapshot.primaryPage == pageIndex &&
                    it.snapshot.phase == ContinuousScrollPhase.IDLE
            }
        }
        rule.waitForIdle()
    }

    private fun assertContinuousMotionWithoutRebound(
        frames: List<PdfMotionAspectHarnessActivity.TimedSnapshot>,
        injectionFrame: Int,
        expectedPhase: ContinuousScrollPhase,
        expectedPrimaryPage: Int,
    ) {
        val motion = frames.withIndex().filter {
            it.value.snapshot.phase == expectedPhase &&
                it.value.snapshot.primaryPage == expectedPrimaryPage &&
                it.value.snapshot.contentAnchorDeltaY != 0f
        }
        val beforeInjection = motion.filter { it.index < injectionFrame }
        val afterInjection = motion.filter { it.index > injectionFrame }
        assertTrue("injection must follow measured motion", beforeInjection.isNotEmpty())
        assertTrue("motion must continue after the late aspect", afterInjection.size >= 3)

        val baselineMax = beforeInjection.maxOf {
            abs(it.value.snapshot.contentAnchorDeltaY)
        }
        assertTrue("baseline frame must have movement", baselineMax > 0f)
        val continuityBound = baselineMax * 1.5f + 1f

        var previous: PdfMotionAspectHarnessActivity.TimedSnapshot? = null
        for ((index, timed) in frames.withIndex()) {
            val snapshot = timed.snapshot
            if (snapshot.primaryPage != expectedPrimaryPage) {
                previous = null
                continue
            }
            if (snapshot.phase == expectedPhase && snapshot.contentAnchorDeltaY != 0f) {
                assertTrue(
                    "upward motion reversed at frame $index: delta=${snapshot.contentAnchorDeltaY}",
                    snapshot.contentAnchorDeltaY <= 0f,
                )
                assertTrue(
                    "same-page screen-top discontinuity at frame $index: " +
                        "delta=${snapshot.contentAnchorDeltaY}, bound=$continuityBound",
                    abs(snapshot.contentAnchorDeltaY) <= continuityBound,
                )
                previous?.let { before ->
                    assertTrue(
                        "same-page screen top moved backward at frame $index: " +
                            "${before.snapshot.contentAnchorScreenY} -> " +
                            snapshot.contentAnchorScreenY,
                        snapshot.contentAnchorScreenY <= before.snapshot.contentAnchorScreenY,
                    )
                }
            }
            previous = timed
        }
    }

    private fun assertFrozenBeforeLateAspectCommit() {
        val frozenAspect = requireNotNull(rule.activity.capturedPageAspect(0))
        assertEquals(1.414f, frozenAspect, 0.000001f)
    }

    private fun awaitLateAspectCommit(pageIndex: Int, aspect: Float) {
        rule.waitUntil(10_000) {
            rule.activity.capturePayload()?.documentState?.gestureActive == false &&
                rule.activity.latestPhase() == ContinuousScrollPhase.IDLE &&
                rule.activity.capturedPageAspect(pageIndex)?.let {
                    abs(it - aspect) <= 0.000001f
                } == true
        }
        rule.waitForIdle()
    }

    private fun assertSameReadingLine(
        motionFinal: com.m1racle.reader.core.model.PdfViewportSessionState,
        committed: com.m1racle.reader.core.model.PdfViewportSessionState,
    ) {
        assertEquals(
            "late geometry commit must not change the page at the top reading line",
            motionFinal.pageIndex,
            committed.pageIndex,
        )
        assertEquals(0f, committed.viewportAnchorYFraction, 0f)
        assertEquals(
            "late geometry commit must preserve the in-page top reading line",
            motionFinal.pageAnchorYFraction,
            committed.pageAnchorYFraction,
            0.0005f,
        )
    }
}

@RunWith(AndroidJUnit4::class)
class PdfKnownAspectWindowRetentionInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nativeAspectSurvivesMovingBeyondBitmapWindowAndReturning() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val id = "pdf-known-aspect-${UUID.randomUUID()}"
        val file = File(app.cacheDir, "$id.pdf")
        val dimensions = buildList {
            add(900 to 400)
            repeat(22) { index -> add(if (index % 2 == 0) 600 to 840 else 840 to 600) }
            add(400 to 1_000)
        }
        val document = PdfDocument()
        try {
            dimensions.forEachIndexed { index, (width, height) ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(width, height, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(
                    "Known aspect page ${index + 1}",
                    24f,
                    42f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 16f
                    },
                )
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }

        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        var model by mutableStateOf<PdfViewerViewModel?>(null)
        var coordinator = ContinuousGestureCoordinator { }
        val repository = DocumentRepository(
            app,
            db,
            db.documentDao(),
            db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(),
            SearchRepository(db.searchIndexDao()),
        )
        val prefs = PdfReadingPrefs(app)
        prefs.setMode(id, PdfReadingMode.VERTICAL_CONTINUOUS)
        runBlocking {
            db.documentDao().upsert(
                DocumentEntity(
                    id = id,
                    displayName = "Known aspects.pdf",
                    uri = Uri.fromFile(file).toString(),
                    mimeType = "application/pdf",
                    fileSize = file.length(),
                    createdAt = 0,
                    lastOpenedAt = 0,
                    lastScrollIndex = 0,
                    lastScrollOffset = 0,
                    readingProgress = 0f,
                    title = null,
                    headingCount = 0,
                    documentType = "PDF",
                    pageCount = dimensions.size,
                ),
            )
        }
        fun createModel(): PdfViewerViewModel =
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PdfViewerViewModel(app, id, repository, applicationScope) as T
            })[PdfViewerViewModel::class.java]

        try {
            rule.setContent {
                val current = model
                if (current != null) {
                    val state by current.uiState.collectAsState()
                    val pageJump by current.pageJump.collectAsState()
                    val ready = state as? PdfViewerUiState.Ready
                    if (ready != null) {
                        MaterialTheme {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                ContinuousDocumentViewport(
                                    state = ready,
                                    pageJump = pageJump,
                                    onPageJumpConsumed = current::onContinuousPageJumpConsumed,
                                    onViewportWidthPx = current::onViewportWidth,
                                    onContinuousGeometryReady = current::onContinuousGeometryReady,
                                    onContinuousEntryRestoreApplied = current::onContinuousEntryRestoreApplied,
                                    onDocumentStateSettled = current::commitContinuousDocumentState,
                                    onZoomGestureActive = current::setZoomGestureActive,
                                    onPageSettled = current::onVerticalPageSettled,
                                    onViewportChanged = current::onContinuousViewportChanged,
                                    onEnsureAround = current::ensurePagesAround,
                                    onRetryPage = current::ensurePagesAround,
                                    onViewportSessionSettled = current::commitViewportSession,
                                    onViewportSessionPreview = current::previewViewportSession,
                                    gestureCoordinatorOverride = coordinator,
                                )
                            }
                        }
                    }
                }
            }
            rule.runOnIdle { model = createModel() }

            val firstAspect = 400f / 900f
            fun awaitAspect(page: Int, aspect: Float) {
                rule.waitUntil(10_000) {
                    val ready = model?.uiState?.value as? PdfViewerUiState.Ready
                    ready?.pageAspects?.get(page)?.let { abs(it - aspect) < 0.000001f } == true
                }
            }
            awaitAspect(0, firstAspect)
            rule.runOnIdle { assertTrue(model!!.jumpToHumanPage(dimensions.size)) }
            awaitAspect(dimensions.lastIndex, 1_000f / 400f)

            rule.runOnIdle {
                val ready = model!!.uiState.value as PdfViewerUiState.Ready
                assertEquals(firstAspect, ready.pageAspects[0]!!, 0f)
                assertNotNull(ready.pageBitmaps[23])
                val frame = requireNotNull(coordinator.snapshots.capture()).payload
                assertEquals(
                    firstAspect,
                    frame.geometry.pageHeight(0) / frame.geometry.basePageWidth,
                    0.000001f,
                )
            }

            rule.runOnIdle { assertTrue(model!!.jumpToHumanPage(1)) }
            rule.waitUntil(10_000) {
                val ready = model?.uiState?.value as? PdfViewerUiState.Ready
                ready?.pageIndex == 0 &&
                    coordinator.snapshots.capture()?.payload?.geometry?.pageCount == dimensions.size
            }
            rule.runOnIdle {
                val frame = requireNotNull(coordinator.snapshots.capture()).payload
                val geometry = frame.geometry
                assertEquals(
                    firstAspect,
                    geometry.pageHeight(0) / geometry.basePageWidth,
                    0.000001f,
                )
            }
        } finally {
            // Dispose even when a failed idle assertion detected a feedback
            // loop, so cleanup cannot hide the original failure.
            rule.runOnUiThread { model = null }
            rule.waitForIdle()
            store.clear()
            applicationScope.cancel()
            runBlocking {
                db.close()
                file.delete()
            }
        }
    }
}
