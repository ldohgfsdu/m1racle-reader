package com.m1racle.reader.documents

import com.m1racle.reader.ui.documents.MarkdownEditorOrigin
import org.junit.Assert.*
import org.junit.Test

class MarkdownEditorOriginTest {
    @Test fun localBundleOnly() {
        assertEquals("markdown-editor/index.html", MarkdownEditorOrigin.assetPath("https://reader-markdown.local/index.html"))
        assertEquals("markdown-editor/assets/editor.js", MarkdownEditorOrigin.assetPath("https://reader-markdown.local/assets/editor.js"))
    }

    @Test fun rejectsPathsOutsideBundleAndTraversal() {
        for (path in listOf("/document.md", "/assets/../index.html", "/assets/%2e%2e/index.html", "/assets/%2e%2e/%2e%2e/private", "/assets/%5c..%5csecret")) {
            assertNull(path, MarkdownEditorOrigin.assetPath("https://reader-markdown.local$path"))
        }
    }

    @Test fun noExternalOrFileOriginsAcquireTheBridge() {
        for (url in listOf("https://example.org/assets/editor.js", "http://reader-markdown.local/index.html", "file:///assets/editor.js", "content://reader-markdown.local/assets/editor.js", "https://user@reader-markdown.local/index.html", "https://reader-markdown.local:443/index.html")) {
            assertNull(url, MarkdownEditorOrigin.assetPath(url))
        }
    }
}
