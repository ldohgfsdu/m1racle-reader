package com.m1racle.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Large-file IO contract: local files stay random-access and scratch stays app-owned. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfBoxRuntimeLargeFileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetRuntime() {
        File(context.cacheDir, PdfBoxRuntime.SCRATCH_DIR_NAME)
            .takeIf(File::exists)
            ?.deleteRecursively()
        val readyField = PdfBoxRuntime::class.java.getDeclaredField("ready").apply {
            isAccessible = true
        }
        (readyField.get(PdfBoxRuntime) as AtomicBoolean).set(false)
        PdfBoxRuntime::class.java.getDeclaredField("appContext").apply {
            isAccessible = true
            set(PdfBoxRuntime, null)
        }
    }

    @Test
    fun memorySettingUsesTheAppScratchDirectory() {
        check(PdfBoxRuntime.ensureInit(context))
        val setting = PdfBoxRuntime.memorySetting(context)
        val scratch = PdfBoxRuntime.scratchDir(context)
        assertNotNull(scratch)
        assertTrue(setting.useTempFile())
        assertFalse(setting.useMainMemory())
        assertEquals(scratch, setting.tempDir)
        assertTrue(scratch!!.path.startsWith(context.cacheDir.path))
        assertEquals(PdfBoxRuntime.SCRATCH_DIR_NAME, scratch.name)
        assertEquals(scratch, PdfBoxRuntime.memorySetting().tempDir)
    }

    @Test
    fun localFileOpenDoesNotCopyTheSourceIntoScratch() {
        check(PdfBoxRuntime.ensureInit(context))
        val source = File(context.cacheDir, "direct-read-source.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(source)
        }
        val scratch = checkNotNull(PdfBoxRuntime.scratchDir(context))
        val before = scratch.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

        PdfBoxRuntime.open(context, Uri.fromFile(source)).use { opened ->
            assertEquals(1, opened.numberOfPages)
        }

        val created = scratch.walkTopDown().filter { it.isFile && it.absolutePath !in before }.toList()
        assertTrue(
            "Opening a local file must not stage a full copy: ${created.map { it.name }}",
            created.none { it.length() >= source.length() },
        )
    }

    @Test
    fun noArgumentSettingRequiresInitializedApplicationContext() {
        assertThrows(IllegalStateException::class.java) {
            PdfBoxRuntime.memorySetting()
        }
    }

    @Test
    fun unusableScratchFailsInitializationWithIoException() {
        val scratch = File(context.cacheDir, PdfBoxRuntime.SCRATCH_DIR_NAME)
        if (scratch.exists()) scratch.deleteRecursively()
        scratch.parentFile?.mkdirs()
        check(scratch.createNewFile())

        try {
            assertThrows(IOException::class.java) {
                PdfBoxRuntime.ensureInit(context)
            }
        } finally {
            scratch.delete()
        }
    }
}
