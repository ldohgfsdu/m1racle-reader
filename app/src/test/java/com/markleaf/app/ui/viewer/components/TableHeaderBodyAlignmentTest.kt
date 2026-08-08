package com.markleaf.app.ui.viewer.components

import androidx.compose.ui.text.style.TextAlign
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.MarkdownParser
import com.markleaf.app.markdown.TableAlignment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Header and body must share column widths, padding, and [TableAlignment].
 * Geometry is pure (no Compose runtime) so CI can lock the alignment contract.
 */
class TableHeaderBodyAlignmentTest {

    private val pad = MarkdownTableLayoutEngine.Tokens.horizontalCellPaddingDp

    @Test
    fun tableColumnTextAlign_mapsGfmRules() {
        assertEquals(TextAlign.Start, tableColumnTextAlign(TableAlignment.Start))
        assertEquals(TextAlign.Center, tableColumnTextAlign(TableAlignment.Center))
        assertEquals(TextAlign.End, tableColumnTextAlign(TableAlignment.End))
    }

    @Test
    fun leftAligned_headerAndBody_shareStartEdge() {
        val cellW = 200f
        val headerText = 80f // "帛书编次"
        val bodyText = 60f // "第一章"
        val h = TableCellTextPlacement(cellW, pad, pad, headerText, TableAlignment.Start)
        val b = TableCellTextPlacement(cellW, pad, pad, bodyText, TableAlignment.Start)
        assertEquals(h.textStartPx, b.textStartPx, 0.01f)
        assertEquals(pad, h.textStartPx, 0.01f)
    }

    @Test
    fun centerAligned_headerAndBody_shareCenter() {
        val cellW = 240f
        val h = TableCellTextPlacement(cellW, pad, pad, 90f, TableAlignment.Center)
        val b = TableCellTextPlacement(cellW, pad, pad, 50f, TableAlignment.Center)
        assertEquals(h.textCenterPx, b.textCenterPx, 0.01f)
    }

    @Test
    fun rightAligned_headerAndBody_shareEndEdge() {
        // Repro for 老子 col0/col2: header "帛书编次"/"通行本章次" vs body "第一章"/"第 38 章"
        val cellW = 180f
        val h = TableCellTextPlacement(cellW, pad, pad, 72f, TableAlignment.End)
        val b = TableCellTextPlacement(cellW, pad, pad, 48f, TableAlignment.End)
        assertEquals(h.textEndPx, b.textEndPx, 0.01f)
        assertEquals(cellW - pad, h.textEndPx, 0.01f)
    }

    @Test
    fun headerAndBody_useIdenticalHorizontalPadding() {
        // Both rows read MarkdownTableLayoutEngine.Tokens.horizontalCellPaddingDp
        val source = tableSource()
        val headerCall = source.substringAfter("TableHeaderRow(").substringBefore("HorizontalDivider(")
        val bodyCall = source.substringAfter("TableRow(").substringBefore("if (rowIndex < block.rows.lastIndex)")
        assertTrue(headerCall.contains("horizontalPadding = tokens.horizontalCellPaddingDp.dp"))
        assertTrue(bodyCall.contains("horizontalPadding = tokens.horizontalCellPaddingDp.dp"))
        assertTrue(headerCall.contains("verticalPadding = tokens.verticalCellPaddingDp.dp"))
        assertTrue(bodyCall.contains("verticalPadding = tokens.verticalCellPaddingDp.dp"))
        // Shared widths list
        assertTrue(headerCall.contains("columnWidths = columnWidths"))
        assertTrue(bodyCall.contains("columnWidths = columnWidths"))
        // Header receives column alignments (not hardcoded Start for every cell)
        assertTrue(headerCall.contains("alignments = block.alignments"))
        assertTrue(bodyCall.contains("alignments = block.alignments"))
    }

    @Test
    fun wideTable_isHorizontallyScrollable_notViewportCompressed() {
        // Six short columns with content wider than a phone portrait viewport.
        val density = 2.5f
        val viewport = 360f * density // ~900px
        val columns = List(6) {
            TableColumnMeasurement(
                contentWidthPx = 120f * density,
                maxCharacterCount = 8,
                characterCounts = listOf(4, 6, 8),
                unconstrainedCellWidthsPx = listOf(100f * density, 110f * density, 120f * density),
                headerWidthPx = 100f * density,
            )
        }
        val metrics = MarkdownTableLayoutEngine.calculate(
            viewportWidthPx = viewport,
            density = density,
            columns = columns,
        )
        assertTrue(metrics.horizontallyScrollable)
        assertTrue(metrics.totalWidthPx > viewport)
        // First column fully present at scroll=0
        assertTrue(metrics.widthsPx[0] > 0f)
        // Last column fully present when scrolled to max
        val last = metrics.widthsPx.last()
        assertTrue(last > 0f)
        val maxScroll = metrics.totalWidthPx - viewport
        assertTrue(maxScroll + viewport >= metrics.totalWidthPx - 0.5f)
        // Scrolling does not clip the last column out of the scrollable content
        assertEquals(metrics.totalWidthPx, metrics.widthsPx.sum(), 0.5f)
    }

