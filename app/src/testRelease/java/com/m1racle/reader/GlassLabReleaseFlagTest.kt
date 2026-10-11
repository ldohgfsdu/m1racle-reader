package com.m1racle.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compiled only into `testReleaseUnitTest` (src/testRelease), so it reads the
 * real release BuildConfig. Workbench is now intentionally included in Release.
 */
class GlassLabReleaseFlagTest {

    @Test
    fun releaseContainsWorkbenchWithoutVariantGate() {
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertTrue(Class.forName("com.m1racle.reader.ui.hub.glasslab.GlassLabScreenKt")
            .declaredMethods.any { it.name == "GlassLabScreen" })
        assertFalse(BuildConfig::class.java.declaredFields.any { it.name == "GLASS_LAB_ENABLED" })
    }
}
