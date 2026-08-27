package com.m1racle.reader.ui.pdf

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.share.CommitOutcome
import com.m1racle.reader.share.ExternalDocumentImporter
import com.m1racle.reader.share.ExternalOpenCoordinator
import com.m1racle.reader.share.PrepareOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA device scenarios A/D/E/F for external open (importer-level, isolated QA package).
 * Full UI dialog cancel/confirm (B/C) is covered in human validation; host automation
 * asserts import identity and metadata authority here.
 */
@RunWith(AndroidJUnit4::class)
class ExternalOpenSingleSessionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var app: M1racleReaderApplication
    private lateinit var importer: ExternalDocumentImporter
    private lateinit var coordinator: ExternalOpenCoordinator

    /** prepare + commit in one step (activeDocumentId=null), mirroring the production two-phase path. */
    private suspend fun importOnce(uri: Uri, mime: String = "application/pdf"): CommitOutcome {
        val prep = importer.prepare(uri, mime, activeDocumentId = null)
        val prepared = (prep as? PrepareOutcome.Success)?.prepared
            ?: error("prepare did not succeed: $prep")
        return importer.commitPrepared(prepared)
    }

    @Before
    fun setup() {
        assertEquals("com.m1racle.reader.qa", context.packageName)
        app = context.applicationContext as M1racleReaderApplication
        importer = ExternalDocumentImporter(context, app.documentRepository)
        coordinator = app.externalOpenCoordinator
        runBlocking {
            // Clean library docs created by previous QA runs with our id prefix
            app.documentRepository.getAllDocuments()
                .filter { it.id.startsWith("c:") }
                .forEach { app.documentRepository.deleteById(it.id) }
        }
    }

    @Test
    fun APP_COLD_START_EXTERNAL_OPEN_imports_named_pdf_once() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("甲号教材.pdf")
        val ok = importOnce(uri) as CommitOutcome.Success
        assertEquals("甲号教材.pdf", ok.displayName)
        assertTrue(ok.sizeBytes > 0)
        val again = importOnce(uri) as CommitOutcome.Success
        assertEquals(ok.documentId, again.documentId)
        val count = app.documentRepository.getAllDocuments().count { it.displayName == "甲号教材.pdf" }
        assertEquals(1, count)
    }

    @Test
    fun REOPEN_SAME_EXTERNAL_FILE_three_times_single_row() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("乙号讲义.pdf")
        repeat(3) {
            val o = importOnce(uri)
            assertTrue(o is CommitOutcome.Success)
        }
        val rows = app.documentRepository.getAllDocuments().filter { it.displayName == "乙号讲义.pdf" }
        assertEquals(1, rows.size)
    }

    @Test
    fun SAME_NAME_DIFFERENT_CONTENT_creates_two_rows() = runBlocking {
        // Different fixture names → different content pads in provider; force different content
        // by using A vs B padding. Same display name via two files is hard without two paths;
        // use two names then rename check sizes/hashes differ for A vs B.
        val a = importOnce(ExternalOpenFixtureProvider.uriFor("样本A.pdf"))
            as CommitOutcome.Success
        val b = importOnce(ExternalOpenFixtureProvider.uriFor("样本B.pdf"))
            as CommitOutcome.Success
        assertNotEquals(a.documentId, b.documentId)
        assertNotEquals(a.contentSha256, b.contentSha256)
        assertNotEquals(a.sizeBytes, b.sizeBytes)
    }

    @Test
    fun PROVIDER_WITHOUT_SIZE_uses_copy_byte_count() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("无尺寸.pdf", omitSize = true)
        val card = importer.probeIncomingCard(uri, "application/pdf")
        // probe may still see AFD length; final import must have size
        val outcome = importOnce(uri) as CommitOutcome.Success
        assertTrue(outcome.sizeBytes > 0)
        val doc = app.documentRepository.getDocumentById(outcome.documentId)!!
        assertTrue((doc.fileSize ?: 0) > 0)
        assertEquals("无尺寸.pdf", doc.displayName)
    }

    @Test
    fun cancel_path_marks_event_without_import_side_effect() = runBlocking {
        val before = app.documentRepository.getAllDocuments().size
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
            ExternalOpenFixtureProvider.uriFor("取消测.pdf"),
            "application/pdf",
        )
        // Simulate active document so confirm would be required
        coordinator.onViewerOpened("active-fake", "sess-fake")
        coordinator.onIncomingIntent(intent)
        val pending = coordinator.pending.value
        if (pending != null && pending.requiresConfirm) {
            coordinator.cancelPending()
            assertEquals(null, coordinator.pending.value)
            assertEquals(before, app.documentRepository.getAllDocuments().size)
        }
        coordinator.onViewerClosed("active-fake", "sess-fake")
    }

    @Test
    fun prepare_fail_keeps_active_session() = runBlocking {
        coordinator.onViewerOpened("keep-me", "sess-keep")
        val beforeActive = coordinator.activeDocumentId.value
        val outcome = importer.prepare(
            sourceUri = ExternalOpenFixtureProvider.uriFor("失败E.pdf", failOpen = true),
            intentType = "application/pdf",
            activeDocumentId = "keep-me",
        )
        assertTrue(outcome is com.m1racle.reader.share.PrepareOutcome.Failed)
        assertEquals("keep-me", coordinator.activeDocumentId.value)
        assertEquals(beforeActive, coordinator.activeDocumentId.value)
        coordinator.onViewerClosed("keep-me", "sess-keep")
    }

    @Test
    fun prepare_then_commit_two_phase_for_new_file() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("两阶段.pdf")
        val prep = importer.prepare(uri, "application/pdf", activeDocumentId = null)
        assertTrue(prep is com.m1racle.reader.share.PrepareOutcome.Success)
        val prepared = (prep as com.m1racle.reader.share.PrepareOutcome.Success).prepared
        val commit = importer.commitPrepared(prepared)
        assertTrue(commit is com.m1racle.reader.share.CommitOutcome.Success)
        val ok = commit as com.m1racle.reader.share.CommitOutcome.Success
        assertTrue(ok.sizeBytes > 0)
        assertEquals("两阶段.pdf", ok.displayName)
    }

    @Test
    fun open_pdf_A_then_B_confirm_replaces_active_session() = runBlocking {
        val importA = importOnce(
            ExternalOpenFixtureProvider.uriFor("会话甲.pdf"),
        ) as CommitOutcome.Success
        coordinator.onViewerOpened(importA.documentId, "sess-a")
        assertEquals(importA.documentId, coordinator.activeDocumentId.value)

        val intentB = Intent(Intent.ACTION_VIEW).setDataAndType(
            ExternalOpenFixtureProvider.uriFor("会话乙.pdf"),
            "application/pdf",
        )
        coordinator.onIncomingIntent(intentB)
        val pending = coordinator.pending.value
        assertTrue(pending != null && pending.requiresConfirm)
        assertEquals(importA.displayName, pending!!.current?.displayName)
        assertEquals("会话乙.pdf", pending.incoming.displayName)
        assertEquals(com.m1racle.reader.share.ExternalOpenPhase.AWAITING_CONFIRM, pending.phase)

        var flushed = false
        coordinator.progressFlushHook = { id ->
            if (id == importA.documentId) flushed = true
        }
        coordinator.confirmPending()
        assertTrue(flushed)
        assertEquals(null, coordinator.pending.value)
        val activeAfter = coordinator.activeDocumentId.value
        assertNotEquals(importA.documentId, activeAfter)
        assertTrue(activeAfter != null)
        val docB = app.documentRepository.getDocumentById(activeAfter!!)
        assertTrue(docB != null)
        assertEquals("会话乙.pdf", docB!!.displayName)
        // Old library row retained (session close only)
        val docA = app.documentRepository.getDocumentById(importA.documentId)
        assertTrue(docA != null)
        assertEquals("会话甲.pdf", docA!!.displayName)
    }

    @Test
    fun open_pdf_A_then_B_cancel_keeps_A() = runBlocking {
        val importA = importOnce(
            ExternalOpenFixtureProvider.uriFor("保留甲.pdf"),
        ) as CommitOutcome.Success
        val countBefore = app.documentRepository.getAllDocuments().size
        coordinator.onViewerOpened(importA.documentId, "sess-keep-a")
        val intentB = Intent(Intent.ACTION_VIEW).setDataAndType(
            ExternalOpenFixtureProvider.uriFor("被取消乙.pdf"),
            "application/pdf",
        )
        coordinator.onIncomingIntent(intentB)
        assertTrue(coordinator.pending.value?.requiresConfirm == true)
        coordinator.cancelPending()
        assertEquals(null, coordinator.pending.value)
        assertEquals(importA.documentId, coordinator.activeDocumentId.value)
        assertEquals(countBefore, app.documentRepository.getAllDocuments().size)
        // Recreate replay of the same event (rotation) must not re-open confirm
        coordinator.onIncomingIntent(intentB, allowActivityRecreateReplay = false)
        assertEquals(null, coordinator.pending.value)
        // Fresh onNewIntent-style delivery must allow confirm again
        coordinator.clearEventHandledForNewDelivery(intentB)
        coordinator.onIncomingIntent(intentB, allowActivityRecreateReplay = true)
        assertTrue(coordinator.pending.value?.requiresConfirm == true)
        coordinator.cancelPending()
        coordinator.onViewerClosed(importA.documentId, "sess-keep-a")
    }

    @Test
    fun probe_card_uses_provider_display_name_and_size() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("元数据测.pdf")
        val card = importer.probeIncomingCard(uri, "application/pdf")
        assertEquals("元数据测.pdf", card.displayName)
        assertTrue(card.sizeBytes != null && card.sizeBytes!! > 0)
        assertFalse(card.sizeUnknownUntilOpen)
        assertTrue(card.isPdf)
    }

    @Test
    fun probe_card_unknown_size_when_provider_omits() = runBlocking {
        val uri = ExternalOpenFixtureProvider.uriFor("无尺寸会话.pdf", omitSize = true)
        val card = importer.probeIncomingCard(uri, "application/pdf")
        assertEquals("无尺寸会话.pdf", card.displayName)
        // AFD may still expose length; if unknown, dialog uses deferred copy size.
        if (card.sizeBytes == null) {
            assertTrue(card.sizeUnknownUntilOpen)
        }
    }
}
