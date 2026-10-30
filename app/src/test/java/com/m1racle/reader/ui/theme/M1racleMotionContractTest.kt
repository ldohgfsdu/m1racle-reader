package com.m1racle.reader.ui.theme

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 5: chrome motion uses M1racleMotion; no bounce / scale-enter leftovers. */
class M1racleMotionContractTest {

    @Test
    fun `hub content switches directly while the dock owns feedback`() {
        val motion = source("ui/hub/HubTabTransitions.kt")
        val nav = source("ui/navigation/NavGraph.kt")
        assertTrue(motion.contains("EnterTransition.None"))
        assertTrue(motion.contains("ExitTransition.None"))
        assertTrue(nav.contains("HubTabTransitions.enter"))
        assertTrue(nav.contains("HubTabTransitions.exit"))
        assertFalse(motion.contains("spring("))
        assertTrue(nav.contains("enterTransition = { androidx.compose.animation.EnterTransition.None }"))
    }

    @Test
    fun `dock owns optical interaction while navigation owns selection`() {
        val dock = source("ui/hub/GlassDock.kt")
        val motion = source("ui/hub/DockMotion.kt")
        val tabs = source("ui/hub/OpticalDockTabs.kt")
        assertTrue(dock.contains("OpticalDockTabs("))
        assertTrue(dock.contains("selectedSlot = dockSlotForDestination(motion.selected.ordinal)"))
        assertTrue(tabs.contains("awaitEachGesture {"))
        assertTrue(tabs.contains("finally {"))
        assertFalse(motion.contains("Animatable"))
        assertFalse(motion.contains("preview"))
    }

    @Test
    fun `pdf chrome uses one fade without stagger or translation`() {
        val screen = source("ui/pdf/PdfViewerScreen.kt")
        assertTrue(screen.contains("fadeIn(M1racleMotion.normal)"))
        assertTrue(screen.contains("fadeOut(M1racleMotion.normalIn)"))
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

        assertTrue(dock.contains("if (index == 2) DockImportAction(onImportDocument, enabled = !importing, backdrop = importBackdrop)"))
        assertTrue(dock.contains("enabled = !importing"))
        // Production feedback is small, monotonic and bounded to the shared 250ms curve.
        assertTrue(dock.contains("if (pressed) (if (coolapk) QaCoolapk.ImportPressScale else 1.04f) else 1f"))
        assertTrue(dock.contains("if (!coolapk) M1racleMotion.normal"))
        assertFalse(source("ui/hub/OpticalDockTabs.kt").contains("spring("))
        assertTrue(dock.contains("testTag(\"dock-import\")"))
        assertTrue(tabsHaveAnExcludedImportSlot())
        assertFalse(dock.contains("LiquidGlassButton("))
        assertFalse(home.contains("HomeImportFab"))
        assertFalse(home.contains("floatingActionButton"))
    }

    @Test
    fun `more menu uses a short ease out without scaling its text`() {
        val host = source("ui/pdf/ReaderMoreHost.kt")
        val pop = source("ui/pdf/AnchoredNestedPopover.kt")
        assertTrue(host.contains("MoreMorphMotion"))
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("tween<Float>(250, easing = MoreMorphEasing)"))
        assertTrue(host.contains("InlineProgressMs = 250"))
        // The card outline morphs; the text does not scale independently. Independent
        // content scaling was rejected as cheap-looking motion, so it must not return.
        assertFalse(host.contains("scaleX"))
        assertFalse(host.contains("scaleY"))
        assertFalse(host.contains("contentScale"))
        assertTrue(pop.contains("M1racleMotion.slow"))
        assertTrue(pop.contains("M1racleMotion.normalIn"))
    }

    private fun tabsHaveAnExcludedImportSlot() = source("ui/hub/OpticalDockTabs.kt").contains("startSlot != 2")

    private fun source(relative: String): String =
        ProductionSource.text("com/m1racle/reader/$relative")
}
