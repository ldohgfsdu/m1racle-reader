package com.m1racle.reader.pdf.edit

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PdfSourceWriteStoreTest {
    @get:Rule val files=TemporaryFolder()
    private fun store()=PdfSourceWriteStore(File(files.root,"journal"),{ File(it).inputStream() },{ File(it).outputStream() })
    private fun file(text:String)=files.newFile().apply { writeText(text) }

    @Test fun mismatchedSourceIsNeverTruncated() {
        val original=file("outside edit"); val candidate=file("candidate")
        assertThrows(IOException::class.java) { store().prepare("doc",original.path,"wrong",candidate) }
        assertEquals("outside edit",original.readText()); assertTrue(store().pending().isEmpty())
    }
    @Test fun successfulWriteAndCommitKeepTheNewBytes() {
        val original=file("original"); val candidate=file("new bytes"); val store=store()
        val ticket=store.prepare("doc",original.path,PdfSourceWriteStore.sha256(original),candidate)
        store.replace(ticket) {}; assertEquals("new bytes",original.readText())
        store().recover(store().pending().single(),committed=true)
        assertEquals("new bytes",original.readText()); assertTrue(store.pending().isEmpty())
    }
    @Test fun restartBeforeDatabaseCommitRestoresOriginal() {
        val original=file("original"); val candidate=file("new bytes"); val store=store()
        val ticket=store.prepare("doc",original.path,PdfSourceWriteStore.sha256(original),candidate)
        store.replace(ticket) {}
        store().recover(store().pending().single(),committed=false)
        assertEquals("original",original.readText()); assertTrue(store.pending().isEmpty())
    }
    @Test fun interruptedPartialWriteIsRecoveredAfterRestart() {
        val original=file("original"); val candidate=file("new bytes"); val store=store()
        store.prepare("doc",original.path,PdfSourceWriteStore.sha256(original),candidate)
        original.writeText("new")
        store().recover(store().pending().single(),committed=false)
        assertEquals("original",original.readText())
    }
    @Test fun laterOutsideChangesAreNotClobberedDuringRecovery() {
        val original=file("original"); val candidate=file("new bytes"); val store=store()
        val ticket=store.prepare("doc",original.path,PdfSourceWriteStore.sha256(original),candidate)
        original.writeText("another application changed this")
        assertThrows(IOException::class.java) { store().recover(store().pending().single(),false) }
        assertEquals("another application changed this",original.readText())
        assertEquals("original",ticket.backup.readText())
    }
    @Test fun secondConflictCheckRunsBeforeOpeningTheWriter() {
        val original=file("original"); val candidate=file("new bytes"); val store=store()
        val ticket=store.prepare("doc",original.path,PdfSourceWriteStore.sha256(original),candidate)
        original.writeText("changed before write"); var started=false
        assertThrows(IOException::class.java) { store.replace(ticket) { started=true } }
        assertFalse(started); assertEquals("changed before write",original.readText())
    }
}
