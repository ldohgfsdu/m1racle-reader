package com.m1racle.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host checks that production-facing flags / packaging assumptions hold.
 * `testBuildType = "qa"` compiles unit tests against the qa variant, whose
 * READER_PERFORMANCE_EXPERIMENTS_ENABLED is intentionally true; the invariant
 * guarded here is that *only* the qa buildType may enable the switches.
 */
class ReleaseHygieneHostTest {

    @Test
    fun readerPerformanceExperiments_onlyEnabledOnQaBuildType() {
        // defaultConfig is false; only buildType qa overrides it to true.
        val expected = BuildConfig.BUILD_TYPE == "qa"
        assertEquals(
            "QA experiment switches must be enabled only for buildType qa",
            expected,
            BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED,
        )
    }

    @Test
    fun noInternetPermission_inMainManifest() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile)
        val text = manifest.readText()
        assertFalse(
            text.contains("android.permission.INTERNET"),
        )
        assertTrue(text.contains("no INTERNET") || !text.contains("uses-permission"))
    }

    @Test
    fun appDataBackup_isDisabledForLocalOnlyDocuments() {
        val manifest = File("app/src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        val text = manifest.readText()
        assertTrue(text.contains("android:allowBackup=\"false\""))
        assertFalse(text.contains("dataExtractionRules"))
        assertFalse(text.contains("fullBackupContent"))
    }
}
