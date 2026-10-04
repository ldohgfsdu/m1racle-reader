package com.m1racle.reader.ui.epub

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.EpubTextAnchorResolver
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBlock
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubChapter
import com.m1racle.reader.epub.EpubInline
import com.m1racle.reader.epub.EpubLocation
import com.m1racle.reader.epub.EpubMetadata
import com.m1racle.reader.epub.EpubReadingAnchors
import com.m1racle.reader.epub.EpubRestoreResult
import com.m1racle.reader.epub.EpubRestoreTier
import com.m1racle.reader.epub.EpubSavedPosition
import com.m1racle.reader.epub.EpubSpineItem
import com.m1racle.reader.epub.readingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paged-reader rows <-> parser coordinates, and typography independence of the anchor. */
class EpubRowIndexTest {

    private fun p(text: String) = EpubBlock.Paragraph(listOf(EpubInline(text)))

    private val book = EpubBook(
        EpubMetadata("书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("a", "a.xhtml", true, 0), EpubSpineItem("b", "b.xhtml", true, 1)),
        emptyList(),
        listOf(
            EpubChapter(0, "a.xhtml", "一", listOf(
                EpubBlock.Heading(1, "第一章"),
                p((0 until 900).joinToString(" ") { "词$it" }),
                EpubBlock.ListBlock(false, List(6) { listOf(EpubInline("条目 $it")) }),
                EpubBlock.Quote(listOf(p("引用一"), EpubBlock.Code("code()"))),
            ), emptyMap()),
            EpubChapter(1, "b.xhtml", "二", List(8) { p("第二章 $it 段") }, emptyMap()),
        ),
    )

    private val variants = listOf(
        ReaderSettings() to TextStyle.Default,
        ReaderSettings(fontScale = ReaderSettings.FONT_SMALL) to TextStyle.Default,
        ReaderSettings(fontScale = ReaderSettings.FONT_LARGE) to TextStyle.Default,
        ReaderSettings(lineHeightScale = ReaderSettings.LINE_COMPACT) to TextStyle.Default,
        ReaderSettings(lineHeightScale = ReaderSettings.MAX_LINE_HEIGHT_SCALE) to TextStyle.Default,
        ReaderSettings(contentPaddingLevel = ContentPaddingLevel.Compact) to TextStyle.Default,
        ReaderSettings(contentPaddingLevel = ContentPaddingLevel.Relaxed) to TextStyle.Default,
        ReaderSettings() to TextStyle(fontFamily = FontFamily.Serif),
        ReaderSettings() to TextStyle(fontFamily = FontFamily.Monospace),
        ReaderSettings(ReaderSettings.FONT_LARGE, ReaderSettings.LINE_RELAXED, ContentPaddingLevel.Relaxed) to
            TextStyle(fontFamily = FontFamily.Serif),
    )

    @Test fun `row text is the reading text and every row offset maps to the same anchor`() {
        val rows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        val index = EpubRowIndex(book, rows)
        // Measurement splits, list item rows and quote children all sit at their reading-text offsets.
        rows.forEach { row ->
            val block = book.chapters.first { it.spineIndex == row.spineIndex }.blocks[row.blockIndex]
            val text = block.readingText()
            assertEquals(text.substring(row.sourceTextStart, row.sourceTextStart + row.text.length), row.text.text)
            val offset = row.sourceTextStart + row.text.length / 2
            val anchor = requireNotNull(index.anchorAt(row.sourceRow, offset))
            assertEquals(EpubLocation(row.spineIndex, row.blockIndex, offset), index.location(row.sourceRow, offset))
            assertEquals(row.sourceRow, index.sourceRow(EpubLocation(row.spineIndex, row.blockIndex, offset)))
            assertEquals(EpubRestoreResult(EpubRestoreTier.Exact, location = EpubLocation(row.spineIndex, row.blockIndex, offset)),
                EpubReadingAnchors.restore(book, EpubSavedPosition(anchor, 0, 0, 0f)))
        }
        assertTrue(rows.count { it.blockIndex == 1 && it.spineIndex == 0 } >= 2)
    }

    @Test fun `font family size line spacing and margins never change anchors or restore and bookmark results`() {
        val referenceRows = epubTextRows(book, ReaderSettings(), TextStyle.Default)
        val reference = EpubRowIndex(book, referenceRows)
        val positions = referenceRows.map { it.sourceRow to it.sourceTextStart + 1 }
        val anchors = positions.map { (row, offset) -> requireNotNull(reference.anchorAt(row, offset)) }
        val bookmarks = anchors.mapIndexed { i, anchor ->
            EpubTextAnchorResolver.projectInBook(anchor, referenceRows[i].spineIndex)
        }
        variants.forEach { (settings, base) ->
            val rows = epubTextRows(book, settings, base)
            // Typography changes styles only; source coordinates and text stay put.
            assertEquals(referenceRows.map { Triple(it.sourceRow, it.sourceTextStart, it.text.text) },
                rows.map { Triple(it.sourceRow, it.sourceTextStart, it.text.text) })
            val index = EpubRowIndex(book, rows)
            positions.forEachIndexed { i, (row, offset) ->
                val anchor = index.anchorAt(row, offset)
                assertEquals("$settings $base", anchors[i], anchor)
                val restored = EpubReadingAnchors.restore(book, EpubSavedPosition(anchors[i], 0, 0, 0f))
                assertEquals(EpubRestoreTier.Exact, restored.tier)
                assertEquals(row, index.sourceRow(restored.location!!))
                assertEquals(offset, restored.location!!.charOffset)
                val bookmark = EpubTextAnchorResolver.resolve(bookmarks[i].locationKind, bookmarks[i].location,
                    bookmarks[i].encoded) as DocumentAnchor.EpubText
                assertEquals(restored, EpubReadingAnchors.restore(book, EpubSavedPosition(bookmark, 0, 0, 0f)))
            }
        }
    }
}