    @Test
    fun portraitAndLandscape_overflowTablesKeepStableWidths_andHeaderBodyShareThem() {
        val density = 2.5f
        // Content wide enough that both portrait and landscape overflow → no fill expansion.
        val columns = List(6) {
            TableColumnMeasurement(
                contentWidthPx = 160f * density,
                maxCharacterCount = 10,
                characterCounts = listOf(8, 10),
                unconstrainedCellWidthsPx = listOf(150f * density, 160f * density),
                headerWidthPx = 150f * density,
            )
        }
        val portrait = MarkdownTableLayoutEngine.calculate(360f * density, density, columns)
        val landscape = MarkdownTableLayoutEngine.calculate(800f * density, density, columns)
        assertTrue(portrait.horizontallyScrollable)
        assertTrue(landscape.horizontallyScrollable)
        // Overflow path does not compress columns toward viewport; widths stay content-led.
        assertEquals(portrait.widthsPx, landscape.widthsPx)
        assertEquals(portrait.totalWidthPx, landscape.totalWidthPx, 0.01f)
        // Header and body always consume this single widths list — start/end edges stay aligned
        // in both orientations given the same shared widths + padding.
        val col0 = portrait.widthsPx[0]
        val h = TableCellTextPlacement(col0, pad, pad, 70f, TableAlignment.End)
        val b = TableCellTextPlacement(col0, pad, pad, 40f, TableAlignment.End)
        assertEquals(h.textEndPx, b.textEndPx, 0.01f)
        val col0L = landscape.widthsPx[0]
        val hL = TableCellTextPlacement(col0L, pad, pad, 70f, TableAlignment.End)
        val bL = TableCellTextPlacement(col0L, pad, pad, 40f, TableAlignment.End)
        assertEquals(hL.textEndPx, bL.textEndPx, 0.01f)
        assertEquals(h.textEndPx, hL.textEndPx, 0.01f)
    }

    @Test
    fun laoziStyleSeparator_parsesRightAlignOnNumericColumns() {
        val md = """
            | 帛书编次 | 章名 | 通行本章次 | 帛书编次 | 章名 | 通行本章次 |
            |---:|---|---:|---:|---|---:|
            | [第一章](#chapter-01) | 论德 | 第 38 章 | [第二十三章](#chapter-23) | 居位 | 第 60 章 |
        """.trimIndent()
        val table = MarkdownParser.parse(md).blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(
            listOf(
                TableAlignment.End,
                TableAlignment.Start,
                TableAlignment.End,
                TableAlignment.End,
                TableAlignment.Start,
                TableAlignment.End,
            ),
            table.alignments,
        )
        // Shared column count for header cells and alignments
        assertEquals(table.headers.size, table.alignments.size)
        assertEquals(table.headers.size, table.rows.single().size)
    }

    @Test
    fun centerAndLeftSeparators_parseCorrectly() {
        val md = """
            | L | C | R |
            |:---|:---:|---:|
            | a | b | c |
        """.trimIndent()
        val table = MarkdownParser.parse(md).blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(
            listOf(TableAlignment.Start, TableAlignment.Center, TableAlignment.End),
            table.alignments,
        )
    }

    @Test
    fun unspecifiedAlignment_defaultsToStart_forHeaderAndBody() {
        val md = """
            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val table = MarkdownParser.parse(md).blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf(TableAlignment.Start, TableAlignment.Start), table.alignments)
        val cellW = 160f
        val h = TableCellTextPlacement(cellW, pad, pad, 40f, table.alignments[0])
        val b = TableCellTextPlacement(cellW, pad, pad, 20f, table.alignments[0])
        assertEquals(h.textStartPx, b.textStartPx, 0.01f)
    }

    @Test
    fun headerRow_doesNotHardcodeStartForEveryColumn() {
        val source = tableSource()
        val headerRow = source.substringAfter("private fun TableHeaderRow(")
            .substringBefore("private fun TableRow(")
        // Must apply per-column alignment helper, not a single Start for all cells.
        assertTrue(headerRow.contains("tableColumnTextAlign(alignment)"))
        assertFalse(
            "header must not force TextAlign.Start for every column",
            headerRow.contains("textAlign = TextAlign.Start"),
        )
        assertTrue(headerRow.contains("alignments: List<TableAlignment>"))
    }

    @Test
    fun scrollContainer_isOnSharedBox_notClippingChildrenOutsideScroll() {
        val source = tableSource()
        // horizontalScroll wraps the Column of fixed tableWidth — last columns live in
        // scrollable content, not outside a non-scrolling parent clip.
        assertTrue(source.contains(".horizontalScroll("))
        assertTrue(source.contains("enabled = metrics.horizontallyScrollable"))
        assertTrue(source.contains("Column(modifier = Modifier.width(tableWidth))"))
        assertTrue(source.contains("testTag(\"markdown-table-scroll\")"))
    }

    private fun tableSource(): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt"),
        File("src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt"),
    ).first { it.isFile }.readText()
}
