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
        assertFalse(nav.contains("EnterTransition.None"))
    }

    @Test
    fun `dock indicator and press opacity use tokens without scale bounce`() {
        val dock = source("ui/hub/GlassDock.kt")
        val motion = source("ui/hub/DockMotion.kt")
        val shared = source("ui/theme/M1racleMotion.kt")
        assertTrue(dock.contains("M1racleMotion.fast"))
        // The dock is the one surface that moves a real object under the finger, so
        // its two springs live in the shared motion language. Feature code must
        // reference them, never declare its own.
        assertTrue(dock.contains("M1racleMotion.dockPickup"))
        assertTrue(motion.contains("M1racleMotion.dockSelection"))
        assertFalse(dock.contains("spring("))
        assertFalse(motion.contains("spring("))
        assertEquals(2, Regex("= spring\\(").findAll(shared).count())
        assertTrue(dock.contains("alpha = contentAlpha"))
        // Press feedback may scale the capsule, but only as a bounded lift driven by
        // the shared pickup state. Unbounded or spring-driven scaling is a bounce.
        assertTrue(dock.contains("pickup.value"))
        for (match in Regex("scale[XY]\\s*=\\s*1f\\s*\\+\\s*([0-9.]+)f").findAll(dock)) {
            assertTrue(
                "dock press scale ${match.groupValues[1]} exceeds the bounded lift",
                match.groupValues[1].toDouble() <= 0.25,
            )
        }
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
    fun `home import uses the fixed action without floating scale animation`() {
        val home = source("ui/home/HomeScreen.kt")
        assertTrue(home.contains("IconButton(onClick = onImport, enabled = !isImporting)"))
        assertFalse(home.contains("HomeImportFab"))
        assertFalse(home.contains("floatingActionButton"))
        assertFalse(home.contains("scaleX"))
        assertFalse(home.contains("scaleY"))
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
