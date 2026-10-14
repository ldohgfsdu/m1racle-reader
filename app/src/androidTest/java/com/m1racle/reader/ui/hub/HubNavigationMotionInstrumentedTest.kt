package com.m1racle.reader.ui.hub

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.m1racle.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise the real NavGraph, including the saved-stack return to Home. */
class HubNavigationMotionInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun tapAnimatesActualPageForwardAndBackWhileDockStaysMounted() {
        rule.waitForIdle()
        for ((tab, route, direction) in listOf(Triple("LIBRARY", "library", 1), Triple("HOME", "home", -1))) {
            rule.mainClock.autoAdvance = false
            try {
                rule.onNodeWithTag("dock-icon-$tab", useUnmergedTree = true).performTouchInput {
                    down(center); advanceEventTime(16); up()
                }
                rule.mainClock.advanceTimeBy(64)
                val page = rule.onNodeWithTag("hub-page-$route")
                val node = page.fetchSemanticsNode()
                val mid = node.positionInRoot.x
                assertTrue("actual $route page must still be moving in the dock direction: $mid", mid * direction > 1f)
                assertTrue("page motion must remain a short slide", kotlin.math.abs(mid) < node.size.width * .08f)
                rule.onNodeWithTag("dock-material").assertExists()
                rule.mainClock.advanceTimeBy(1000)
                assertEquals(0f, page.fetchSemanticsNode().positionInRoot.x, 1f)
            } finally { rule.mainClock.autoAdvance = true }
        }
    }
}
