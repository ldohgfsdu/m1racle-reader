package com.m1racle.reader.ui.documents

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.ui.epub.EpubQaHostActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MarkdownWebEditorInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<EpubQaHostActivity>()

    @Test fun offlineEditorPreservesRawSourceAcrossPresentationModes() {
        val original = "\uFEFF# 原文\r\n\r\n**加粗** 与 `code`。\n\n尾段\r换行\n"
        val controller = MarkdownEditorController()
        val error = AtomicReference<String?>(null)
        rule.setContent {
            MarkdownWebEditor(id = "isolated-markdown-webview", initialMarkdown = original,
                sourceMode = false, readOnly = false, dark = false, settings = ReaderSettings.Default,
                controller = controller, onChange = {}, onHistory = { _, _ -> }, onLink = {},
                onError = { error.set(it) }, modifier = Modifier.fillMaxSize())
        }
        rule.waitUntil(30_000) { controller.ready || error.get() != null }
        assertEquals(null, error.get())
        for (source in listOf(true, false, true, false)) {
            val switched = AtomicBoolean(false)
            val snapshot = AtomicReference<String?>(null)
            rule.runOnUiThread {
                controller.setMode(source, accepted = {
                    switched.set(true)
                    controller.snapshot({ snapshot.set(it) }, { error.set("Unexpected composition") })
                }, composing = { error.set("Unexpected composition") })
            }
            rule.waitUntil(10_000) { snapshot.get() != null || error.get() != null }
            assertEquals(null, error.get())
            assertEquals(true, switched.get())
            assertEquals("mode switch must preserve BOM and every newline byte form", original, snapshot.get())
        }
    }
}
