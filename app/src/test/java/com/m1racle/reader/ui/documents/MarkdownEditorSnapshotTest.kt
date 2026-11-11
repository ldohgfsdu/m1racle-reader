package com.m1racle.reader.ui.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownEditorSnapshotTest {
    @Test
    fun snapshotWithoutWebView_reportsUnavailableInsteadOfReturning() {
        val controller = MarkdownEditorController()
        var snapshots = 0
        var composing = 0
        var unavailable = 0
        controller.snapshot(
            onSnapshot = { snapshots++ },
            onComposing = { composing++ },
            onUnavailable = { unavailable++ },
        )
        assertEquals(0, snapshots)
        assertEquals(0, composing)
        assertEquals(1, unavailable)
        assertFalse(controller.snapshotPending)
    }

    @Test
    fun detach_clearsPendingSnapshot() {
        val controller = MarkdownEditorController()
        controller.ready = true
        controller.detach()
        assertFalse(controller.ready)
        assertFalse(controller.snapshotPending)
        var unavailable = 0
        controller.snapshot({}, {}, { unavailable++ })
        assertEquals(1, unavailable)
    }
}
