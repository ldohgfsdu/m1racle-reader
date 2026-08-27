package com.m1racle.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityExternalIntentRestoreTest {

    @Test
    fun `restored activity still dispatches launch intent to process coordinator`() {
        val source = File("app/src/main/java/com/m1racle/reader/MainActivity.kt").takeIf { it.isFile }
            ?: File("src/main/java/com/m1racle/reader/MainActivity.kt")
        val onCreate = source.readText()
            .substringAfter("override fun onCreate")
            .substringBefore("setContent")

        assertTrue(onCreate.contains("coordinator.onIncomingIntent"))
        assertTrue(onCreate.contains("allowActivityRecreateReplay = savedInstanceState == null"))
        assertFalse(onCreate.contains("if (savedInstanceState == null)"))
    }
}
