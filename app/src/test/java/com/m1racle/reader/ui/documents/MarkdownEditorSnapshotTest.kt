package com.m1racle.reader.ui.documents

import android.content.Context
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownEditorSnapshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun encodedSnapshot(markdown: String): String =
        JSONObject.quote(JSONObject().put("composing", false).put("markdown", markdown).toString())

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

    @Test
    fun timedOutReply_cannotConsumeTheNextRequestOrDropItsSnapshot() {
        val controller = MarkdownEditorController()
        val webView = WebView(context)
        controller.webView = webView
        controller.ready = true
        val snapshots = mutableListOf<String>()
        var unavailable = 0

        controller.snapshot(snapshots::add, {}, { unavailable++ })
        val replyA = requireNotNull(shadowOf(webView).lastEvaluatedJavascriptCallback)

        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        assertEquals(1, unavailable)
        assertFalse(controller.snapshotPending)

        controller.snapshot(snapshots::add, {}, { unavailable++ })
        val replyB = requireNotNull(shadowOf(webView).lastEvaluatedJavascriptCallback)

        replyA.onReceiveValue(encodedSnapshot("old"))
        assertTrue(controller.snapshotPending)
        assertTrue(snapshots.isEmpty())

        replyB.onReceiveValue(encodedSnapshot("new"))
        assertFalse(controller.snapshotPending)
        assertEquals(listOf("new"), snapshots)
        assertEquals(1, unavailable)
    }

    @Test
    fun replyFromReplacedWebView_cannotConsumeTheCurrentRequest() {
        val controller = MarkdownEditorController()
        val first = WebView(context)
        val second = WebView(context)
        controller.webView = first
        controller.ready = true
        var snapshots = emptyList<String>()
        var unavailable = 0

        controller.snapshot({ snapshots = listOf(it) }, {}, { unavailable++ })
        val firstReply = requireNotNull(shadowOf(first).lastEvaluatedJavascriptCallback)

        controller.webView = second
        assertFalse(controller.snapshotPending)
        controller.snapshot({ snapshots = listOf(it) }, {}, { unavailable++ })
        val secondReply = requireNotNull(shadowOf(second).lastEvaluatedJavascriptCallback)

        firstReply.onReceiveValue(encodedSnapshot("stale"))
        assertTrue(controller.snapshotPending)
        assertTrue(snapshots.isEmpty())

        secondReply.onReceiveValue(encodedSnapshot("current"))
        assertFalse(controller.snapshotPending)
        assertEquals(listOf("current"), snapshots)
        assertEquals(0, unavailable)
    }
}
