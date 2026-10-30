package com.m1racle.reader.ui.documents

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.MainActivity
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.data.repository.ImportResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Uses the real launcher, navigation, inline editor, WebView and save path. */
@RunWith(AndroidJUnit4::class)
class MarkdownEditorRotationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun rotationPreservesSelectionBothHistoryBranchesAndExactSource() {
        runBlocking {
        val app = rule.activity.application as M1racleReaderApplication
        assertEquals("com.m1racle.reader.qa", app.packageName)
        val repository = app.documentRepository
        val title = "旋转回归-${System.nanoTime()}"
        val original = buildString {
            append("\uFEFF# $title\r\n\r\n")
            repeat(120) { index ->
                append("第${index + 1}段：选择、撤销和原始换行都应保留。")
                append(listOf("\r\n", "\n", "\r")[index % 3])
            }
        }
        val source = File(app.cacheDir, "$title.md").apply { writeText(original) }
        val document = (repository.importDocument(Uri.fromFile(source)) as ImportResult.Success).document
        try {
            rule.waitUntil(30_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText(title).onFirst().performClick()
            enterEditor()
            rule.onNodeWithText("源码").performClick()
            assertEquals("source", evaluate("document.body.dataset.mode"))

            val activity = rule.activity
            val editor = webView()
            val firstEdit = evaluate("""
                (() => {
                    const view = document.querySelector('.cm-content').cmTile.root.view;
                    view.dispatch({changes: {from: view.state.doc.line(61).from,
                        to: view.state.doc.line(64).from, insert: '旋转前修改\n'}, userEvent: 'input.paste'});
                    return window.ReaderMarkdown.getMarkdown();
                })()
            """) as String
            val secondEdit = evaluate("""
                (() => {
                    const view = document.querySelector('.cm-content').cmTile.root.view;
                    view.dispatch({changes: {from: view.state.doc.length, insert: '第二次修改'}, userEvent: 'input.paste'});
                    return window.ReaderMarkdown.getMarkdown();
                })()
            """) as String
            rule.onNodeWithContentDescription("撤销").performClick()
            assertEquals(firstEdit, markdown())
            evaluate("""
                (() => {
                    const view = document.querySelector('.cm-content').cmTile.root.view;
                    const position = view.state.doc.line(60).from;
                    view.dispatch({selection: {anchor: position + 12, head: position + 3}, scrollIntoView: true});
                    return true;
                })()
            """)
            val selection = evaluate("JSON.stringify(document.querySelector('.cm-content').cmTile.root.view.state.selection.toJSON())")
            rule.onNodeWithContentDescription("撤销").assertIsEnabled()
            rule.onNodeWithContentDescription("重做").assertIsEnabled()

            for ((orientation, expected) in listOf(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
            )) {
                rule.runOnUiThread { activity.requestedOrientation = orientation }
                rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == expected }
                rule.waitForIdle()
                assertSame("rotation must keep the actual editing Activity", activity, rule.activity)
                assertSame("rotation must retain the CodeMirror runtime", editor, webView())
                assertEquals(firstEdit, markdown())
                assertEquals(selection, evaluate("JSON.stringify(document.querySelector('.cm-content').cmTile.root.view.state.selection.toJSON())"))
                assertEquals("source", evaluate("document.body.dataset.mode"))
                assertEquals(true, evaluate("document.querySelector('.cm-scroller').scrollTop > 0"))
                rule.onNodeWithContentDescription("撤销").assertIsEnabled()
                rule.onNodeWithContentDescription("重做").assertIsEnabled()
            }

            // Undo removes an edit spanning CRLF, LF and CR. A text-only
            // restoration or lossy history serialization cannot pass this.
            rule.onNodeWithContentDescription("撤销").performClick()
            assertEquals(original, markdown())
            rule.onNodeWithContentDescription("重做").performClick()
            assertEquals(firstEdit, markdown())
            rule.onNodeWithContentDescription("重做").performClick()
            assertEquals(secondEdit, markdown())
            rule.onNodeWithText("正文").performClick()
            rule.onNodeWithText("完成").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithContentDescription("编辑正文").fetchSemanticsNodes().isNotEmpty()
            }
            val saved = repository.getDocumentById(document.id)!!
            assertEquals(secondEdit, repository.readDocumentText(saved.uri, saved.contentSha256).getOrThrow())
            assertEquals("ordinary finish must leave the source file untouched", original, source.readText())
            enterEditor()
            assertEquals(secondEdit, markdown())
            rule.onNodeWithContentDescription("撤销").assertIsNotEnabled()
            rule.onNodeWithContentDescription("重做").assertIsNotEnabled()
            rule.onNodeWithContentDescription("退出编辑").performClick()
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            repository.deleteById(document.id)
            source.delete()
        }
        }
    }

    private fun enterEditor() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithContentDescription("编辑正文").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("编辑正文").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("完成").fetchSemanticsNodes().isNotEmpty() &&
                rule.runCatching { rule.onNodeWithText("完成").assertIsEnabled() }.isSuccess
        }
    }

    private fun webView(): WebView {
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) repeat(view.childCount) { index -> find(view.getChildAt(index))?.let { return it } }
            return null
        }
        val result = AtomicReference<WebView>()
        rule.runOnUiThread { result.set(find(rule.activity.window.decorView)) }
        return requireNotNull(result.get()) { "Inline Markdown WebView missing" }
    }

    private fun markdown() = evaluate("window.ReaderMarkdown.getMarkdown()") as String

    private fun evaluate(script: String): Any {
        val result = AtomicReference<String>()
        val editor = webView()
        rule.runOnUiThread { editor.evaluateJavascript(script) { result.set(it) } }
        rule.waitUntil(10_000) { result.get() != null }
        return JSONTokener(result.get()).nextValue() ?: JSONObject.NULL
    }
}
