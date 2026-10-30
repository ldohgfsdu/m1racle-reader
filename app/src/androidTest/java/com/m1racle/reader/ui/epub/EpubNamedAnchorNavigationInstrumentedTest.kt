package com.m1racle.reader.ui.epub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubChapter
import com.m1racle.reader.epub.EpubMetadata
import com.m1racle.reader.epub.EpubSpineItem
import com.m1racle.reader.epub.EpubTocNode
import com.m1racle.reader.epub.EpubXhtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubNamedAnchorNavigationInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun namedChapterFragmentsResolveToClickablePaginatedDirectoryTargets() {
        val preceding = (0 until 20).joinToString("") { "<p>Paragraph $it. " + "Reading text. ".repeat(12) + "</p>" }
        val (blocks, anchors) = EpubXhtmlParser.parse(("<html><body>$preceding" +
            "<p><a id='' name='target'/>Named destination</p></body></html>").toByteArray())
        val node = EpubTocNode("Named target", "chapter.xhtml#target", 0, emptyList())
        val book = EpubBook(EpubMetadata(null, emptyList(), null, null, null, false, false),
            listOf(EpubSpineItem("chapter", "chapter.xhtml", true, 0)), listOf(node),
            listOf(EpubChapter(0, "chapter.xhtml", null, blocks, anchors)))
        var measured by mutableStateOf<EpubTextLayout?>(null)
        var selected: Int? = null
        rule.setContent {
            MaterialTheme {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.bodyLarge
                val rows = remember(book, style) { epubTextRows(book, ReaderSettings(), style) }
                LaunchedEffect(rows, measurer) { measured = measureEpub(rows, measurer, 600, 800, 24f, 24) }
                measured?.let { layout ->
                    EpubTocPanel("Synthetic EPUB", book.toc, epubTocPages(book, layout), 0, { selected = it })
                }
            }
        }
        rule.waitUntil(10_000) { measured != null }
        val expected = requireNotNull(measured!!.pageForToc(book, node))
        assertTrue("The named destination must be beyond the first page", expected > 0)
        rule.onNodeWithText("Named target").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(expected, selected) }
    }
}
