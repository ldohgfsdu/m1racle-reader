package com.markleaf.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.markleaf.app.data.local.AppDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QaDataIsolationInstrumentedTest {
    @Test
    fun qaSandboxIsIsolatedAndRoomOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.markleaf.app.qa", context.packageName)

        val database = AppDatabase.getInstance(context).openHelper.writableDatabase
        assertTrue(database.isOpen)

        assertTrue(
            context.getSharedPreferences("qa_data_isolation_probe", Context.MODE_PRIVATE)
                .edit()
                .putString("state", "qa-only")
                .commit(),
        )
        val probe = File(context.filesDir, "qa-data-isolation-probe.txt")
        probe.writeText("applicationId=${context.packageName}\n")
        assertTrue(probe.isFile && probe.length() > 0L)
    }
}
