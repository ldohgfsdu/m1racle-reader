package com.m1racle.reader.ui.epub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBlock
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubChapter
import com.m1racle.reader.epub.EpubInline
import com.m1racle.reader.epub.EpubMetadata
import com.m1racle.reader.epub.EpubSpineItem
import com.m1racle.reader.epub.EpubTocNode
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Pagination cost contract: a long book must not retain a layout per row, and
 * slicing the main thread must not change the page list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EpubPaginationHostTest {
    @get:Rule val rule = createComposeRule()

    private val longBook = EpubBook(
        EpubMetadata("长书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("0", "0.xhtml", true, 0), EpubSpineItem("1", "1.xhtml", true, 1)),
        listOf(
            EpubTocNode("第一章", "0.xhtml", 0, listOf(EpubTocNode("小节", "0.xhtml#s1", 0, emptyList()))),
            EpubTocNode("第二章", "1.xhtml", 1, emptyList()),
        ),
        listOf(
            EpubChapter(0, "0.xhtml", "第一章", List(200) {
                EpubBlock.Paragraph(listOf(EpubInline("第一章节 $it " + "内容".repeat(60))))
            }, mapOf("s1" to 5)),
            EpubChapter(1, "1.xhtml", "第二章", List(200) {
                EpubBlock.Paragraph(listOf(EpubInline("第二章节 $it " + "内容".repeat(60))))
            }, emptyMap()),
        ),
    )

    /** One composition per test; both budgets are measured side by side when asked. */
    private fun measure(book: EpubBook, vararg budgets: Long): List<EpubTextLayout> {
        val results = mutableListOf<EpubTextLayout?>()
        rule.setContent {
            M1racleReaderTheme {
                val measurer = rememberTextMeasurer(cacheSize = 0)
                val style = MaterialTheme.typography.bodyLarge
                val rows = remember(book, style) { epubTextRows(book, ReaderSettings(), style) }
                budgets.forEach { budget ->
                    LaunchedEffect(measurer, budget) {
                        val layout = measureEpub(rows, measurer, 1080, 1600, 24f, 24, sliceNanos = budget)
                        synchronized(results) {
                            while (results.size < budgets.size) results.add(null)
                            results[budgets.indexOf(budget)] = layout
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        return results.map { requireNotNull(it) { "measureEpub never completed" } }
    }

    @Test fun `a long book keeps only a bounded window of layouts resident`() {
        val layout = measure(longBook, MEASURE_SLICE_NANOS).single()
        assertEquals(400, layout.rows.size)
        assertTrue(layout.pages.size > 1)
        assertTrue(
            "retained ${layout.retainedLayoutCount} layouts for ${layout.rows.size} rows",
            layout.retainedLayoutCount <= RETAINED_ROW_WINDOW,
        )
        assertTrue(layout.retainedLayoutCount < layout.rows.size)
    }

    @Test fun `an evicted row is re-measured on demand and then cached`() {
        val layout = measure(longBook, MEASURE_SLICE_NANOS).single()
        val lastRow = layout.rows.lastIndex
        val first = layout.layoutFor(lastRow)
        assertTrue(first.lineCount >= 1)
        assertSame(first, layout.layoutFor(lastRow))
        assertTrue(layout.retainedLayoutCount <= RETAINED_ROW_WINDOW)
    }

    @Test fun `yielding between measurement slices does not change the pages`() {
        val (budgeted, unsliced) = measure(longBook, MEASURE_SLICE_NANOS, Long.MAX_VALUE)
        assertEquals(unsliced.pages.size, budgeted.pages.size)
        assertEquals(
            unsliced.pages.map { it.slices.map { s -> s.row to s.start } },
            budgeted.pages.map { it.slices.map { s -> s.row to s.start } },
        )
    }

    @Test fun `every toc node resolves to a page once, including nested anchors`() {
        val layout = measure(longBook, MEASURE_SLICE_NANOS).single()
        val pages = epubTocPages(longBook, layout)
        assertEquals(3, pages.size)
        val expected = listOf(
            tocKey(longBook.toc[0]),
            tocKey(longBook.toc[0].children[0]),
            tocKey(longBook.toc[1]),
        )
        assertEquals(expected.toSet(), pages.keys)
        assertTrue(pages.values.all { it in layout.pages.indices })
        assertTrue(pages.getValue(expected[1]) >= pages.getValue(expected[0]))
    }

    @Test fun `image bounds probing stays off the main thread and tolerates missing files`() {
        val rows = epubTextRows(longBook, ReaderSettings(), androidx.compose.ui.text.TextStyle.Default)
        val bounds = runBlocking { probeEpubImageBounds(rows) }
        assertNotNull(bounds)
        assertTrue(bounds.isEmpty())
    }

    @Test fun `toc fragment resolves against the real spine, not the filtered chapter ordinal`() {
        val book = EpubBook(
            EpubMetadata("Book", emptyList(), null, null, "3.0", false, false),
            List(3) { EpubSpineItem("$it", "$it.xhtml", it != 1, it) }, emptyList(),
            List(3) { index -> EpubChapter(index, "$index.xhtml", "Chapter $index",
                List(2) { EpubBlock.Paragraph(listOf(EpubInline("text"))) }, mapOf("target" to 1)) },
        )
        val layout = measure(book, MEASURE_SLICE_NANOS).single()
        assertEquals(4, layout.rows.size)
        // The linear chapter is filtered out, so ordinal 2 must resolve to spine 2.
        assertEquals(1, layout.pageForToc(book, EpubTocNode("chapter", "2.xhtml", 2, emptyList())))
        assertEquals(1, layout.pageForToc(book, EpubTocNode("target", "2.xhtml#target", 2, emptyList())))
        assertNull(layout.pageForToc(book, EpubTocNode("nonlinear", "1.xhtml", 1, emptyList())))
    }
}
