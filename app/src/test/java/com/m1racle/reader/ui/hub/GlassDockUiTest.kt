package com.m1racle.reader.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.m1racle.reader.ui.theme.M1racleReaderTheme
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
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-notlong-notround-any-420dpi-keyshidden-nonav")
class GlassDockUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `all icons retain full bounds with a real backdrop host`() {
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = {})
                }
            }
        }
        HubDestination.entries.forEach { destination ->
            rule.onNodeWithTag("dock-icon-${destination.name}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertWidthIsEqualTo(24.dp)
                .assertHeightIsEqualTo(24.dp)
        }
    }

    @Test
    fun `visible tab labels stay displayed above the sampling overlay`() {
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = {})
                }
            }
        }
        rule.onNodeWithText("首页").assertIsDisplayed()
        rule.onNodeWithText("文库").assertIsDisplayed()
        rule.onAllNodes(isSelectable()).fetchSemanticsNodes().let { nodes ->
            assertEquals(4, nodes.size)
        }
    }

    @Test
    fun `selection follows owner even when callback refuses navigation`() {
        val requested = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = requested::add)
                }
            }
        }
        rule.onAllNodes(isSelectable())[3].performClick()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(HubDestination.SETTINGS), requested) }
    }

    @Test
    fun `rapid navigation retargets shared capsule across page replacement`() {
        val selected = mutableStateOf(HubDestination.HOME)
        lateinit var motion: HubDockMotion
        rule.mainClock.autoAdvance = false
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(selected.value) {
                    motion = LocalHubDockMotion.current
                    key(selected.value) { GlassDock(onSelect = { selected.value = it }) }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            selected.value = HubDestination.SETTINGS
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(HubDestination.SETTINGS, motion.selected) }
        rule.mainClock.advanceTimeUntil(timeoutMillis = 1000) { motion.position.value > 0f }
        rule.runOnIdle {
            assertTrue("capsule must animate rather than restart at destination: ${motion.position.value}", motion.position.value > 0f && motion.position.value < 3f)
            selected.value = HubDestination.LIBRARY
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeBy(1200)
        rule.onAllNodes(isSelectable())[1].assertIsSelected()
        rule.runOnIdle { assertEquals(1f, motion.position.value, 0.001f) }
    }

    @Test
    fun `drag navigates once on release and cancellation preserves route`() {
        val selected = mutableStateOf(HubDestination.HOME)
        val requested = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(selected.value) {
                    GlassDock(onSelect = { requested.add(it); selected.value = it })
                }
            }
        }
        rule.onAllNodes(isSelectable())[0].performTouchInput {
            down(center)
            moveTo(Offset(width * 3.5f, center.y), 300)
            cancel()
        }
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        rule.runOnIdle { assertTrue(requested.isEmpty()) }
        rule.onAllNodes(isSelectable())[0].performTouchInput {
            down(center)
            moveTo(Offset(width * 3.5f, center.y), 300)
            up()
        }
        rule.onAllNodes(isSelectable())[3].assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(HubDestination.SETTINGS), requested) }
    }

    @Test
    fun `short drag lands on adjacent tab without counting touch slop twice`() {
        val selected = mutableStateOf(HubDestination.HOME)
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(selected.value) {
                    GlassDock(onSelect = { selected.value = it })
                }
            }
        }
        rule.onAllNodes(isSelectable())[0].performTouchInput {
            down(center)
            moveTo(Offset(width * 1.5f, center.y), 300)
            up()
        }
        rule.onAllNodes(isSelectable())[1].assertIsSelected()
    }

    @Test
    fun `holding a tab does not navigate until release and cancel clears press`() {
        val requested = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) { GlassDock(onSelect = requested::add) }
            }
        }
        val tab = rule.onAllNodes(isSelectable())[1]
        tab.performTouchInput { down(center); advanceEventTime(800) }
        rule.mainClock.advanceTimeBy(800)
        rule.runOnIdle { assertTrue(requested.isEmpty()) }
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        tab.performTouchInput { cancel() }
        rule.runOnIdle { assertTrue(requested.isEmpty()) }
        tab.performTouchInput { down(center); advanceEventTime(800); up() }
        rule.runOnIdle { assertEquals(listOf(HubDestination.LIBRARY), requested) }
    }

    @Test
    fun `rtl drag selects the destination under the finger`() {
        val selected = mutableStateOf(HubDestination.HOME)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                M1racleReaderTheme {
                    HubDockMotionProvider(selected.value) {
                        GlassDock(onSelect = { selected.value = it })
                    }
                }
            }
        }
        rule.onAllNodes(isSelectable())[0].performTouchInput {
            down(center)
            moveTo(Offset(-width * 2.5f, center.y), 300)
            up()
        }
        rule.onAllNodes(isSelectable())[3].assertIsSelected()
    }
}
