package com.m1racle.reader.ui.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
    @Test
    fun `held import action grows without importing and returns on cancellation`() {
        var imports = 0
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = {}, onImportDocument = { imports++ })
                }
            }
        }
        val icon = rule.onNodeWithContentDescription("导入文件", useUnmergedTree = true)
        val resting = icon.fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("dock-import")
        val touchBounds = action.fetchSemanticsNode().boundsInRoot
        action.performTouchInput { down(center); advanceEventTime(900) }
        rule.mainClock.advanceTimeBy(900)
        val held = icon.fetchSemanticsNode().boundsInRoot
        assertTrue("held=$held resting=$resting", held.width > resting.width * 1.08f)
        assertTrue(held.width <= resting.width * 1.15f)
        assertEquals(touchBounds, action.fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals(0, imports) }
        action.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(1200)
        assertEquals(resting, icon.fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals(0, imports) }
        action.performTouchInput { down(center); advanceEventTime(900); up() }
        rule.runOnIdle { assertEquals(1, imports) }
    }

    @Test
    fun `trailing import action preserves the active tab and does not participate in tab dragging`() {
        var imports = 0
        val requested = mutableListOf<HubDestination>()
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.BOOKMARKS) {
                    GlassDock(onSelect = requested::add, onImportDocument = { imports++ })
                }
            }
        }
        rule.onNodeWithTag("dock-import").performClick()
        rule.onAllNodes(isSelectable())[2].assertIsSelected()
        rule.runOnIdle { assertEquals(1, imports); assertTrue(requested.isEmpty()) }
        rule.onNodeWithTag("dock-import").performTouchInput { down(center); moveBy(Offset(-80f, 0f)); cancel() }
        rule.onAllNodes(isSelectable())[2].assertIsSelected()
        rule.runOnIdle { assertEquals(1, imports); assertTrue(requested.isEmpty()) }
    }

    @Test
    fun `busy import disables the trailing action`() {
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = {}, onImportDocument = {}, importing = true)
                }
            }
        }
        rule.onNodeWithTag("dock-import").assertIsNotEnabled()
    }

    @Test
    fun `narrow dock retains all labels and separate action with double system text`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                M1racleReaderTheme {
                    Box(Modifier.width(320.dp)) {
                        HubDockMotionProvider(HubDestination.HOME) {
                            GlassDock(onSelect = {}, onImportDocument = {})
                        }
                    }
                }
            }
        }
        val action = rule.onNodeWithTag("dock-import").fetchSemanticsNode().boundsInRoot
        listOf("首页", "文库", "书签", "设置").forEachIndexed { index, label ->
            val tab = rule.onAllNodes(isSelectable())[index].fetchSemanticsNode().boundsInRoot
            val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= tab.left && text.right <= tab.right)
            assertTrue(text.top >= tab.top && text.bottom <= tab.bottom)
            assertTrue(tab.width >= 48f * rule.density.density)
            assertTrue(tab.right < action.left)
        }
    }

    @Test
    fun `large system labels fit inside each glass tab with accessible touch targets`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                M1racleReaderTheme {
                    HubDockMotionProvider(HubDestination.HOME) { GlassDock(onImportDocument = {}, onSelect = {}) }
                }
            }
        }
        val labels = listOf("首页", "文库", "书签", "设置")
        labels.forEachIndexed { index, label ->
            val tab = rule.onAllNodes(isSelectable())[index].fetchSemanticsNode().boundsInRoot
            val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$label label=$text tab=$tab", text.top >= tab.top && text.bottom <= tab.bottom)
            assertTrue(text.left >= tab.left && text.right <= tab.right)
            assertTrue(tab.height >= 48f * rule.density.density)
        }
    }
    @get:Rule val rule = createComposeRule()

    @Test
    fun `all icons retain full bounds with a real backdrop host`() {
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onImportDocument = {}, onSelect = {})
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
                val page = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                CompositionLocalProvider(com.m1racle.reader.ui.material.LocalGlassBackdropHost provides
                    com.m1racle.reader.ui.material.GlassBackdropHost(page, page)) {
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onImportDocument = {}, onSelect = {})
                    }
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
                    GlassDock(onImportDocument = {}, onSelect = requested::add)
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
                    key(selected.value) { GlassDock(onImportDocument = {}, onSelect = { selected.value = it }) }
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
                    GlassDock(onImportDocument = {}, onSelect = { requested.add(it); selected.value = it })
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
                    GlassDock(onImportDocument = {}, onSelect = { selected.value = it })
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
                HubDockMotionProvider(HubDestination.HOME) { GlassDock(onImportDocument = {}, onSelect = requested::add) }
            }
        }
        val tab = rule.onAllNodes(isSelectable())[1]
        val lens = rule.onNodeWithTag("dock-selection")
        val resting = lens.fetchSemanticsNode().boundsInRoot
        tab.performTouchInput { down(center); advanceEventTime(800) }
        rule.mainClock.advanceTimeBy(800)
        rule.runOnIdle { assertTrue(requested.isEmpty()) }
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        assertEquals(tab.fetchSemanticsNode().boundsInRoot.center.x,
            lens.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        tab.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(1200)
        assertEquals(resting, lens.fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertTrue(requested.isEmpty()) }
        tab.performTouchInput { down(center); advanceEventTime(800); up() }
        rule.runOnIdle { assertEquals(listOf(HubDestination.LIBRARY), requested) }
    }

    @Test
    fun `selection lens grows while held and returns after cancellation`() {
        rule.setContent {
            M1racleReaderTheme {
                HubDockMotionProvider(HubDestination.HOME) { GlassDock(onImportDocument = {}, onSelect = {}) }
            }
        }
        val lens = rule.onNodeWithTag("dock-selection", useUnmergedTree = true)
        val resting = lens.fetchSemanticsNode().boundsInRoot
        val icon = rule.onNodeWithTag("dock-icon-HOME", useUnmergedTree = true)
        val restingIcon = icon.fetchSemanticsNode().boundsInRoot
        val tab = rule.onAllNodes(isSelectable())[0]
        tab.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        val held = lens.fetchSemanticsNode().boundsInRoot
        val heldIcon = icon.fetchSemanticsNode().boundsInRoot
        assertTrue("heldIcon=$heldIcon restingIcon=$restingIcon",
            heldIcon.width in restingIcon.width * 1.20f..restingIcon.width * 1.24f)
        assertTrue("held=$held resting=$resting", held.width in resting.width * 1.36f..resting.width * 1.40f)
        assertTrue("held=$held resting=$resting", held.height in resting.height * 1.42f..resting.height * 1.46f)
        tab.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(1200)
        assertEquals(resting, lens.fetchSemanticsNode().boundsInRoot)
        assertEquals(restingIcon, icon.fetchSemanticsNode().boundsInRoot)
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
    }

    @Test
    fun `rtl drag selects the destination under the finger`() {
        val selected = mutableStateOf(HubDestination.HOME)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                M1racleReaderTheme {
                    HubDockMotionProvider(selected.value) {
                        GlassDock(onImportDocument = {}, onSelect = { selected.value = it })
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
