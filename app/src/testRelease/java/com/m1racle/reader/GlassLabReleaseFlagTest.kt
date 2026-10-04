package com.m1racle.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Compiled only into `testReleaseUnitTest` (src/testRelease), so it reads the
 * real release BuildConfig. Public release must not ship the Glass Lab workbench.
 */
class GlassLabReleaseFlagTest {

    @Test
    fun releaseBuildConfig_disablesGlassLab() {
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertFalse("Glass Lab must be off in release", BuildConfig.GLASS_LAB_ENABLED)
    }
}
