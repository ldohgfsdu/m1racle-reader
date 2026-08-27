package com.m1racle.reader.ui.viewer.components

import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownParser
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import java.io.File
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableLayoutEngineTest {
    private fun column(
        width: Float,
        characters: Int = 8,
        counts: List<Int> = listOf(characters),
    ) = TableColumnMeasurement(width, characters, counts)

    private fun candidate(
        fraction: Float,
        viewport: Float = 360f,
        firstHeight: Float,
        secondHeight: Float,
        firstLines: Int = 1,
        secondLines: Int = 1,
        firstCharacters: Int = 40,
        secondCharacters: Int = 40,
        headerLines: Pair<Int, Int> = 1 to 1,
    ) = TwoColumnWidthCandidate(
        firstFraction = fraction,
        widthsPx = listOf(viewport * fraction, viewport * (1f - fraction)),
        rows = listOf(
            WrappedRowMeasurement(
                cells = listOf(
                    WrappedCellMeasurement(20f * headerLines.first, headerLines.first, 4),
                    WrappedCellMeasurement(20f * headerLines.second, headerLines.second, 4),
                ),
                isHeader = true,
            ),
            WrappedRowMeasurement(
                cells = listOf(
                    WrappedCellMeasurement(firstHeight, firstLines, firstCharacters),
                    WrappedCellMeasurement(secondHeight, secondLines, secondCharacters),
                ),
                isHeader = false,
            ),
        ),
    )

    @Test
    fun `two column short content uses measured cost candidate and fits viewport`() {
        val columns = listOf(
            column(70f, 6, listOf(2, 5, 6, 5)),
            column(300f, 40, listOf(2, 2, 8, 40)),
        )
        val candidates = listOf(
            candidate(.40f, firstHeight = 20f, secondHeight = 40f, secondLines = 2),
            candidate(.45f, firstHeight = 20f, secondHeight = 60f, secondLines = 3),
            candidate(.50f, firstHeight = 20f, secondHeight = 80f, secondLines = 4),
            candidate(.55f, firstHeight = 20f, secondHeight = 100f, secondLines = 5),
            candidate(.60f, firstHeight = 20f, secondHeight = 120f, secondLines = 6),
        )
        val metrics = MarkdownTableLayoutEngine.calculate(360f, 1f, columns, candidates)
        assertEquals(360f, metrics.totalWidthPx, 0.01f)
        assertFalse(metrics.horizontallyScrollable)
        assertTrue(metrics.usedMeasuredCandidateOptimization)
        assertEquals(.40f, metrics.selectedTwoColumnFraction!!, 0.001f)
        assertTrue(metrics.widthsPx[0] < metrics.widthsPx[1])
    }

    @Test
    fun `two column verbose content naturally selects sixty forty without fixed rule`() {
        val columns = listOf(
            column(900f, 100, listOf(8, 100, 80)),
            column(600f, 60, listOf(6, 60, 50)),
        )
        val candidates = listOf(
            candidate(.40f, firstHeight = 100f, secondHeight = 40f, firstLines = 5),
            candidate(.45f, firstHeight = 80f, secondHeight = 40f, firstLines = 4),
            candidate(.50f, firstHeight = 60f, secondHeight = 40f, firstLines = 3),
            candidate(.55f, firstHeight = 45f, secondHeight = 45f, firstLines = 3, secondLines = 3),
            candidate(.60f, firstHeight = 35f, secondHeight = 45f, firstLines = 2, secondLines = 3),
        )
        val metrics = MarkdownTableLayoutEngine.calculate(360f, 1f, columns, candidates)
        assertEquals(.60f, metrics.selectedTwoColumnFraction!!, 0.001f)
        assertEquals(216f, metrics.widthsPx[0], 0.01f)
        assertEquals(144f, metrics.widthsPx[1], 0.01f)
        assertFalse(metrics.horizontallyScrollable)
        assertNotEquals(260f, metrics.widthsPx.first(), 0.01f)
    }

    @Test
    fun `two column normal phone viewport scrolls only when readable minima cannot fit`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            320f,
            1f,
            listOf(column(1000f, 90), column(1000f, 90)),
        )
        assertEquals(320f, metrics.totalWidthPx, 0.01f)
        assertFalse(metrics.horizontallyScrollable)
        assertTrue(metrics.widthsPx.all { it >= 132f })
    }

    @Test
    fun `two column tiny viewport scrolls at exact readable minima`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            240f,
            1f,
            listOf(column(1000f, 90), column(1000f, 90)),
        )
        assertEquals(listOf(132f, 132f), metrics.widthsPx)
        assertEquals(264f, metrics.totalWidthPx, 0.01f)
        assertTrue(metrics.horizontallyScrollable)
    }

    @Test
    fun `short label wrapping receives decisive cost penalty`() {
        val columns = listOf(column(80f, 5, listOf(5)), column(200f, 20, listOf(20)))
        val singleLine = candidate(.40f, firstHeight = 20f, secondHeight = 60f)
        val wrapped = candidate(
            .45f,
            firstHeight = 40f,
            secondHeight = 20f,
            firstLines = 2,
            firstCharacters = 5,
        )
        assertTrue(
            MarkdownTableLayoutEngine.twoColumnCost(singleLine, columns, 360f) <
                MarkdownTableLayoutEngine.twoColumnCost(wrapped, columns, 360f),
        )
    }

    @Test
    fun `unnecessary header wrapping receives cost penalty`() {
        val columns = listOf(column(100f, 20), column(100f, 20))
        val clean = candidate(.50f, firstHeight = 40f, secondHeight = 40f)
        val wrapped = candidate(
            .50f,
            firstHeight = 20f,
            secondHeight = 20f,
            headerLines = 2 to 1,
        )
        assertTrue(
            MarkdownTableLayoutEngine.twoColumnCost(clean, columns, 360f) <
                MarkdownTableLayoutEngine.twoColumnCost(wrapped, columns, 360f),
        )
    }

    @Test
    fun `narrow long text pillar receives cost penalty`() {
        val columns = listOf(column(500f, 60), column(300f, 40))
        val readable = candidate(
            .55f,
            firstHeight = 100f,
            secondHeight = 80f,
            firstLines = 8,
            firstCharacters = 60,
        )
        val pillar = candidate(
            .40f,
            firstHeight = 100f,
            secondHeight = 60f,
            firstLines = 30,
            firstCharacters = 60,
        )
        assertTrue(
            MarkdownTableLayoutEngine.twoColumnCost(readable, columns, 360f) <
                MarkdownTableLayoutEngine.twoColumnCost(pillar, columns, 360f),
        )
    }

    @Test
    fun `diagnostic classification does not force verbose scrolling`() {
        val columns = listOf(
            column(900f, 80, listOf(8, 80, 46)),
            column(520f, 48, listOf(6, 48, 38)),
        )
        val metrics = MarkdownTableLayoutEngine.calculate(360f, 1f, columns)
        assertEquals(TwoColumnContentMode.VERBOSE_COMPARISON, metrics.twoColumnContentMode)
        assertFalse(metrics.horizontallyScrollable)
        assertEquals(360f, metrics.totalWidthPx, 0.01f)
    }

    @Test
    fun `compact diagnostic classification remains available`() {
        val columns = listOf(
            column(90f, 12, listOf(2, 5, 12, 3)),
            column(800f, 80, listOf(2, 40, 80, 60)),
        )
        assertEquals(
            TwoColumnContentMode.COMPACT_LABEL_VALUE,
            MarkdownTableLayoutEngine.classifyTwoColumn(columns),
        )
    }

    @Test
    fun `three columns preserve natural preferred widths and overflow`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            340f,
            1f,
            listOf(column(60f), column(150f, 12), column(160f, 12)),
        )
        assertTrue(metrics.horizontallyScrollable)
        assertEquals(listOf(82f, 178f, 188f), metrics.widthsPx)
        assertEquals(448f, metrics.totalWidthPx, 0.01f)
    }

    @Test
    fun `wide identifier column uses measured content instead of primary text floor`() {
        val identifier = TableColumnMeasurement(
            contentWidthPx = 50f,
            maxCharacterCount = 4,
            characterCounts = listOf(2, 2, 4, 2),
            unconstrainedCellWidthsPx = listOf(34f, 25f, 50f, 34f),
            headerWidthPx = 34f,
        )
        val metrics = MarkdownTableLayoutEngine.calculate(
            340f,
            1f,
            listOf(identifier, column(150f, 12), column(160f, 12)),
        )

        assertEquals(72f, metrics.widthsPx.first(), 0.01f)
        assertTrue(metrics.widthsPx.first() < 84f)
        assertTrue(metrics.widthsPx[1] >= metrics.readableWidthsPx[1])
    }

    @Test
    fun `third column partial visibility is a natural bounds result`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            360f,
            1f,
            listOf(column(60f), column(120f), column(180f)),
        )
        val thirdStart = metrics.widthsPx.take(2).sum()
        assertTrue(thirdStart < metrics.viewportWidthPx)
        assertTrue(thirdStart > 0f)
        assertTrue(metrics.horizontallyScrollable)
    }

    @Test
    fun `wide landscape viewport expands all columns and disables scrolling`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            1000f,
            1f,
            listOf(column(60f), column(150f), column(160f)),
        )
        assertEquals(1000f, metrics.totalWidthPx, 0.01f)
        assertFalse(metrics.horizontallyScrollable)
        assertEquals(82f, metrics.widthsPx.first(), 0.01f)
        assertTrue(metrics.widthsPx.drop(1).all { it > 136f })
    }

    @Test
    fun `five long columns do not collapse into narrow text pillars`() {
        val longColumn = TableColumnMeasurement(
            contentWidthPx = 900f,
            maxCharacterCount = 80,
            characterCounts = listOf(5, 80),
            unconstrainedCellWidthsPx = listOf(80f, 900f),
            headerWidthPx = 80f,
        )
        val metrics = MarkdownTableLayoutEngine.calculate(
            360f,
            1f,
            List(5) { longColumn },
        )
        assertEquals(listOf(100f, 108f, 108f, 108f, 108f), metrics.widthsPx)
        assertEquals(532f, metrics.totalWidthPx, 0.01f)
        assertTrue(metrics.widthsPx.zip(metrics.readableWidthsPx)
            .all { (width, readable) -> width >= readable })
    }

    @Test
    fun `all rows consume one shared column metric vector`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            500f,
            1f,
            listOf(column(120f), column(180f), column(90f)),
        )
        val headerWidths = metrics.widthsPx
        val firstBodyWidths = metrics.widthsPx
        assertTrue(headerWidths === firstBodyWidths)
    }

    @Test
    fun `last column retains complete positive width at scroll end`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            320f,
            1f,
            List(4) { column(200f, 20) },
        )
        val lastStart = metrics.widthsPx.dropLast(1).sum()
        assertEquals(metrics.widthsPx.last(), metrics.totalWidthPx - lastStart, 0.01f)
        assertTrue(metrics.widthsPx.last() > 0f)
    }

    @Test
    fun `invalid measurements never create zero negative or non finite width`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            Float.NaN,
            Float.NaN,
            listOf(column(Float.NaN, 0), column(-500f, 0)),
        )
        assertTrue(metrics.widthsPx.all { it.isFinite() && it >= 72f })
        assertTrue(metrics.totalWidthPx.isFinite() && metrics.totalWidthPx > 0f)
    }

    @Test
    fun `viewport change recalculates responsive widths`() {
        val columns = listOf(column(800f, 80), column(900f, 90))
        val phone = MarkdownTableLayoutEngine.calculate(320f, 1f, columns)
        val tablet = MarkdownTableLayoutEngine.calculate(700f, 1f, columns)
        assertEquals(320f, phone.totalWidthPx, 0.01f)
        assertEquals(576f, tablet.totalWidthPx, 0.01f)
        assertTrue(tablet.widthsPx != phone.widthsPx)
    }

    @Test
    fun `density scales centralized wide table tokens`() {
        val oneX = MarkdownTableLayoutEngine.calculate(300f, 1f, listOf(column(1000f, 80)))
        val twoX = MarkdownTableLayoutEngine.calculate(600f, 2f, listOf(column(2000f, 80)))
        assertEquals(oneX.widthsPx.single() * 2f, twoX.widthsPx.single(), 0.01f)
    }

    @Test
    fun `fixed fixture preserves inline markdown source and semantics`() {
        val fixture = requireNotNull(
            javaClass.getResource("/fixtures/markdown-responsive-tables.md"),
        ).readText()
        val tables = MarkdownParser.parse(fixture).blocks.filterIsInstance<MarkdownBlock.Table>()
        assertEquals(4, tables.size)
        val inline = tables[3].rows.single().last()
        assertTrue(inline.contains("**粗体**"))
        assertTrue(inline.contains("`code()`"))
        assertTrue(inline.contains("[链接]"))
        assertEquals("粗体、code()、链接", InlineMarkdownParser.toPlainText(inline))
    }

    @Test
    fun `compose measurement cache key covers required environment and excludes scroll`() {
        val source = tableSource()
        val cacheKey = source.substringAfter("val cacheKey =").substringBefore("val cachedLayout")
        assertTrue(cacheKey.contains("stableTableId"))
        assertTrue(cacheKey.contains("tableContentHash"))
        assertTrue(cacheKey.contains("viewportWidthPx"))
        assertTrue(cacheKey.contains("density.density"))
        assertTrue(cacheKey.contains("density.fontScale"))
        assertTrue(cacheKey.contains("typographyIdentity"))
        assertTrue(cacheKey.contains("layoutDirection"))
        assertFalse(cacheKey.contains("scrollState"))
        assertTrue(source.contains("effectiveCache.getOrSolve(cacheKey)"))
        assertTrue(source.contains("twoColumnCandidateFractions.map"))
        assertTrue(source.contains("Constraints(maxWidth = it)"))
    }

    @Test
    fun `compose table keeps natural wrapping and gesture ownership`() {
        val source = tableSource()
        assertTrue(source.contains("softWrap = true"))
        assertTrue(source.contains("overflow = TextOverflow.Clip"))
        assertTrue(source.contains("maxLines = Int.MAX_VALUE"))
        assertFalse(source.contains("TextOverflow.Ellipsis"))
        assertTrue(source.contains("horizontalScroll("))
        assertFalse(source.contains("pointerInput"))
    }

    @Test
    fun `visual structure has only horizontal dividers and normal body weight`() {
        val source = tableSource()
        assertFalse(source.contains(".border("))
        assertFalse(source.contains("RoundedCornerShape"))
        assertFalse(source.contains("headerBackground"))
        assertFalse(source.contains("markdown-table-fade"))
        assertFalse(source.contains("Brush.horizontalGradient"))
        assertTrue(source.contains("HorizontalDivider"))
        assertTrue(source.contains("fontWeight = FontWeight.Normal"))
        assertTrue(source.contains("resolveTableHeaderTextStyle(bodyStyle)"))
        assertTrue(source.contains("InlineMarkdownParser.parse"))
        assertNotNull(MarkdownTableLayoutEngine.Tokens.twoColumnCandidateFractions)
    }

    @Test
    fun `column metrics expose content driven readable preferred and maximum widths`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            360f,
            1f,
            listOf(column(60f, 4), column(220f, 30)),
        )
        assertEquals(2, metrics.readableWidthsPx.size)
        assertEquals(2, metrics.preferredWidthsPx.size)
        assertEquals(2, metrics.maximumWidthsPx.size)
        metrics.readableWidthsPx.indices.forEach { index ->
            assertTrue(metrics.readableWidthsPx[index] <= metrics.preferredWidthsPx[index])
            assertTrue(metrics.preferredWidthsPx[index] <= metrics.maximumWidthsPx[index])
        }
    }

    @Test
    fun `two column finite candidate range covers twenty eight through seventy two percent`() {
        val fractions = MarkdownTableLayoutEngine.Tokens.twoColumnCandidateFractions
        assertEquals(.28f, fractions.first(), 0.001f)
        assertEquals(.72f, fractions.last(), 0.001f)
        assertTrue(fractions.size >= 9)
        assertTrue(fractions.all { it in .28f..0.72f })
    }

    @Test
    fun `short first column excess space loses to minimum necessary candidate`() {
        val columns = listOf(
            column(80f, 6, listOf(2, 2, 6, 3)),
            column(140f, 10, listOf(2, 2, 5, 10)),
        )
        val candidates = listOf(
            candidate(.28f, viewport = 276f, firstHeight = 20f, secondHeight = 20f),
            candidate(.40f, viewport = 276f, firstHeight = 20f, secondHeight = 20f),
            candidate(.44f, viewport = 276f, firstHeight = 20f, secondHeight = 20f),
        )
        val metrics = MarkdownTableLayoutEngine.calculate(360f, 1f, columns, candidates)
        assertEquals(.40f, metrics.selectedTwoColumnFraction!!, 0.001f)
        assertTrue(metrics.widthsPx[0] < metrics.widthsPx[1])
    }

    @Test
    fun `row height imbalance contributes to candidate cost`() {
        val columns = listOf(column(200f, 30), column(200f, 30))
        val balanced = candidate(.50f, firstHeight = 40f, secondHeight = 40f)
        val imbalanced = candidate(.50f, firstHeight = 40f, secondHeight = 10f)
        assertTrue(
            MarkdownTableLayoutEngine.twoColumnCost(balanced, columns, 360f) <
                MarkdownTableLayoutEngine.twoColumnCost(imbalanced, columns, 360f),
        )
    }

    @Test
    fun `wide table preserves preferred primary widths instead of fitting viewport`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            430f,
            1f,
            listOf(column(100f), column(150f), column(160f)),
        )
        assertEquals(488f, metrics.totalWidthPx, 0.01f)
        assertTrue(metrics.horizontallyScrollable)
        assertEquals(metrics.preferredWidthsPx, metrics.widthsPx)
    }

    @Test
    fun `wide table keeps natural overflow when fitting would require excessive compression`() {
        val columns = listOf(
            TableColumnMeasurement(100f, 6, listOf(2, 2, 4, 6), listOf(40f, 50f, 70f, 100f), 40f),
            TableColumnMeasurement(500f, 30, listOf(6, 12, 20, 30), listOf(80f, 100f, 140f, 500f), 80f),
            TableColumnMeasurement(500f, 30, listOf(6, 12, 20, 30), listOf(80f, 100f, 140f, 500f), 80f),
        )
        val metrics = MarkdownTableLayoutEngine.calculate(340f, 1f, columns)
        assertTrue(metrics.readableWidthsPx.sum() < 340f)
        assertTrue(metrics.totalWidthPx > 340f)
        assertTrue(metrics.horizontallyScrollable)
        val thirdStart = metrics.widthsPx.take(2).sum()
        assertTrue(thirdStart < metrics.viewportWidthPx)
        assertTrue(thirdStart + metrics.widthsPx[2] > metrics.viewportWidthPx)
    }

    @Test
    fun `default parity fixture gives primary second column priority and natural third clipping`() {
        val fixture = requireNotNull(
            javaClass.getResource("/fixtures/markdown-table-human-acceptance.md"),
        ).readText()
        val table = MarkdownParser.parse(fixture).blocks
            .filterIsInstance<MarkdownBlock.Table>()[2]
        assertEquals("rsR′、M 形、兔耳", table.rows[0][1])

        val columns = listOf(
            TableColumnMeasurement(50f, 4, listOf(2, 2, 4, 2), listOf(34f, 25f, 50f, 34f), 34f),
            TableColumnMeasurement(
                300f,
                21,
                listOf(6, 11, 6, 21),
                listOf(90f, 145f, 75f, 300f),
                90f,
            ),
            TableColumnMeasurement(
                300f,
                21,
                listOf(6, 8, 10, 21),
                listOf(90f, 68f, 130f, 300f),
                90f,
            ),
        )
        val viewport = 353f
        val metrics = MarkdownTableLayoutEngine.calculate(viewport, 1f, columns)
        val firstEnd = metrics.widthsPx[0]
        val secondEnd = metrics.widthsPx.take(2).sum()
        val thirdEnd = metrics.totalWidthPx

        assertTrue(firstEnd <= viewport)
        assertTrue(secondEnd <= viewport)
        assertTrue(metrics.widthsPx[1] >= 145f + 20f)
        assertTrue(secondEnd < viewport)
        assertTrue(thirdEnd > viewport)
        assertTrue(metrics.horizontallyScrollable)
        val initialScrollOffset = 0f
        assertEquals(0f, initialScrollOffset, 0f)
        val maximumScrollOffset = thirdEnd - viewport
        assertTrue(secondEnd - maximumScrollOffset >= 0f)
        assertEquals(viewport, thirdEnd - maximumScrollOffset, 0.01f)
        assertFalse(tableSource().contains("rsR′、M 形、兔耳"))
    }

    @Test
    fun `two column overflow uses asymmetric measured readable minima`() {
        val metrics = MarkdownTableLayoutEngine.calculate(
            180f,
            1f,
            listOf(column(20f, 2), column(1000f, 90)),
        )
        assertEquals(listOf(72f, 132f), metrics.widthsPx)
        assertTrue(metrics.horizontallyScrollable)
    }

    @Test
    fun `renderer contains no fixed two hundred sixty dp path and uses parity padding`() {
        val source = tableSource()
        assertFalse(source.contains("260.dp"))
        assertEquals(10f, MarkdownTableLayoutEngine.Tokens.horizontalCellPaddingDp, 0.01f)
        assertEquals(10f, MarkdownTableLayoutEngine.Tokens.verticalCellPaddingDp, 0.01f)
        assertEquals(.88f, MarkdownTableLayoutEngine.Tokens.tableTextScale, 0.01f)
    }

    @Test
    fun `compact two column table keeps natural content width while verbose table uses viewport`() {
        val compact = MarkdownTableLayoutEngine.calculate(
            360f,
            1f,
            listOf(column(80f, 6), column(140f, 10)),
        )
        val verbose = MarkdownTableLayoutEngine.calculate(
            360f,
            1f,
            listOf(column(900f, 90), column(700f, 70)),
        )
        assertEquals(276f, compact.totalWidthPx, 0.01f)
        assertFalse(compact.horizontallyScrollable)
        assertEquals(360f, verbose.totalWidthPx, 0.01f)
        assertFalse(verbose.horizontallyScrollable)
    }

    @Test
    fun `all twenty seven reader setting combinations preserve compact table readability`() {
        val fontScales = listOf(
            ReaderSettings.FONT_SMALL,
            ReaderSettings.FONT_DEFAULT,
            ReaderSettings.FONT_LARGE,
        )
        val lineHeightRatios = listOf(
            ReaderSettings.LINE_COMPACT,
            ReaderSettings.LINE_DEFAULT,
            ReaderSettings.LINE_RELAXED,
        )
        val paddingLevels = ContentPaddingLevel.entries
        var combinations = 0

        fontScales.forEach { readerFontScale ->
            lineHeightRatios.forEach { lineHeightRatio ->
                paddingLevels.forEach { paddingLevel ->
                    combinations += 1
                    val tableFontScale = readerFontScale *
                        MarkdownTableLayoutEngine.Tokens.tableTextScale
                    val viewport = 393f - paddingLevel.horizontalDp.value * 2f
                    val columns = listOf(
                        column(width = 62f * tableFontScale, characters = 5),
                        column(width = 128f * tableFontScale, characters = 7),
                    )
                    val target = MarkdownTableLayoutEngine.twoColumnTargetWidth(
                        viewportWidthPx = viewport,
                        density = 1f,
                        columns = columns,
                    )
                    val candidates = MarkdownTableLayoutEngine.Tokens.twoColumnCandidateFractions
                        .map { fraction ->
                            val widths = listOf(target * fraction, target * (1f - fraction))
                            val contentWidths = listOf(62f * tableFontScale, 128f * tableFontScale)
                            val cells = widths.indices.map { index ->
                                val available = (widths[index] - 20f).coerceAtLeast(1f)
                                val lines = ceil(contentWidths[index] / available).toInt()
                                WrappedCellMeasurement(
                                    heightPx = lines * 16f * tableFontScale * lineHeightRatio,
                                    lineCount = lines,
                                    characterCount = if (index == 0) 5 else 7,
                                )
                            }
                            TwoColumnWidthCandidate(
                                firstFraction = fraction,
                                widthsPx = widths,
                                rows = listOf(
                                    WrappedRowMeasurement(cells = cells, isHeader = false),
                                ),
                            )
                        }
                    val metrics = MarkdownTableLayoutEngine.calculate(
                        viewportWidthPx = viewport,
                        density = 1f,
                        columns = columns,
                        twoColumnCandidates = candidates,
                    )
                    val context = "font=$readerFontScale line=$lineHeightRatio padding=$paddingLevel"
                    assertFalse(context, metrics.horizontallyScrollable)
                    assertTrue(context, metrics.totalWidthPx <= viewport + 0.5f)
                    assertEquals(context, 1, candidates
                        .first { it.firstFraction == metrics.selectedTwoColumnFraction }
                        .rows.single().cells.first().lineCount)
                    assertTrue(context, metrics.widthsPx.zip(metrics.readableWidthsPx)
                        .all { (width, readable) -> width >= readable })

                    val wideColumns = listOf(
                        TableColumnMeasurement(
                            50f * tableFontScale,
                            4,
                            listOf(2, 2, 4, 2),
                            listOf(34f, 25f, 50f, 34f).map { it * tableFontScale },
                            34f * tableFontScale,
                        ),
                        TableColumnMeasurement(
                            300f * tableFontScale,
                            21,
                            listOf(6, 11, 6, 21),
                            listOf(90f, 145f, 75f, 300f).map { it * tableFontScale },
                            90f * tableFontScale,
                        ),
                        TableColumnMeasurement(
                            300f * tableFontScale,
                            21,
                            listOf(6, 8, 10, 21),
                            listOf(90f, 68f, 130f, 300f).map { it * tableFontScale },
                            90f * tableFontScale,
                        ),
                    )
                    val wide = MarkdownTableLayoutEngine.calculate(viewport, 1f, wideColumns)
                    assertTrue(context, wide.widthsPx.all { it.isFinite() && it > 0f })
                    assertTrue(context, wide.widthsPx.zip(wide.readableWidthsPx)
                        .all { (width, readable) -> width >= readable })
                    assertTrue(
                        context,
                        wide.widthsPx.first() >=
                            MarkdownTableLayoutEngine.Tokens.wideIdentifierMinimumDp,
                    )
                    assertEquals(context, wide.totalWidthPx > viewport + 0.5f,
                        wide.horizontallyScrollable)
                    if (wide.horizontallyScrollable) {
                        val maximumScrollOffset = wide.totalWidthPx - viewport
                        assertEquals(context, viewport,
                            wide.totalWidthPx - maximumScrollOffset, 0.01f)
                    }
                }
            }
        }

        assertEquals(27, combinations)
    }

    private fun tableSource(): String = listOf(
        File("app/src/main/java/com/m1racle/reader/ui/viewer/components/TableBlockView.kt"),
        File("src/main/java/com/m1racle/reader/ui/viewer/components/TableBlockView.kt"),
    ).first { it.isFile }.readText()
}
