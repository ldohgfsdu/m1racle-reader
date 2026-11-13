package com.m1racle.reader.ui.annotation

import com.m1racle.reader.core.model.DocumentAnchor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.m1racle.reader.core.model.NormalizedRect
import com.m1racle.reader.pdf.PdfSelectableGlyph
import com.m1racle.reader.pdf.PdfSelectableLine
import com.m1racle.reader.pdf.PdfSelectablePage
import com.m1racle.reader.pdf.PdfTextSelectionRange
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfTextHighlightGestureTest {
    @get:Rule val rule=createComposeRule()
    private val saved=mutableListOf<TextSelection.PdfSelection>()
    private val notedSelections=mutableListOf<TextSelection.PdfSelection>()
    private val ownership=mutableListOf<Boolean>()
    private val parentTaps=mutableListOf<Offset>()
    private val unavailable=mutableListOf<Unit>()
    private val page=PdfSelectablePage(0,300f,400f,listOf(
        PdfSelectableLine(listOf(PdfSelectableGlyph("A",NormalizedRect(.1f,.2f,.15f,.05f)),PdfSelectableGlyph("B",NormalizedRect(.25f,.2f,.15f,.05f))),.1f,.4f),
        PdfSelectableLine(listOf(PdfSelectableGlyph("C",NormalizedRect(.1f,.4f,.15f,.05f)),PdfSelectableGlyph("D",NormalizedRect(.25f,.4f,.15f,.05f))),.1f,.4f),
    ))

    private fun Modifier.consumeInitialEvents() = pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

    private fun Modifier.recordParentTaps() = pointerInput(Unit) {
        detectTapGestures(onTap = { parentTaps += it })
    }

    private fun show(
        enabled: Boolean=true,
        items: List<AnnotationOverlayItem> = emptyList(),
        delete: ((Long,Int)->Unit)? = null,
        note: ((AnnotationOverlayItem,Int)->Unit)? = null,
        selectionNote: ((TextSelection.PdfSelection)->Unit)? = null,
        consumeInitial: Boolean = false,
        recordParentTaps: Boolean = false,
        selectablePage: PdfSelectablePage? = page,
        pageScale: Float = 1f,
        loupeSource: PdfSelectionLoupeSource? = null,
        selectionRange: PdfTextSelectionRange? = null,
    ) {
        val selectionSession = PdfTextSelectionSession()
        if (selectionRange != null) selectionSession.setSelection(0, selectionRange)
        rule.setContent {
            Box(
                Modifier
                    .size(300.dp,400.dp)
                    // Mirrors both reading modes: the annotation layer sits inside
                    // the viewport's page-scale transform.
                    .graphicsLayer {
                        scaleX = pageScale
                        scaleY = pageScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .then(if (recordParentTaps) Modifier.recordParentTaps() else Modifier),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .then(if (consumeInitial) Modifier.consumeInitialEvents() else Modifier),
                ) {
                    PdfHighlightLayer(
                        pageIndex = 0,
                        items = items,
                        selectionEnabled = enabled,
                        textPage = selectablePage,
                        pageScale = pageScale,
                        loupeSource = loupeSource,
                        selectionSession = selectionSession,
                        onSelectionUnavailable = { unavailable += Unit },
                        onSelectCompleted = { saved += it },
                        modifier = Modifier.testTag("page"),
                        onDeleteRequested = delete,
                        onNoteRequested = note,
                        onSelectionGestureActiveChanged = { ownership += it },
                        onSelectionNoteRequested = selectionNote,
                    )
                }
            }
        }
    }

    /** On-screen width of a node, i.e. after every parent transform. */
    private fun visualWidthDp(tag: String): Float =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width / rule.density.density

    @Test fun actionBarKeepsScreenSizeWhileThePageIsZoomed() {
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        rule.setContent {
            Box(
                Modifier
                    .size(300.dp, 400.dp)
                    .graphicsLayer {
                        scaleX = scale.floatValue
                        scaleY = scale.floatValue
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    },
            ) {
                PdfHighlightLayer(
                    pageIndex = 0,
                    items = emptyList(),
                    selectionEnabled = true,
                    textPage = page,
                    pageScale = scale.floatValue,
                    onSelectionUnavailable = {},
                    onSelectCompleted = { saved += it },
                    modifier = Modifier.testTag("page"),
                    onSelectionNoteRequested = { notedSelections += it },
                )
            }
        }
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width * .15f, height * .22f)); advanceEventTime(700); up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        val unzoomed = visualWidthDp("pdf-selection-actions")

        rule.runOnIdle { scale.floatValue = 2.5f }
        rule.waitForIdle()
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        val zoomed = visualWidthDp("pdf-selection-actions")

        // The menu must not grow with the page: same on-screen dp at 1x and 2.5x.
        assertEquals(
            "action bar must stay screen-sized under page zoom (1x=$unzoomed, 2.5x=$zoomed)",
            unzoomed,
            zoomed,
            1.0f,
        )
    }
    @Test fun singleCharacterHighlightCanBeDeletedWithoutHittingNoteAction() {
        val rect=NormalizedRect(.1f,.2f,.02f,.04f)
        var deleted=0;var noted=0
        show(items=listOf(AnnotationOverlayItem(7,AnnotationType.HIGHLIGHT,com.m1racle.reader.core.model.DocumentAnchor.PdfPage(0,listOf(rect)),"A")),
            delete={ _,_->deleted++ },note={ _,_->noted++ })
        rule.onNodeWithTag("page").performTouchInput { click(Offset(width*.11f,height*.22f)) }
        rule.onNodeWithTag("page").performTouchInput {
            val (x,y)=PdfHighlightGeometry.badgeCenter(rect,width.toFloat(),height.toFloat(),width/300f*18f,true)
            click(Offset(x,y))
        }
        rule.runOnIdle { assertEquals(1,deleted);assertEquals(0,noted) }
    }
    @Test fun longPressDragKeepsTwoTextLinesUntilHighlightChosen() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.42f));up()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.onNodeWithTag("pdf-selection-handle-anchor").assertExists()
        rule.onNodeWithTag("pdf-selection-handle-focus").assertExists()
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle {
            assertEquals(1,saved.size);assertEquals("AB\nCD",saved.single().excerpt)
            assertEquals(2,saved.single().rects.size)
        }
    }
    @Test fun initialPassConsumptionDoesNotCancelLongPressDrag() {
        show(consumeInitial = true)
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.42f));up()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty());assertEquals(listOf(true,false),ownership) }
        rule.onNodeWithTag("pdf-selection-action-highlight").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals("AB\nCD",saved.single().excerpt) }
    }
    @Test fun releasedSelectionRemainsAndFocusHandleAdjustmentDoesNotSubmit() {
        show(consumeInitial = true)
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }

        rule.onNodeWithTag("page").performTouchInput {
            val range=PdfTextSelectionRange(PdfSelectablePage.Hit(0,0),PdfSelectablePage.Hit(0,0))
            val focus=PdfTextSelectionGeometry.handles(
                page,
                range,
                width.toFloat(),
                height.toFloat(),
                12.5f*rule.density.density,
            ).single { it.handle == PdfSelectionHandle.FOCUS }
            val bRect=page.lines[0].glyphs[1].rect
            val bEndTipX=(bRect.x+bRect.width)*page.width
            down(Offset(focus.x,focus.y));advanceEventTime(50)
            // The END body follows the pointer delta from A's right edge to B's right edge.
            moveTo(Offset(focus.x+bEndTipX-focus.tipX,focus.y));up()
        }
        rule.runOnIdle {
            assertTrue(saved.isEmpty())
            assertEquals(listOf(true,false,true,false),ownership)
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("AB",saved.single().excerpt) }
    }
    @Test fun tappingTheStartHandleDoesNotTurnBIntoAB() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.325f,height*.225f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()

        rule.onNodeWithTag("page").performTouchInput {
            val range=PdfTextSelectionRange(PdfSelectablePage.Hit(0,1),PdfSelectablePage.Hit(0,1))
            val anchor=PdfTextSelectionGeometry.handles(
                page,
                range,
                width.toFloat(),
                height.toFloat(),
                12.5f*rule.density.density,
            ).single { it.handle == PdfSelectionHandle.ANCHOR }
            down(Offset(anchor.x,anchor.y));advanceEventTime(50);up()
        }

        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("B",saved.single().excerpt) }
    }
    @Test fun handleTouchTargetsStay44dpAtTwoAndAHalfTimesZoom() {
        val scale = androidx.compose.runtime.mutableFloatStateOf(1f)
        val session = PdfTextSelectionSession().apply {
            setSelection(0, PdfTextSelectionRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 1)))
        }
        rule.setContent {
            Box(
                Modifier
                    .size(300.dp, 400.dp)
                    .graphicsLayer {
                        scaleX = scale.floatValue
                        scaleY = scale.floatValue
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    },
            ) {
                PdfHighlightLayer(
                    pageIndex = 0,
                    items = emptyList(),
                    selectionEnabled = true,
                    textPage = page,
                    pageScale = scale.floatValue,
                    selectionSession = session,
                    onSelectCompleted = { saved += it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val unzoomedAnchor = visualWidthDp("pdf-selection-handle-anchor")
        val unzoomedFocus = visualWidthDp("pdf-selection-handle-focus")
        rule.runOnIdle { scale.floatValue = 2.5f }
        rule.waitForIdle()
        val zoomedAnchor = visualWidthDp("pdf-selection-handle-anchor")
        val zoomedFocus = visualWidthDp("pdf-selection-handle-focus")

        assertEquals(44f, unzoomedAnchor, .5f)
        assertEquals(44f, unzoomedFocus, .5f)
        assertEquals(44f, zoomedAnchor, .5f)
        assertEquals(44f, zoomedFocus, .5f)
    }
    @Test fun externalSessionPrewarmsReverseSelectionAndDraggingAnchorKeepsIdentity() {
        show(
            consumeInitial = true,
            selectionRange = PdfTextSelectionRange(
                PdfSelectablePage.Hit(0, 1),
                PdfSelectablePage.Hit(0, 0),
            ),
        )
        val anchorBounds = rule.onNodeWithTag("pdf-selection-handle-anchor").fetchSemanticsNode().boundsInRoot
        val focusBounds = rule.onNodeWithTag("pdf-selection-handle-focus").fetchSemanticsNode().boundsInRoot
        assertTrue(anchorBounds.center.x > focusBounds.center.x)
        val aRect=page.lines[0].glyphs[0].rect
        val aEndTipX=(aRect.x+aRect.width)*page.width
        val pageNode = rule.onNodeWithTag("page").fetchSemanticsNode()
        val pageOrigin = pageNode.boundsInRoot.topLeft
        val reverseAnchor=PdfTextSelectionGeometry.handles(
            page,
            PdfTextSelectionRange(PdfSelectablePage.Hit(0,1),PdfSelectablePage.Hit(0,0)),
            pageNode.layoutInfo.coordinates.size.width.toFloat(),
            pageNode.layoutInfo.coordinates.size.height.toFloat(),
            12.5f*rule.density.density,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }

        rule.onNodeWithTag("page").performTouchInput {
            down(anchorBounds.center - pageOrigin)
            advanceEventTime(50)
            moveTo(Offset(
                anchorBounds.center.x-pageOrigin.x+aEndTipX-reverseAnchor.tipX,
                anchorBounds.center.y-pageOrigin.y,
            ))
            up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("A", saved.single().excerpt) }
    }
    @Test fun tapOnHandleBodyDoesNotCommitOrMoveTheEndpoint() {
        show(
            selectionRange = PdfTextSelectionRange(
                PdfSelectablePage.Hit(0, 0),
                PdfSelectablePage.Hit(0, 1),
            ),
        )
        val anchorBefore = rule.onNodeWithTag("pdf-selection-handle-anchor").fetchSemanticsNode().boundsInRoot
        val focusBefore = rule.onNodeWithTag("pdf-selection-handle-focus").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("page").performTouchInput { click(anchorBefore.center) }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
        val anchorAfter = rule.onNodeWithTag("pdf-selection-handle-anchor").fetchSemanticsNode().boundsInRoot
        val focusAfter = rule.onNodeWithTag("pdf-selection-handle-focus").fetchSemanticsNode().boundsInRoot
        assertEquals(anchorBefore, anchorAfter)
        assertEquals(focusBefore, focusAfter)
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
    }
    @Test fun draggingStartPastEndDoesNotWaitForUpToCorrectAffinity() {
        show(
            consumeInitial = true,
            selectionRange = PdfTextSelectionRange(
                PdfSelectablePage.Hit(0, 0),
                PdfSelectablePage.Hit(0, 0),
            ),
        )
        val anchor = PdfTextSelectionGeometry.handles(
            page,
            PdfTextSelectionRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 0)),
            300f,
            400f,
            12.5f * rule.density.density,
        ).single { it.handle == PdfSelectionHandle.ANCHOR }
        val bRect=page.lines[0].glyphs[1].rect
        val bEndTipX=(bRect.x+bRect.width)*page.width

        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(anchor.x, anchor.y))
            advanceEventTime(50)
            moveTo(Offset(anchor.x+bEndTipX-anchor.tipX, anchor.y))
        }
        val duringCrossing = rule.onNodeWithTag("pdf-selection-handle-anchor").fetchSemanticsNode().boundsInRoot
        val fixedFocus = rule.onNodeWithTag("pdf-selection-handle-focus").fetchSemanticsNode().boundsInRoot
        assertTrue("dragged anchor must cross its fixed focus", duringCrossing.center.x > fixedFocus.center.x)

        rule.onNodeWithTag("page").performTouchInput { up() }
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("AB", saved.single().excerpt) }
    }
    @Test fun noteActionUsesDirectSelectionCallbackWithoutStyleDialog() {
        show(selectionNote = { notedSelections += it })
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-action-note").performClick()
        rule.runOnIdle {
            assertTrue(saved.isEmpty())
            assertEquals("A",notedSelections.single().excerpt)
        }
    }
    @Test fun copyActionClearsSelectionWithoutSubmitting() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-action-copy").performClick()
        rule.onNodeWithTag("pdf-selection-actions").assertDoesNotExist()
        rule.onNodeWithTag("pdf-selection-handle-anchor").assertDoesNotExist()
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun outsideTapClearsSelectionWithoutSubmitting() {
        show(recordParentTaps = true)
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.onNodeWithTag("page").performTouchInput { click(Offset(width*.8f,height*.75f)) }
        rule.onNodeWithTag("pdf-selection-actions").assertDoesNotExist()
        rule.runOnIdle { assertTrue(saved.isEmpty());assertTrue(parentTaps.isEmpty()) }
    }
    @Test fun ordinaryTapWithoutSelectionReachesParentChrome() {
        show(recordParentTaps = true)
        rule.onNodeWithTag("page").performTouchInput { click(Offset(width*.8f,height*.75f)) }
        rule.runOnIdle {
            assertEquals(1,parentTaps.size)
            assertTrue(saved.isEmpty())
            assertTrue(ownership.isEmpty())
        }
    }
    @Test fun scrollBeforeLongPressDoesNotCommit() {
        show()
        rule.onNodeWithTag("page").performTouchInput { swipe(Offset(width*.15f,height*.22f),Offset(width*.32f,height*.42f),150) }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun cancellationDoesNotCommitThePreview() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.42f));cancel()
        }
        rule.runOnIdle {
            assertTrue(saved.isEmpty())
            assertEquals(listOf(true,false),ownership)
        }
    }
    @Test fun secondFingerCancelsSelection() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(0,Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(0,Offset(width*.32f,height*.42f))
            down(1,Offset(width*.6f,height*.6f));up(1);up(0)
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun disabledSelectionNeverCommits() {
        show(false)
        rule.onNodeWithTag("page").performTouchInput { down(Offset(width*.15f,height*.22f));advanceEventTime(700);up() }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }
    @Test fun longPressOnBlankSpaceDoesNotMakeABox() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.8f,height*.7f));advanceEventTime(700);moveTo(Offset(width*.32f,height*.42f));up()
        }
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
    }

    @Test fun longPressBeforeTextIsReadyContinuesMoveAndUpInTheSameGesture() {
        val ready = androidx.compose.runtime.mutableStateOf<PdfSelectablePage?>(null)
        val loading = androidx.compose.runtime.mutableStateOf(true)
        val ownership = mutableListOf<Boolean>()
        rule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                PdfHighlightLayer(
                    pageIndex = 0,
                    items = emptyList(),
                    selectionEnabled = true,
                    textPage = ready.value,
                    textLoading = loading.value,
                    onSelectionUnavailable = { unavailable += Unit },
                    onSelectCompleted = { saved += it },
                    onSelectionGestureActiveChanged = { ownership += it },
                    modifier = Modifier.testTag("page"),
                )
            }
        }
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width * .15f, height * .22f)); advanceEventTime(700)
        }
        rule.mainClock.advanceTimeBy(700)
        rule.runOnIdle {
            assertTrue("no re-press prompt while extraction is pending", unavailable.isEmpty())
            assertEquals("the long press must start before publishing ready", listOf(true), ownership)
        }
        rule.runOnIdle { ready.value = page; loading.value = false }
        rule.waitForIdle()
        rule.onNodeWithTag("page").performTouchInput {
            moveTo(Offset(width * .325f, height * .225f)); up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.runOnIdle { assertEquals(listOf(true, false), ownership) }
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("AB", saved.single().excerpt) }
    }

    @Test fun textReadyBeforeLongPressTimeoutStillUsesTheOriginalPointerStream() {
        val ready = androidx.compose.runtime.mutableStateOf<PdfSelectablePage?>(null)
        val loading = androidx.compose.runtime.mutableStateOf(true)
        val ownership = mutableListOf<Boolean>()
        rule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                PdfHighlightLayer(
                    pageIndex = 0,
                    items = emptyList(),
                    selectionEnabled = true,
                    textPage = ready.value,
                    textLoading = loading.value,
                    onSelectionUnavailable = { unavailable += Unit },
                    onSelectCompleted = { saved += it },
                    onSelectionGestureActiveChanged = { ownership += it },
                    modifier = Modifier.testTag("page"),
                )
            }
        }
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width * .15f, height * .22f)); advanceEventTime(100)
        }
        rule.runOnIdle { ready.value = page; loading.value = false }
        rule.onNodeWithTag("page").performTouchInput {
            advanceEventTime(700)
            moveTo(Offset(width * .325f, height * .225f)); up()
        }
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
        rule.runOnIdle { assertEquals(listOf(true,false),ownership) }
        rule.onNodeWithTag("pdf-selection-action-highlight").performClick()
        rule.runOnIdle { assertEquals("AB",saved.single().excerpt) }
    }

    @Test fun longPressOnScannedPageWithoutTextReportsOnceInsteadOfWaiting() {
        val scanned = PdfSelectablePage(0, 300f, 400f, emptyList(), "此页没有可选择的横排文字，扫描页暂不支持文字高亮")
        val ready = androidx.compose.runtime.mutableStateOf<PdfSelectablePage?>(null)
        val loading = androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                PdfHighlightLayer(
                    pageIndex = 0,
                    items = emptyList(),
                    selectionEnabled = true,
                    textPage = ready.value,
                    textLoading = loading.value,
                    onSelectionUnavailable = { unavailable += Unit },
                    onSelectCompleted = { saved += it },
                    modifier = Modifier.testTag("page"),
                )
            }
        }
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width * .15f, height * .22f)); advanceEventTime(700); up()
        }
        rule.runOnIdle { assertTrue(unavailable.isEmpty()) }
        rule.runOnIdle { ready.value = scanned; loading.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, unavailable.size)
            assertTrue(saved.isEmpty())
        }
        rule.onNodeWithTag("pdf-selection-actions").assertDoesNotExist()
    }

    @Test fun longPressWithNoTextSourceReportsUnavailable() {
        show(selectablePage = null)
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.runOnIdle { assertEquals(1, unavailable.size); assertTrue(saved.isEmpty()) }
    }

    @Test fun zoomedHandleStartsDraggingAtTheSameScreenTouchSlop() {
        show(
            pageScale = 2.5f,
            loupeSource = PdfSelectionLoupeSource(StubImageBitmap, colorFilter = null),
            selectionRange = PdfTextSelectionRange(PdfSelectablePage.Hit(0, 0), PdfSelectablePage.Hit(0, 0)),
        )
        val pageBounds = rule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val handleBounds = rule.onNodeWithTag("pdf-selection-handle-focus").fetchSemanticsNode().boundsInRoot
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val physicalSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        rule.onNodeWithTag("page").performTouchInput {
            down(handleBounds.center - pageBounds.topLeft)
            moveBy(Offset(0f, physicalSlop * 1.25f))
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertExists()
        rule.runOnIdle { assertTrue(saved.isEmpty()) }
        rule.onNodeWithTag("page").performTouchInput { up() }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
    }

    @Test fun draggingASelectionShowsTheLoupeAndReleasingHidesIt() {
        show(loupeSource = PdfSelectionLoupeSource(StubImageBitmap, colorFilter = null))
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.32f,height*.22f))
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertExists()
        rule.onNodeWithTag("page").performTouchInput { up() }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
    }

    @Test fun loupeDoesNotAppearWithoutPagePixels() {
        show()
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700)
            moveTo(Offset(width*.2f,height*.22f))
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
        rule.onNodeWithTag("page").performTouchInput { up() }
    }

    @Test fun longPressWithoutMovementDoesNotFlashTheLoupe() {
        show(loupeSource = PdfSelectionLoupeSource(StubImageBitmap, colorFilter = null))
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
        rule.onNodeWithTag("pdf-selection-actions").assertExists()
    }

    @Test fun handleDragAlsoShowsTheLoupe() {
        show(loupeSource = PdfSelectionLoupeSource(StubImageBitmap, colorFilter = null))
        rule.onNodeWithTag("page").performTouchInput {
            down(Offset(width*.15f,height*.22f));advanceEventTime(700);up()
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
        rule.onNodeWithTag("page").performTouchInput {
            val range=PdfTextSelectionRange(PdfSelectablePage.Hit(0,0),PdfSelectablePage.Hit(0,0))
            val focus=PdfTextSelectionGeometry.handles(
                page, range, width.toFloat(), height.toFloat(), 12.5f*rule.density.density,
            ).single { it.handle == PdfSelectionHandle.FOCUS }
            val bRect=page.lines[0].glyphs[1].rect
            val bEndTipX=(bRect.x+bRect.width)*page.width
            down(Offset(focus.x,focus.y));advanceEventTime(50)
            moveTo(Offset(focus.x+bEndTipX-focus.tipX,focus.y))
        }
        rule.onNodeWithTag("pdf-selection-loupe").assertExists()
        rule.onNodeWithTag("page").performTouchInput { up() }
        rule.onNodeWithTag("pdf-selection-loupe").assertDoesNotExist()
    }

    /** Robolectric's LEGACY graphics cannot allocate real bitmaps; only size is read here. */
    private object StubImageBitmap : androidx.compose.ui.graphics.ImageBitmap {
        override val width = 60
        override val height = 80
        override val config = androidx.compose.ui.graphics.ImageBitmapConfig.Argb8888
        override val colorSpace = androidx.compose.ui.graphics.colorspace.ColorSpaces.Srgb
        override val hasAlpha = true
        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) = Unit
        override fun prepareToDraw() = Unit
    }
}
