package com.m1racle.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Switching system night mode used to recreate [MainActivity], remount the
 * PDF/Markdown viewer, and land on page 1. The activity must keep uiMode
 * in-process so reading position and renderer session stay put.
 */
class NightModeConfigChangeTest {

    @Test
    fun `main activity handles uiMode without recreate`() {
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        val text = manifest.readText()
        val activity = text.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertTrue(
            "MainActivity must handle uiMode so night mode does not remount the reader",
            activity.contains("android:configChanges=\"uiMode\""),
        )
        val src = File("app/src/main/java/com/m1racle/reader/MainActivity.kt").takeIf { it.isFile }
            ?: File("src/main/java/com/m1racle/reader/MainActivity.kt")
        val kotlin = src.readText()
        assertTrue(kotlin.contains("override fun onConfigurationChanged"))
        assertTrue(kotlin.contains("applyEdgeToEdge()"))
        assertTrue(kotlin.contains("clearContrastScrim"))
    }
}
