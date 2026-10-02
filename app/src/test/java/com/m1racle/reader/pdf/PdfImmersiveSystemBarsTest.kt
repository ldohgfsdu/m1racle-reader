package com.m1racle.reader.pdf

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfImmersiveSystemBarsTest {

    @Test
    fun `immersive reasserts hide only while the status bar is showing`() {
        assertTrue(PdfImmersiveSystemBars.shouldReassertHide(PdfTopBarMode.IMMERSIVE, 80))
        assertFalse(PdfImmersiveSystemBars.shouldReassertHide(PdfTopBarMode.IMMERSIVE, 0))
        assertFalse(PdfImmersiveSystemBars.shouldReassertHide(PdfTopBarMode.NORMAL, 80))
    }

    @Test
    fun `reader immersive re-enters without transient swipe reveal`() {
        val source = ProductionSource.text("com/m1racle/reader/ui/pdf/PdfViewerScreen.kt")
        assertFalse(source.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"))
        assertTrue(source.contains("shouldReassertHide"))
        assertTrue(source.contains("OnWindowFocusChangeListener"))
        assertTrue(source.contains("PdfImmersiveSystemBars.REHIDE_DELAY_MS"))
        assertTrue(source.contains("pdfStatusBarVisiblePx"))
        assertFalse(source.contains("WindowInsets.statusBars.getTop"))
        assertTrue(source.contains("clearContrastScrim"))
    }

    @Test
    fun `theme opts out of status bar contrast scrim`() {
        val theme = ProductionSource.projectText("app/src/main/res/values/themes.xml")
        assertTrue(theme.contains("android:enforceStatusBarContrast\">false"))
        assertTrue(theme.contains("android:enforceNavigationBarContrast\">false"))
    }
}