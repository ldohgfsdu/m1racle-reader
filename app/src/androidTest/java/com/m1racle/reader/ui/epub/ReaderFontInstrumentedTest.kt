package com.m1racle.reader.ui.epub

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.settings.ReaderFontRepository
import com.m1racle.reader.data.settings.ReaderSettingsRepository
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFontInstrumentedTest {
    @Test fun privateFontImportDeduplicatesPersistsRejectsBadFilesAndRemovesSelectedFont() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.m1racle.reader.qa", context.packageName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = ReaderFontRepository(context, scope)
        val settings = ReaderSettingsRepository(context)
        val original = settings.settingsFlow.first()
        val source = File(context.cacheDir, "external-font-test.otf")
        val bad = File(context.cacheDir, "bad-font-test.ttf")
        val large = File(context.cacheDir, "large-font-test.ttf")
        try {
            context.assets.open("fonts/NotoSerifCJKsc-Regular.otf").use { input -> source.outputStream().use(input::copyTo) }
            val initial = repository.fonts.first { it != null }.orEmpty()
            val font = repository.import(Uri.fromFile(source))
            assertEquals(font.id, repository.import(Uri.fromFile(source)).id)
            assertEquals(1, repository.fonts.value!!.count { it.id == font.id })
            assertTrue(font.file.canonicalPath.startsWith(context.filesDir.canonicalPath))
            settings.updateImportedFont(font.id)
            source.delete()
            assertTrue(font.file.isFile)
            val reopened = ReaderFontRepository(context, scope).fonts.first { it != null }.orEmpty()
            assertTrue(reopened.any { it.id == font.id })
            assertEquals(font.id, settings.settingsFlow.first().importedFontId)
            bad.writeBytes(byteArrayOf(0, 1, 0, 0, 1, 2, 3))
            try { repository.import(Uri.fromFile(bad)); fail("Damaged sfnt must fail native validation") }
            catch (_: IOException) { }
            RandomAccessFile(large, "rw").use { it.setLength(ReaderFontRepository.MAX_BYTES + 1) }
            try { repository.import(Uri.fromFile(large)); fail("Oversized file must fail before font parsing") }
            catch (failure: IOException) { assertTrue(failure.message!!.contains("50 MB")) }
            if (initial.none { it.id == font.id }) {
                repository.remove(font, settings)
                assertFalse(font.file.exists())
                assertNull(settings.settingsFlow.first().importedFontId)
            }
        } finally {
            settings.updateImportedFont(original.importedFontId)
            source.delete(); bad.delete(); large.delete(); scope.cancel()
        }
    }
}
