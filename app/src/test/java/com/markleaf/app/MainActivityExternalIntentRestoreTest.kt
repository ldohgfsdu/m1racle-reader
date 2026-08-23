package com.markleaf.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityExternalIntentRestoreTest {

    @Test
    fun `restored activity still dispatches launch intent to process coordinator`() {
        val source = File("app/src/main/java/com/markleaf/app/MainActivity.kt").takeIf { it.isFile }
            ?: File("src/main/java/com/markleaf/app/MainActivity.kt")
        val onCreate = source.readText()
            .substringAfter("override fun onCreate")
            .substringBefore("setContent")

        assertTrue(onCreate.contains("coordinator.onIncomingIntent"))
        assertTrue(onCreate.contains("allowActivityRecreateReplay = savedInstanceState == null"))
        assertFalse(onCreate.contains("if (savedInstanceState == null)"))
    }
}
