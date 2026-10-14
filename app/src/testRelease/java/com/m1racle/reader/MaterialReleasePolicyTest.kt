package com.m1racle.reader

import com.m1racle.reader.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks the real Release BuildConfig and compiled production classes. */
class MaterialReleasePolicyTest {
    @Test
    fun releaseUsesProductionMaterialWithoutWorkbenchOrExperimentSwitches() {
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertFalse(BuildConfig.QA_GLASS_VARIANTS_ENABLED)
        assertFalse(BuildConfig.READER_PERFORMANCE_EXPERIMENTS_ENABLED)
        assertFalse(Routes::class.java.declaredFields.any { it.name == "GLASS_LAB" })
        assertFalse(R.string::class.java.declaredFields.any { it.name.startsWith("glass_lab_") })
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.m1racle.reader.ui.hub.glasslab.GlassLabScreenKt")
        }
        assertFalse(BuildConfig::class.java.declaredFields.any { it.name == "GLASS_LAB_ENABLED" })
    }
}
