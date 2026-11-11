package com.m1racle.reader

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityExternalIntentRestoreTest {

    @Test
    fun `restored activity still dispatches launch intent to process coordinator`() {
        val onCreate = ProductionSource.text("com/m1racle/reader/MainActivity.kt")
            .substringAfter("override fun onCreate")
            .substringBefore("setContent")

        assertTrue(onCreate.contains("coordinator.onIncomingIntent"))
        assertTrue(onCreate.contains("allowActivityRecreateReplay = savedInstanceState == null"))
        assertFalse(onCreate.contains("if (savedInstanceState == null)"))
    }
}
