package com.m1racle.reader.ui.material

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBackButtonTest {
    @Test
    fun `reader back button presses the whole control without a separate ripple`() {
        val pdf = source("ui/pdf/PdfViewerScreen.kt")
        val button = pdf.substring(
            pdf.indexOf("private fun FloatingChromeButton("),
            pdf.indexOf("private fun Modifier.capsuleLocalPressHighlight("),
        )
        assertTrue(button.contains("pressed = pressed"))
        assertTrue(button.contains("indication = null"))
        assertFalse(button.contains("ripple"))
        assertTrue(button.indexOf("glassMaterial(") < button.indexOf("Icon("))
        val icon = button.substring(button.indexOf("Icon("))
        assertFalse(icon.contains("scaleX"))
        assertFalse(icon.contains("graphicsLayer"))
    }

    private fun source(relative: String): String = File(readerRoot(), relative).readText()

    private fun readerRoot(): File = listOf(
        File("src/main/java/com/m1racle/reader"),
        File("app/src/main/java/com/m1racle/reader"),
    ).first { it.isDirectory }
}
