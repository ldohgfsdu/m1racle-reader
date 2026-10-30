package com.m1racle.reader.documents

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.ImportResult
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.ui.documents.MarkdownEditorViewModel
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Preserve Android's replace-existing atomic rename semantics on Windows,
// using the same primitive adapter as MarkdownDraftStoreTest.
@Config(sdk = [34], application = Application::class, shadows = [AndroidAtomicRenameShadow::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MarkdownEditorDraftRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()

    @Test fun editorCannotMountOriginalWhileMatchingDraftIsStillLoading() = recover(conflict = false)
    @Test fun editorCannotMountOriginalWhileConflictingDraftIsStillLoading() = recover(conflict = true)

    private fun recover(conflict: Boolean) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = DocumentRepository(app, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()))
        val original = "\uFEFF# 原文\r\n\r\n书库原文不变。\n"
        val draft = "\uFEFF# 未保存草稿\r\n\r\n恢复这份修改。\n"
        val source = temporary.newFile("draft-recovery.md").apply { writeText(original) }
        val imported = repository.importDocument(Uri.fromFile(source)) as ImportResult.Success
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val model = MarkdownEditorViewModel(app, imported.document.id, repository)
        val field = MarkdownEditorViewModel::class.java.getDeclaredField("drafts").apply { isAccessible = true }
        val store = field.get(model) as MarkdownDraftStore
        try {
            store.write(MarkdownDraftStore.Draft(if (conflict) "older-base" else imported.document.contentSha256!!, draft))
            // Hold the actual disk reader after source loading but before the
            // restored value can publish. No timing assumption about IO speed.
            synchronized(store) {
                await("source staging") { model.value.text == original }
                assertTrue(model.busy)
                assertNull("the WebView must not mount until the draft is resolved", model.document)
            }
            await("draft publication") { model.document != null && !model.busy }
            assertEquals(draft, model.value.text)
            assertEquals(conflict, model.draftConflict)
            assertTrue(model.dirty)
            assertEquals(original, source.readText())
            if (!conflict) {
                val saved = draft + "保存快照"
                val later = saved + "，随后到达的输入"
                model.change(TextFieldValue(saved))
                var savedAcknowledgements = 0
                var completed = 0
                model.finish(onSaved = { savedAcknowledgements++ }, onDone = { completed++ })
                model.change(TextFieldValue(later))
                await("snapshot save") { !model.busy }
                val updated = repository.getDocumentById(imported.document.id)!!
                assertEquals(saved, repository.readDocumentText(updated.uri, updated.contentSha256).getOrThrow())
                assertEquals(later, model.value.text)
                assertTrue(model.dirty)
                assertEquals(1, savedAcknowledgements)
                assertEquals("late input keeps the current mode open", 0, completed)
                assertEquals("已保存，仍有未保存修改", model.message)
                try {
                    await("later draft against the saved revision") {
                        store.read()?.let { it.text == later && it.baseHash == updated.contentSha256 } == true
                    }
                } catch (error: AssertionError) {
                    val writer = MarkdownEditorViewModel::class.java.getDeclaredField("draftWriter").apply { isAccessible = true }.get(model)
                    throw AssertionError("draft=${store.read()}, expectedHash=${updated.contentSha256}, message=${model.message}, writer=$writer, channel=${MarkdownEditorViewModel::class.java.getDeclaredField("draftChanges").apply { isAccessible = true }.get(model)}", error)
                }
                model.finish(onSaved = { savedAcknowledgements++ }, onDone = { completed++ })
                await("finish returns to the same reader") { completed == 1 && !model.busy }
                assertFalse(model.dirty)
                model.finish(onSaved = { savedAcknowledgements++ }, onDone = { completed++ })
                assertEquals("unchanged finish exits without another write", 2, completed)
                assertEquals(2, savedAcknowledgements)
                val finalText = later + "，旋转前最后输入"
                // Clear the actual inline owner while IO is blocked. The final
                // flush must outlive viewModelScope and run after older writes.
                synchronized(store) {
                    model.change(TextFieldValue(finalText))
                    androidx.lifecycle.ViewModelStore().apply { put("inline", model); clear() }
                }
                await("final draft survives clearing inline owner") { store.read()?.text == finalText }
                assertEquals(original, source.readText())
            }
        } finally {
            model.viewModelScope.cancel()
            scheduler.runCurrent()
            store.delete()
            Dispatchers.resetMain()
            db.close()
        }
    }

    private fun await(stage: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("timed out waiting for $stage", condition())
    }
}
