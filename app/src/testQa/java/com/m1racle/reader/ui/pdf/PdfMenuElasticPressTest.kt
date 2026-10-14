package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PdfMenuElasticPressTest {
    @get:Rule val rule = createComposeRule()
    private var clicks = 0

    private fun showMenu() {
        rule.setContent {
            Box(Modifier.padding(60.dp)) {
                PdfMenuElasticPress(Modifier.testTag("gesture")) { transform, _ ->
                    Box(Modifier.graphicsLayer(transform).size(184.dp, 104.dp).testTag("surface").clickable { clicks++ })
                }
            }
        }
    }

    private fun pickUpAndMove() {
        rule.onNodeWithTag("gesture").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithTag("gesture").performTouchInput {
            advanceEventTime(700)
            moveBy(Offset(70f, 30f))
        }
        rule.waitForIdle()
    }

    @Test fun heldDeformationDoesNotChaseTheFingerAfterRendering() {
        showMenu()
        pickUpAndMove()
        rule.mainClock.autoAdvance = false
        val surface = rule.onNodeWithTag("surface")
        val before = surface.fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("gesture").performTouchInput { moveBy(Offset(-60f, 35f)) }
        // The continued move is composed a few test frames after the call.
        // The first rendered pose must already be the final one.
        var seen = before
        var frames = 0
        while (seen == before && frames < 8) {
            rule.mainClock.advanceTimeByFrame()
            seen = surface.fetchSemanticsNode().boundsInRoot
            frames++
        }
        assertTrue("Held pose did not follow the pointer within $frames frames", seen != before)
        rule.mainClock.advanceTimeBy(700)
        assertEquals(
            "Held deformation must use the latest pointer, not keep animating toward it",
            seen,
            surface.fetchSemanticsNode().boundsInRoot,
        )
        rule.onNodeWithTag("gesture").performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun shortTapStillSelects() {
        showMenu()
        rule.onNodeWithTag("surface").performTouchInput { click() }
        assertEquals(1, clicks)
    }

    @Test fun longPressFollowsTheFingerWithinABoundThenReturnsWithoutSelecting() {
        showMenu()
        val start = rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        pickUpAndMove()
        val moved = rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        val dx = moved.center.x - start.center.x
        val dy = moved.center.y - start.center.y
        assertTrue("card should shift with the finger, dx=$dx", dx > 4f)
        assertTrue("card should shift with the finger, dy=$dy", dy > 1f)
        assertTrue("follow stays well inside the 70px finger travel, dx=$dx", dx < 40f)
        assertTrue("follow stays well inside the 30px finger travel, dy=$dy", dy < 24f)
        assertEquals(start.width, moved.width, 1.5f)
        assertEquals(start.height, moved.height, 1.5f)
        rule.onNodeWithTag("gesture").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(start, rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, clicks)
    }

    @Test fun cancelledDragAlsoReturnsWithoutSelecting() {
        showMenu()
        val start = rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        pickUpAndMove()
        rule.onNodeWithTag("gesture").performTouchInput { cancel() }
        rule.waitForIdle()
        assertEquals(start, rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, clicks)
    }

    @Test fun movementBeforeLongPressDoesNotPickUpTheMenu() {
        showMenu()
        val start = rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("gesture").performTouchInput {
            down(center)
            moveBy(Offset(70f, 30f), delayMillis = 30)
            up()
        }
        rule.waitForIdle()
        assertEquals(start, rule.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun diagonalStrainHasTheSameResistanceAsAnAxisStrain() {
        val axis = pdfMenuStrain(Offset(100f, 0f), 18f)
        val diagonal = pdfMenuStrain(Offset(60f, 80f), 18f)
        assertEquals(axis.getDistance(), diagonal.getDistance(), 0.001f)
        assertTrue(diagonal.getDistance() < 1f)
        assertEquals(0.75f, diagonal.x / diagonal.y, 0.001f)
    }

    @Test fun resistanceIsSymmetricContinuousAndBounded() {
        assertEquals(Offset.Zero, pdfMenuStrain(Offset.Zero, 40f))
        val near = pdfMenuStrain(Offset(1f, -1f), 40f)
        val far = pdfMenuStrain(Offset(10000f, -10000f), 40f)
        assertTrue(near.x > 0f && near.x < 1f)
        assertEquals(-near.x, near.y, 0f)
        assertTrue(far.x > near.x && far.getDistance() < 1f)
        assertEquals(-far.x, far.y, 0f)
    }
}
