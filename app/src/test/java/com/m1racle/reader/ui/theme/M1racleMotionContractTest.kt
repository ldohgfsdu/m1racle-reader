package com.m1racle.reader.ui.theme

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 5: chrome motion uses M1racleMotion; no bounce / scale-enter leftovers. */
class M1racleMotionContractTest {

    @Test
    fun `hub tab content uses a short fade and slide without bounce`() {
        val motion = source("ui/hub/HubTabTransitions.kt")
        val nav = source("ui/navigation/NavGraph.kt")
        assertTrue(motion.contains("M1racleMotion.NormalMs"))
        assertTrue(motion.contains("M1racleMotion.FastMs"))
        assertTrue(nav.contains("HubTabTransitions.enter"))
        assertTrue(nav.contains("HubTabTransitions.exit"))
        assertFalse(motion.contains("spring("))
        assertTrue(nav.contains("enterTransition = { androidx.compose.animation.EnterTransition.None }"))
    }

    @Test
    fun `dock uses RiseDiary animation while navigation owns selection`() {
        val dock = source("ui/hub/GlassDock.kt")
        val motion = source("ui/hub/DockMotion.kt")
        val tabs = source("ui/hub/RiseDiaryBottomTabs.kt")
        assertTrue(dock.contains("LiquidBottomTabs("))
        assertTrue(dock.contains("selectedTabIndex = { dockSlotForDestination(motion.selected.ordinal) }"))
        assertTrue(tabs.contains("DampedDragAnimation("))
        assertTrue(tabs.contains("onDragCancelled ="))
        assertFalse(motion.contains("Animatable"))
        assertFalse(motion.contains("preview"))
    }

    @Test
    fun `pdf chrome circles fade and slide without scale-enter leftovers`() {
        val screen = source("ui/pdf/PdfViewerScreen.kt")
        assertTrue(screen.contains("fadeIn(tween(200)) + slideInVertically(tween(200))"))
        assertTrue(screen.contains("fadeOut(tween(180, delayMillis = 100))"))
        // The fade node lives in PdfChromeVisibility (shadow-clip single source);
        // PdfViewerScreen only passes enter/exit specs at the call sites.
        val chrome = source("ui/pdf/PdfChromeVisibility.kt")
        val fadeNode = chrome.substring(chrome.indexOf("AnimatedVisibility("))
        val fadeHead = fadeNode.substring(0, minOf(1200, fadeNode.length))
        assertFalse(fadeHead.contains("scaleIn"))
        assertFalse(fadeHead.contains("expandVertically"))
    }

    @Test
    fun `hub import occupies the central capsule slot with a small press expansion`() {
        val home = source("ui/home/HomeScreen.kt")
        val dock = source("ui/hub/GlassDock.kt")

        assertTrue(dock.contains("if (index == 2) DockImportAction(onImportDocument, enabled = !importing)"))
        assertTrue(dock.contains("enabled = !importing"))
        assertTrue(dock.contains("if (pressed) 1.085f else 1f"))
        assertTrue(dock.contains("spring(0.55f, 450f)"))
        assertTrue(dock.contains("testTag(\"dock-import\")"))
        assertTrue(dock.contains("actionSlot = 2"))
        assertFalse(dock.contains("LiquidGlassButton("))
        assertFalse(home.contains("HomeImportFab"))
        assertFalse(home.contains("floatingActionButton"))
    }

    @Test
    fun `more menu morphs from the circle with the capsule spring`() {
        val host = source("ui/pdf/ReaderMoreHost.kt")
        val pop = source("ui/pdf/AnchoredNestedPopover.kt")
        assertTrue(host.contains("MoreMorphSpring"))
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("dampingRatio = 0.8f"))
        assertTrue(host.contains("stiffness = 450f"))
        // The card outline morphs; the text does not scale independently. Independent
        // content scaling was rejected as cheap-looking motion, so it must not return.
        assertFalse(host.contains("scaleX"))
        assertFalse(host.contains("scaleY"))
        assertFalse(host.contains("contentScale"))
        assertTrue(pop.contains("M1racleMotion.slow"))
        assertTrue(pop.contains("M1racleMotion.normalIn"))
    }

    private fun source(relative: String): String =
        ProductionSource.text("com/m1racle/reader/$relative")
}
