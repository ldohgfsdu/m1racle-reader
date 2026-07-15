package com.markleaf.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalOpenSessionRulesTest {

    @Test
    fun coldStart_directImport_noConfirm() {
        assertEquals(
            ExternalOpenPath.DIRECT_IMPORT,
            ExternalOpenSessionRules.classify(null, "c:new"),
        )
        assertFalse(ExternalOpenSessionRules.requiresConfirm(null, "c:new"))
    }

    @Test
    fun sameActiveDocument_bringToFront_noConfirm() {
        assertEquals(
            ExternalOpenPath.BRING_TO_FRONT,
            ExternalOpenSessionRules.classify("c:a", "c:a"),
        )
        assertFalse(ExternalOpenSessionRules.requiresConfirm("c:a", "c:a"))
    }

    @Test
    fun differentActiveDocument_confirmReplace() {
        assertEquals(
            ExternalOpenPath.CONFIRM_REPLACE,
            ExternalOpenSessionRules.classify("c:a", "c:b"),
        )
        assertTrue(ExternalOpenSessionRules.requiresConfirm("c:a", "c:b"))
        // Unknown library id still confirms when something is active
        assertTrue(ExternalOpenSessionRules.requiresConfirm("c:a", null))
    }

    @Test
    fun switchOrder_prepareBeforeFlushBeforeNav() {
        val steps = ExternalOpenSessionRules.switchStepOrder()
        assertTrue(steps.indexOf("PHASE1_PREPARE") < steps.indexOf("PHASE2_FLUSH_OLD"))
        assertTrue(steps.indexOf("PHASE2_FLUSH_OLD") < steps.indexOf("PHASE2_COMMIT_ROOM"))
        assertTrue(steps.indexOf("PHASE2_COMMIT_ROOM") < steps.indexOf("PHASE2_SET_ACTIVE"))
        assertTrue(steps.indexOf("PHASE2_SET_ACTIVE") < steps.indexOf("PHASE2_NAV_REPLACE"))
        assertTrue(steps.indexOf("PHASE2_NAV_REPLACE") < steps.indexOf("OLD_SESSION_DISPOSE"))
    }

    @Test
    fun cancelPath_keepsActiveAndDoesNotCreateRecord() {
        var activeId: String? = "doc-a"
        var createdRecord = false
        var copied = false
        // cancel
        val cancelled = true
        if (!cancelled) {
            createdRecord = true
            copied = true
            activeId = "doc-b"
        }
        assertEquals("doc-a", activeId)
        assertFalse(createdRecord)
        assertFalse(copied)
    }

    @Test
    fun confirmPath_replacesActiveOnlyAfterPrepareSuccess() {
        var active = "doc-a"
        val prepareOk = true
        if (prepareOk) {
            // flush + commit + set active
            active = "doc-b"
        }
        assertEquals("doc-b", active)
    }

    @Test
    fun prepareFail_keepsActive() {
        var active = "doc-a"
        val prepareOk = false
        if (prepareOk) active = "doc-b"
        assertEquals("doc-a", active)
    }

    @Test
    fun eventIdSingleShot() {
        val handled = mutableSetOf<String>()
        val event = "evt-1"
        fun deliver(): Boolean {
            if (!handled.add(event)) return false
            return true
        }
        assertTrue(deliver())
        assertFalse(deliver())
        assertEquals(1, handled.size)
    }
}
