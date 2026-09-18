package com.m1racle.reader.ui.theme

import java.io.File
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
        assertTrue(dock.contains("M1racleMotion.fast"))
        assertTrue(motion.contains("M1racleMotion.normal"))
        assertFalse(motion.contains("spring("))
        assertFalse(dock.contains("scaleX"))
        assertFalse(dock.contains("scaleY"))
        assertTrue(dock.contains("alpha = contentAlpha"))
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
        assertTrue(host.contains("scaleX = contentScale"))
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("dampingRatio = 0.8f"))
        assertTrue(host.contains("stiffness = 450f"))
        assertTrue(pop.contains("M1racleMotion.slow"))
        assertTrue(pop.contains("M1racleMotion.normalIn"))
    }

    private fun source(relative: String): String = listOf(
        File("app/src/main/java/com/m1racle/reader/$relative"),
        File("src/main/java/com/m1racle/reader/$relative"),
    ).first { it.isFile }.readText()
}
