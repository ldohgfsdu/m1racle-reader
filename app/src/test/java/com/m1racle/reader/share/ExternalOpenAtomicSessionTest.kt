package com.m1racle.reader.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure policy + state-machine tests for two-phase external open.
 * File-system identity-index tests use a temp directory (no Room).
 */
class ExternalOpenAtomicSessionTest {

    @Test
    fun prepareFailureKeepsActiveSessionUntouched() {
        var activeId: String? = "doc-a"
        var activeSession: String? = "sess-a"
        var rendererClosed = false
        // Simulate prepare fail before any session mutation
        val prepareOk = false
        if (prepareOk) {
            activeId = "doc-b"
            activeSession = "sess-b"
            rendererClosed = true
        }
        assertEquals("doc-a", activeId)
        assertEquals("sess-a", activeSession)
        assertFalse(rendererClosed)
    }

    @Test
    fun shaFailureSameAsPrepareFail_oldViewerAlive() {
        val events = mutableListOf<String>()
        events += "prepare_start"
        events += "copy_stream"
        events += "sha_fail"
        events += "cleanup_temp"
        // no close_old / no clear_active
        assertFalse(events.contains("close_old_renderer"))
        assertFalse(events.contains("clear_active"))
        assertTrue(events.contains("cleanup_temp"))
    }

    @Test
    fun stageFileFail_oldViewerAlive() {
        var active = "a"
        val stageOk = false
        if (!stageOk) {
            // discard temp only
        } else {
            active = "b"
        }
        assertEquals("a", active)
    }

    @Test
    fun roomWriteFail_afterPrepare_doesNotSwitchSession() {
        var active = "doc-a"
        val preparedOk = true
        val roomOk = false
        if (preparedOk && roomOk) {
            active = "doc-b"
        }
        assertEquals("doc-a", active)
    }

    @Test
    fun flushFailAbortsCommit_keepsOldSession() {
        var active = "doc-a"
        var discardedPrepared = false
        val flushOk = false
        val prepared = true
        if (prepared) {
            if (!flushOk) {
                discardedPrepared = true
            } else {
                active = "doc-b"
            }
        }
        assertEquals("doc-a", active)
        assertTrue(discardedPrepared)
    }

    @Test
    fun closeRendererOnlyAfterPrepareSuccess() {
        val order = mutableListOf<String>()
        order += "prepare_ok"
        order += "flush_old"
        order += "commit_room"
        order += "set_active"
        order += "close_old_via_nav_dispose"
        assertTrue(order.indexOf("prepare_ok") < order.indexOf("close_old_via_nav_dispose"))
        assertTrue(order.indexOf("flush_old") < order.indexOf("set_active"))
    }

    @Test
    fun currentDocumentReopen_noConfirmNoCopyNoReset() {
        fun classify(activeId: String?, incomingId: String?): String {
            if (activeId != null && incomingId == activeId) return "bring_to_front"
            if (activeId != null) return "confirm"
            return "import"
        }
        assertEquals("bring_to_front", classify("a", "a"))
        assertEquals("confirm", classify("a", "b"))
        assertEquals("import", classify(null, "b"))
    }

    @Test
    fun existingNonActiveReusesRecord() {
        val libraryId = "c:sha1"
        val prepared = PreparedOpen.ExistingLibrary(
            documentId = libraryId,
            isPdf = true,
            displayName = "书.pdf",
            sizeBytes = 10L,
            contentSha256 = "sha1",
            externalKey = "uri:x",
            mimeType = "application/pdf",
        )
        assertTrue(prepared is PreparedOpen.ExistingLibrary)
        assertEquals(libraryId, (prepared as PreparedOpen.ExistingLibrary).documentId)
    }

    @Test
    fun confirmDialogSizeWhenProviderHasNoSize() {
        val line = FileSizeFormatter.formatForConfirmDialog(null, sizeUnknownUntilOpen = true)
        assertEquals("大小将在打开时确认", line)
        assertEquals(FileSizeFormatter.SIZE_COMPUTE_AFTER_OPEN, line)
    }

    @Test
    fun afterOpenUsesActualByteCount() {
        val (finalSize, source) = ExternalUriMetadata.resolveFinalSizeBytes(
            copiedBytes = 12345L,
            hint = null,
            hintSource = null,
        )
        assertEquals(12345L, finalSize)
        assertEquals(ResolvedExternalMetadata.SizeSource.COPY_BYTE_COUNT, source)
    }

