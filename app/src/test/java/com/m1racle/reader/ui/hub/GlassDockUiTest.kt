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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-normal-notlong-notround-any-420dpi-keyshidden-nonav")
class GlassDockUiTest {
    @Test fun `drag visibly stretches and squashes lens then release rebounds and settles`() {
        rule.setContent { DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
            GlassDock(onImportDocument = {}, onSelect = {})
        } } }
        val home = rule.onAllNodes(isSelectable())[0]
        val lens = rule.onNodeWithTag("dock-selection")
        val rest = lens.fetchSemanticsNode().boundsInRoot
        val slot = rule.onAllNodes(isSelectable())[1].fetchSemanticsNode().boundsInRoot.center.x -
            home.fetchSemanticsNode().boundsInRoot.center.x
        rule.mainClock.autoAdvance = false
        try {
            home.performTouchInput { down(center); advanceEventTime(800) }
            rule.mainClock.advanceTimeBy(800)
            val held = lens.fetchSemanticsNode().boundsInRoot
            repeat(5) {
                home.performTouchInput { moveBy(Offset(slot * .6f, 0f), 16) }
                rule.mainClock.advanceTimeByFrame()
            }
            val stretched = lens.fetchSemanticsNode().boundsInRoot
            assertTrue("drag must visibly widen lens", stretched.width > held.width * 1.15f)
            assertTrue("drag must visibly compress height", stretched.height < held.height * .94f)
            home.performTouchInput { cancel() }
            var rebounded = false
            repeat(30) {
                rule.mainClock.advanceTimeByFrame()
                if (lens.fetchSemanticsNode().boundsInRoot.width < rest.width * .98f) rebounded = true
            }
            assertTrue("release must squash past rest before returning", rebounded)
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        assertEquals(rest, lens.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `held lens follows every movement within one frame including reversals`() {
        assertDirectTracking(LayoutDirection.Ltr)
    }

    @Test fun `rtl held lens follows every movement within one frame including reversals`() {
        assertDirectTracking(LayoutDirection.Rtl)
    }

    private fun assertDirectTracking(direction: LayoutDirection) {
        val requests = mutableListOf<HubDestination>()
        rule.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) {
            DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
                GlassDock(onImportDocument = {}, onSelect = requests::add)
            } }
        } }
        val home = rule.onAllNodes(isSelectable())[0]
        val library = rule.onAllNodes(isSelectable())[1]
        val lens = rule.onNodeWithTag("dock-selection")
        val slot = library.fetchSemanticsNode().boundsInRoot.center.x -
            home.fetchSemanticsNode().boundsInRoot.center.x
        rule.mainClock.autoAdvance = false
        try {
            home.performTouchInput { down(center); advanceEventTime(800) }
            rule.mainClock.advanceTimeBy(800)
            var position = 0f
            for (movement in listOf(.75f, .5f, -.4f, .6f, -.8f)) {
                position += movement
                home.performTouchInput { moveBy(Offset(slot * movement, 0f), 16) }
                rule.mainClock.advanceTimeByFrame()
                val homeX = home.fetchSemanticsNode().boundsInRoot.center.x
                assertEquals("lens must track this event without waiting for a spring",
                    homeX + slot * position, lens.fetchSemanticsNode().boundsInRoot.center.x, 1f)
            }
            // Stopping a held finger must not cause position to keep catching up.
            val relativeX = lens.fetchSemanticsNode().boundsInRoot.center.x -
                home.fetchSemanticsNode().boundsInRoot.center.x
            rule.mainClock.advanceTimeBy(1200)
            assertEquals(relativeX, lens.fetchSemanticsNode().boundsInRoot.center.x -
                home.fetchSemanticsNode().boundsInRoot.center.x, 1f)
            assertTrue(requests.isEmpty())
            home.performTouchInput { cancel() }
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        assertEquals(home.fetchSemanticsNode().boundsInRoot.center.x,
            lens.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `extended system text retains breathing room inside each tab capsule`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 3f)) {
                DockTestTheme {
                    HubDockMotionProvider(HubDestination.HOME) {
                        GlassDock(onSelect = {}, onImportDocument = {})
                    }
                }
            }
        }
        val clearance = 4f * rule.density.density
        listOf("首页", "文库", "书签", "设置").forEachIndexed { index, label ->
            val tab = rule.onAllNodes(isSelectable())[index].fetchSemanticsNode().boundsInRoot
            val icon = rule.onNodeWithTag("dock-icon-${HubDestination.entries[index].name}",
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val text = rule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$label needs top clearance: icon=$icon tab=$tab", icon.top >= tab.top + clearance)
            assertTrue("$label needs bottom clearance: text=$text tab=$tab", text.bottom <= tab.bottom - clearance)
        }
    }

    @Test
    fun `held import action grows without importing and returns on cancellation`() {
        var imports = 0
        rule.setContent {
            DockTestTheme {
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
        assertEquals("reference independent action expands about nine percent", resting.width * 1.085f, held.width, .5f)
        assertTrue(action.fetchSemanticsNode().boundsInRoot.width <= touchBounds.width * 1.04f)
        rule.runOnIdle { assertEquals(0, imports) }
        action.performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(1200)
        assertEquals(resting, icon.fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals(0, imports) }
        action.performTouchInput { down(center); advanceEventTime(900); up() }
        rule.runOnIdle { assertEquals(1, imports) }
    }

    @Test
    fun `central import action preserves the active tab and does not participate in tab dragging`() {
        var imports = 0
        val requested = mutableListOf<HubDestination>()
        rule.setContent {
            DockTestTheme {
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
            DockTestTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onSelect = {}, onImportDocument = {}, importing = true)
                }
            }
        }
        rule.onNodeWithTag("dock-import").assertIsNotEnabled()
    }

    @Test
    fun `narrow unified dock retains all labels and central action with double system text`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                DockTestTheme {
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
            if (index < 2) assertTrue(tab.right <= action.left + 1f)
            else assertTrue(tab.left >= action.right - 1f)
        }
    }

    @Test
    fun `large system labels fit inside each glass tab with accessible touch targets`() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                DockTestTheme {
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
    @Test fun `inactive tab previews on press and cancellation never commits`() {
        val requests = mutableListOf<HubDestination>()
        rule.setContent { DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
            GlassDock(onImportDocument = {}, onSelect = requests::add)
        } } }
        val tab = rule.onAllNodes(isSelectable())[2]
        tab.performTouchInput { down(center); advanceEventTime(800) }
        rule.mainClock.advanceTimeBy(800)
        assertEquals(tab.fetchSemanticsNode().boundsInRoot.center.x,
            rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertTrue(requests.isEmpty())
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        tab.performTouchInput { cancel() }
        rule.waitForIdle()
        assertTrue(requests.isEmpty())
        assertEquals(rule.onAllNodes(isSelectable())[0].fetchSemanticsNode().boundsInRoot.center.x,
            rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot.center.x, 1f)
    }

    @Test fun `new press interrupts old release without collapsing held lens`() {
        rule.setContent { DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
            GlassDock(onImportDocument = {}, onSelect = {})
        } } }
        val lens = rule.onNodeWithTag("dock-selection")
        val rest = lens.fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        val tab = rule.onAllNodes(isSelectable())[0]
        tab.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200)
        tab.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(32)
        tab.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        assertTrue(lens.fetchSemanticsNode().boundsInRoot.height > rest.height * 1.35f)
        tab.performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(rest, lens.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `import is centered inside the same capsule and never a selected destination`() {
        val requests = mutableListOf<HubDestination>()
        var imports = 0
        rule.setContent { DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
            GlassDock(onImportDocument = { imports++ }, onSelect = requests::add)
        } } }
        val capsule = rule.onNodeWithTag("dock-capsule").fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("dock-import")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        assertEquals(capsule.center.x, bounds.center.x, 1f)
        assertTrue(bounds.left > capsule.left && bounds.right < capsule.right)
        assertEquals(4, rule.onAllNodes(isSelectable()).fetchSemanticsNodes().size)
        val home = rule.onAllNodes(isSelectable())[0].fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("dock-selection").performTouchInput {
            down(center); moveBy(Offset(bounds.center.x - home.center.x, 0f), 300); up()
        }
        rule.waitForIdle()
        assertTrue(requests.isEmpty())
        assertEquals(0, imports)
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        action.performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, imports); assertTrue(requests.isEmpty()) }
    }

    @get:Rule val rule = createComposeRule()

    @Test
    fun `all icons retain full bounds with a real backdrop host`() {
        rule.setContent {
            DockTestTheme {
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
            DockTestTheme {
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
            DockTestTheme {
                HubDockMotionProvider(HubDestination.HOME) {
                    GlassDock(onImportDocument = {}, onSelect = requested::add)
                }
            }
        }
        rule.onAllNodes(isSelectable())[3].performClick()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(HubDestination.SETTINGS), requested) }
    }

    @Test fun `selection animates to owner and rejected navigation returns to owner`() {
        val selected = mutableStateOf(HubDestination.HOME)
        val requests = mutableListOf<HubDestination>()
        rule.setContent { DockTestTheme { HubDockMotionProvider(selected.value) {
            GlassDock(onImportDocument = {}, onSelect = requests::add)
        } } }
        val initial = rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot
        rule.onAllNodes(isSelectable())[3].performClick()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        assertEquals(initial, rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { selected.value = HubDestination.SETTINGS }
        rule.waitForIdle()
        val final = rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot
        assertEquals(rule.onAllNodes(isSelectable())[3].fetchSemanticsNode().boundsInRoot.center.x, final.center.x, 1f)
        assertTrue(final.left > initial.left)
        assertEquals(listOf(HubDestination.SETTINGS), requests)
    }

    @Test fun `selected lens drags once on release and cancel never navigates`() {
        val selected = mutableStateOf(HubDestination.HOME)
        val requests = mutableListOf<HubDestination>()
        rule.setContent { DockTestTheme { HubDockMotionProvider(selected.value) {
            GlassDock(onImportDocument = {}, onSelect = { requests.add(it); selected.value = it })
        } } }
        val lens = rule.onNodeWithTag("dock-selection")
        lens.performTouchInput { down(center); moveBy(Offset(width * 4f, 0f), 300); cancel() }
        rule.waitForIdle()
        assertTrue(requests.isEmpty())
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        lens.performTouchInput { down(center); moveBy(Offset(width * 4f, 0f), 300); up() }
        rule.waitForIdle()
        assertEquals(listOf(HubDestination.SETTINGS), requests)
        rule.onAllNodes(isSelectable())[3].assertIsSelected()
    }

    @Test fun `rtl lens drag follows the physical finger direction`() {
        val selected = mutableStateOf(HubDestination.HOME)
        rule.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            DockTestTheme { HubDockMotionProvider(selected.value) {
                GlassDock(onImportDocument = {}, onSelect = { selected.value = it })
            } }
        } }
        assertEquals(rule.onAllNodes(isSelectable())[0].fetchSemanticsNode().boundsInRoot.center.x,
            rule.onNodeWithTag("dock-selection").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        rule.onNodeWithTag("dock-selection").performTouchInput {
            down(center); moveBy(Offset(-width * 4f, 0f), 300); up()
        }
        rule.waitForIdle()
        rule.onAllNodes(isSelectable())[3].assertIsSelected()
    }

    @Test fun `RiseDiary lens grows while held and returns after cancellation`() {
        rule.setContent { DockTestTheme { HubDockMotionProvider(HubDestination.HOME) {
            GlassDock(onImportDocument = {}, onSelect = {})
        } } }
        val lens = rule.onNodeWithTag("dock-selection")
        val initial = lens.fetchSemanticsNode().boundsInRoot
        lens.performTouchInput { down(center); advanceEventTime(800) }
        rule.mainClock.advanceTimeBy(800)
        val held = lens.fetchSemanticsNode().boundsInRoot
        assertTrue(held.width > initial.width * 1.35f)
        assertTrue(held.height > initial.height * 1.35f)
        lens.performTouchInput { cancel() }
        rule.waitForIdle()
        assertEquals(initial, lens.fetchSemanticsNode().boundsInRoot)
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
    }
}

@androidx.compose.runtime.Composable
private fun DockTestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    M1racleReaderTheme {
        CompositionLocalProvider(com.m1racle.reader.ui.material.LocalGlassCapability provides
            com.m1racle.reader.ui.material.GlassCapability.BLUR_ONLY) { content() }
    }
}
