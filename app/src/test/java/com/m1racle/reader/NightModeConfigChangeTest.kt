package com.m1racle.reader

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Switching system night mode used to recreate [MainActivity], remount the
 * PDF/Markdown viewer, and land on page 1. The activity must keep uiMode
 * in-process so reading position and renderer session stay put.
 */
class NightModeConfigChangeTest {

    @Test
    fun `main activity handles uiMode without recreate`() {
        val manifest = ProductionSource.projectFile("app/src/main/AndroidManifest.xml")
        val text = manifest.readText()
        val activity = text.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        val handledChanges = Regex("android:configChanges=\"([^\"]+)\"")
            .find(activity)?.groupValues?.get(1)?.split('|').orEmpty()
        assertTrue(
            "MainActivity must handle uiMode so night mode does not remount the reader",
            "uiMode" in handledChanges,
        )
        val kotlin = ProductionSource.text("com/m1racle/reader/MainActivity.kt")
        assertTrue(kotlin.contains("override fun onConfigurationChanged"))
        assertTrue(kotlin.contains("applyEdgeToEdge()"))
        assertTrue(kotlin.contains("clearContrastScrim"))
    }
}