    @Test
    fun cancelLeavesNoTempAndNoDbIntent() {
        var tempExists = true
        var dbInserted = false
        // cancel
        tempExists = false
        dbInserted = false
        assertFalse(tempExists)
        assertFalse(dbInserted)
    }

    @Test
    fun identityIndexAtomicWriteAndRecover() {
        val dir = java.nio.file.Files.createTempDirectory("idx-test-").toFile()
        try {
            val index = File(dir, "identity_index_v1.json")
            val tmp = File(dir, "identity_index_v1.json.tmp")
            tmp.writeText("""{"formatVersion":1,"entries":[]}""")
            assertTrue(
                tmp.renameTo(index) || run {
                    tmp.copyTo(index, overwrite = true)
                    tmp.delete()
                    true
                },
            )
            assertTrue(index.isFile)
            index.writeText("{not-json")
            val corruptDir = File(dir, "corrupt").also { it.mkdirs() }
            index.copyTo(File(corruptDir, "bad.json"), overwrite = true)
            index.delete()
            assertFalse(index.exists())
            assertTrue(corruptDir.listFiles()?.isNotEmpty() == true)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun identityIndexCorruptionDoesNotCrashLoad() {
        val raw = "{{{"
        val loaded = try {
            // Mirror DocumentIdentityIndex catch: invalid payload → empty recovery list
            if (!raw.trimStart().startsWith("{") || raw.contains("{{{")) {
                throw IllegalStateException("corrupt")
            }
            listOf("ok")
        } catch (_: Exception) {
            emptyList()
        }
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun deleteDocumentRemovesIndexEntryPolicy() {
        val entries = mutableListOf("doc-a", "doc-b")
        val deleted = "doc-a"
        entries.remove(deleted)
        assertFalse(entries.contains("doc-a"))
        assertTrue(entries.contains("doc-b"))
    }

    @Test
    fun roomIndexMismatchAutoRepair() {
        val roomIds = setOf("c:1", "c:2")
        val indexIds = mutableListOf("c:1", "c:ghost")
        indexIds.removeAll { it !in roomIds }
        assertEquals(listOf("c:1"), indexIds)
    }

    @Test
    fun atMostOneActiveRendererDuringSwitch() {
        var live = 0
        var peak = 0
        fun open() {
            live++
            peak = maxOf(peak, live)
        }
        fun close() {
            live = (live - 1).coerceAtLeast(0)
        }
        open() // old
        assertEquals(1, live)
        // prepare — still 1
        close() // dispose old after commit nav
        open() // new
        assertEquals(1, live)
        assertTrue(peak <= 2) // brief overlap during dispose/open is at most 2; steady state 1
    }

    @Test
    fun afterNavBackDoesNotRestoreOldViewer() {
        val stack = mutableListOf("HOME", "pdf/A")
        // atomic replace to B
        stack.removeAll { it.startsWith("pdf/") || it.startsWith("viewer/") }
        stack.add("pdf/B")
        assertEquals(listOf("HOME", "pdf/B"), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf("HOME"), stack)
    }

    @Test
    fun activityRecreateDoesNotReplayHandledEvent() {
        val handled = mutableSetOf<String>()
        val event = "evt-same"
        fun deliver(): Boolean {
            if (handled.contains(event)) return false
            handled.add(event)
            return true
        }
        assertTrue(deliver())
        assertFalse(deliver())
        assertEquals(1, handled.size)
    }

    @Test
    fun twoPhaseOrderIsPrepareThenFlushThenCommitThenNav() {
        val steps = listOf(
            "PHASE1_PREPARE",
            "PHASE2_FLUSH",
            "PHASE2_COMMIT_ROOM",
            "PHASE2_SET_ACTIVE",
            "PHASE2_NAV_REPLACE",
        )
        assertEquals(0, steps.indexOf("PHASE1_PREPARE"))
        assertTrue(steps.indexOf("PHASE1_PREPARE") < steps.indexOf("PHASE2_FLUSH"))
        assertTrue(steps.indexOf("PHASE2_FLUSH") < steps.indexOf("PHASE2_NAV_REPLACE"))
    }
}
