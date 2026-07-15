package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R7H11bHorizontalSharedPhysicsTest {
    private fun source(name: String): String = File(
        "src/main/java/com/markleaf/app/${if (name == "PdfViewerScreen.kt") "ui/pdf" else "pdf"}/$name",
    ).readText()

    @Test
    fun `horizontal mode delegates pointer epoch pan focal and fling to shared core`() {
        val screen = source("PdfViewerScreen.kt")
        val controller = source("PdfGestureTransformController.kt")
        assertTrue(screen.contains("PdfGestureTransformController("))
        assertTrue(screen.contains("sharedGestureController.pointerFrame("))
        assertTrue(screen.contains("sharedGestureController.applyPlatformFlingDelta("))
        assertTrue(screen.contains("sharedGestureController.stepSpring("))
        assertFalse(screen.contains("sharedGestureController.trackPointersWithoutTransform("))
        assertFalse(screen.contains("ZoomPanPointerEpochReducer.frame("))
        assertTrue(controller.contains("ZoomPanPointerEpochReducer.frame("))
        assertTrue(controller.contains("ZoomPanPhysics.springStep("))
        assertTrue(controller.contains("ZoomPanPhysicsProfile.ScaleRange"))
        assertTrue(screen.contains("val horizontalOverscrollPolicy = AxisOverscrollPolicy("))
        assertTrue(screen.contains("cancelHorizontalMotion()"))
    }

    @Test
    fun `old horizontal tuning and duplicate pointer pair algorithm are gone from production`() {
        val screen = source("PdfViewerScreen.kt")
        val adapter = source("HorizontalZoomPanPhysics.kt")
        assertFalse(screen.contains("28.dp.toPx()"))
        assertFalse(screen.contains("Animatable(1f).animateTo"))
        assertFalse(screen.contains("var pair: Pair<Long,Long>?"))
        assertFalse(adapter.contains("private fun panAxis("))
        assertFalse(adapter.contains("private fun inverseRubberBand("))
    }

    @Test
    fun `three page strip is the only horizontal visual owner`() {
        val screen = source("PdfViewerScreen.kt")
        assertTrue(screen.contains("ThreePageStripPhysics.drag("))
        assertTrue(screen.contains("settleZoomedPageTransition("))
        assertTrue(screen.contains("ThreePageStripPhysics.slotTranslationPx("))
        assertTrue(screen.contains("pageStrip = pageStrip.committed("))
        assertFalse(screen.contains("HorizontalPager("))
        assertFalse(screen.contains("PagerSnapDistance.atMost(1)"))
        assertFalse(screen.contains("horizontalNestedScrollDispatcher"))
        assertFalse(screen.contains("dispatchPostScroll"))
        assertFalse(screen.contains("dispatchRawDelta"))
        assertFalse(screen.contains("performFling"))
    }

    @Test
    fun `top alignment and unscaled translation contract are preserved`() {
        val screen = source("PdfViewerScreen.kt")
        assertTrue(screen.contains("contentAlignment = Alignment.TopCenter"))
        assertTrue(screen.contains("transformOrigin = TransformOrigin(0.5f, 0f)"))
        assertTrue(screen.contains("translationX = activeTransform.pan.displayOffsetX"))
        assertTrue(screen.contains("translationY = activeTransform.pan.displayOffsetY"))
        assertFalse(screen.contains("translationX = activeTransform.pan.displayOffsetX * activeTransform.scale"))
        assertFalse(screen.contains("translationY = activeTransform.pan.displayOffsetY * activeTransform.scale"))
    }

    @Test
    fun `tile and bitmap identity are not pointer input restart keys`() {
        val screen = source("PdfViewerScreen.kt")
        assertTrue(
            screen.contains(
                    ".pointerInput(\n" +
                    "                        slot.stableId,\n" +
                    "                        slot.role,\n" +
                    "                        activeHorizontalPage,\n" +
                    "                        fittedPage.fittedWidthPx,\n" +
                    "                        fittedPage.fittedHeightPx,",
            ),
        )
        assertFalse(screen.contains(".pointerInput(page, scale, currentHorizontalStatePage)"))
    }

    @Test
    fun `horizontal page transform map is removed and viewport unused region is white`() {
        val screen = source("PdfViewerScreen.kt")
        assertFalse(screen.contains("horizontalTransforms"))
        assertTrue(screen.contains("HorizontalZoomPanPhysics.restoreViewportSession("))
        assertTrue(screen.contains(".testTag(\"h1-horizontal-viewport\")"))
        assertTrue(screen.contains(".background(horizontalViewportBackground)"))
        assertTrue(screen.contains(".align(Alignment.BottomCenter)"))
    }
}
