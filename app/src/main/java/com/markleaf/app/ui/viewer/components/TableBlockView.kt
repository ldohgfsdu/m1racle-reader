package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.InlineMarkdownStyles
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.markdown.TableAlignment
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
import kotlin.math.roundToInt

@Composable
fun TableBlockView(
    block: MarkdownBlock.Table,
    settings: ReaderSettings,
    isCurrentSearchMatch: Boolean = false,
    modifier: Modifier = Modifier,
    /** Test/diagnostic hook; invoked only when the remembered whole-table measurement is rebuilt. */
    onColumnMetricsCalculated: (() -> Unit)? = null,
    /** Test/diagnostic hook exposing resolved header metrics without OCR. */
    onHeaderCellLayout: ((Int, TableHeaderCellLayoutMetadata) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val tokens = MarkdownTableLayoutEngine.Tokens
    val tableFontScale = settings.fontScale * tokens.tableTextScale
    val tableBodySize = ReaderTypography.bodyFontSize.scaled(tableFontScale)

    val bodyStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = tableBodySize,
        lineHeight = (
            tableBodySize.value * ReaderSettings.clampLineHeightScale(settings.lineHeightScale)
            ).sp,
        color = scheme.onSurface,
    )
    val resolvedHeaderTextStyle = resolveTableHeaderTextStyle(bodyStyle)
    val inlineStyles = remember(
        scheme.primary,
        scheme.surfaceContainerHighest,
        settings.fontScale,
    ) {
        InlineMarkdownStyles.themed(
            primary = scheme.primary,
            codeBackground = scheme.surfaceContainerHighest,
            codeFontSize = ReaderTypography.inlineCodeFontSize.scaled(tableFontScale),
        )
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    val currentMetricsObserver by rememberUpdatedState(onColumnMetricsCalculated)
    val scrollState = rememberScrollState()
    val tableContentHash = remember(block) { block.hashCode() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .searchBlockHighlight(isCurrentSearchMatch)
            .testTag("markdown-table"),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        // The cache key contains content, viewport, density/font scale, typography, and direction.
        // Scroll position is deliberately absent, so width optimization cannot run while scrolling.
        val metrics = remember(
            block,
            tableContentHash,
            viewportWidthPx,
            density.density,
            density.fontScale,
            bodyStyle,
            resolvedHeaderTextStyle,
            inlineStyles,
            layoutDirection,
        ) {
            val columnCount = block.headers.size.coerceAtLeast(1)
            val measurements = List(columnCount) { column ->
                val cells = buildList {
                    add(true to block.headers.getOrElse(column) { "" })
                    block.rows.forEach { row -> add(false to row.getOrElse(column) { "" }) }
                }
                var widest = 0f
                var maxCharacters = 0
                val characterCounts = mutableListOf<Int>()
                val unconstrainedWidths = mutableListOf<Float>()
                var headerWidth = 0f
                cells.forEach { (isHeader, source) ->
                    val annotated = InlineMarkdownParser.parse(source.ifEmpty { " " }, inlineStyles)
                    val measured = textMeasurer.measure(
                        text = annotated,
                        style = if (isHeader) resolvedHeaderTextStyle else bodyStyle,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    widest = maxOf(widest, measured.size.width.toFloat())
                    unconstrainedWidths += measured.size.width.toFloat()
                    if (isHeader) headerWidth = measured.size.width.toFloat()
                    val plainText = InlineMarkdownParser.toPlainText(source)
                    val characterCount = plainText.codePointCount(0, plainText.length)
                    characterCounts += characterCount
                    maxCharacters = maxOf(maxCharacters, characterCount)
                }
                TableColumnMeasurement(
                    contentWidthPx = widest,
                    maxCharacterCount = maxCharacters,
                    characterCounts = characterCounts,
                    unconstrainedCellWidthsPx = unconstrainedWidths,
                    headerWidthPx = headerWidth,
                )
            }
            val candidates = if (columnCount == 2) {
                val targetWidthPx = MarkdownTableLayoutEngine.twoColumnTargetWidth(
                    viewportWidthPx = viewportWidthPx,
                    density = density.density,
                    columns = measurements,
                )
                tokens.twoColumnCandidateFractions.map { firstFraction ->
                    val widths = listOf(
                        targetWidthPx * firstFraction,
                        targetWidthPx * (1f - firstFraction),
                    )
                    val rows = buildList {
                        add(block.headers to true)
                        block.rows.forEach { add(it to false) }
                    }.map { (sources, isHeader) ->
                        WrappedRowMeasurement(
                            cells = List(2) { column ->
                                val source = sources.getOrElse(column) { "" }
                                val plainText = InlineMarkdownParser.toPlainText(source)
                                val availableWidth = (
                                    widths[column] -
                                        tokens.horizontalCellPaddingDp * 2f * density.density
                                    ).roundToInt().coerceAtLeast(1)
                                val measured = textMeasurer.measure(
                                    text = InlineMarkdownParser.parse(
                                        source.ifEmpty { " " },
                                        inlineStyles,
                                    ),
                                    style = if (isHeader) resolvedHeaderTextStyle else bodyStyle,
                                    softWrap = true,
                                    overflow = TextOverflow.Clip,
                                    maxLines = Int.MAX_VALUE,
                                    constraints = Constraints(maxWidth = availableWidth),
                                )
                                WrappedCellMeasurement(
                                    heightPx = measured.size.height.toFloat(),
                                    lineCount = measured.lineCount,
                                    characterCount = plainText.codePointCount(0, plainText.length),
                                )
                            },
                            isHeader = isHeader,
                        )
                    }
                    TwoColumnWidthCandidate(firstFraction, widths, rows)
                }
            } else {
                emptyList()
            }
            MarkdownTableLayoutEngine.calculate(
                viewportWidthPx = viewportWidthPx,
                density = density.density,
                columns = measurements,
                twoColumnCandidates = candidates,
            ).also { currentMetricsObserver?.invoke() }
        }
        val columnWidths = remember(metrics, density.density) {
            metrics.widthsPx.map { with(density) { it.toDp() } }
        }
        val tableWidth = with(density) { metrics.totalWidthPx.toDp() }
        val dividerColor = scheme.outlineVariant.copy(alpha = 0.55f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    state = scrollState,
                    enabled = metrics.horizontallyScrollable,
                )
                .testTag("markdown-table-scroll"),
        ) {
            Column(modifier = Modifier.width(tableWidth)) {
                TableHeaderRow(
                    cells = block.headers,
                    columnWidths = columnWidths,
                    resolvedHeaderTextStyle = resolvedHeaderTextStyle,
                    inlineStyles = inlineStyles,
                    horizontalPadding = tokens.horizontalCellPaddingDp.dp,
                    verticalPadding = tokens.verticalCellPaddingDp.dp,
                    onHeaderCellLayout = onHeaderCellLayout,
                )
                HorizontalDivider(
                    modifier = Modifier.testTag("markdown-table-header-divider"),
                    color = dividerColor,
                    thickness = 0.75.dp,
                )
                block.rows.forEachIndexed { rowIndex, row ->
                    TableRow(
                        cells = row,
                        alignments = block.alignments,
                        columnWidths = columnWidths,
                        style = bodyStyle,
                        inlineStyles = inlineStyles,
                        horizontalPadding = tokens.horizontalCellPaddingDp.dp,
                        verticalPadding = tokens.verticalCellPaddingDp.dp,
                        rowTag = "body-$rowIndex",
                    )
                    if (rowIndex < block.rows.lastIndex) {
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

data class TableHeaderCellLayoutMetadata(
    val fontSizeSp: Float,
    val fontWeight: FontWeight?,
    val lineHeightSp: Float,
    val letterSpacingSp: Float,
    val fontFamily: FontFamily?,
    val platformStyle: PlatformTextStyle?,
    val textDirection: TextDirection?,
    val firstBaselinePx: Float,
    val textHeightPx: Int,
    val lineCount: Int,
)

internal fun resolveTableHeaderTextStyle(bodyStyle: TextStyle): TextStyle = bodyStyle.copy(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Start,
    textDirection = TextDirection.ContentOrLtr,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun TableHeaderRow(
    cells: List<String>,
    columnWidths: List<Dp>,
    resolvedHeaderTextStyle: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onHeaderCellLayout: ((Int, TableHeaderCellLayoutMetadata) -> Unit)?,
) {
    Row(modifier = Modifier.testTag("markdown-table-row-header")) {
        columnWidths.forEachIndexed { column, width ->
            val source = cells.getOrElse(column) { "" }
            val annotated = remember(source, inlineStyles) {
                InlineMarkdownParser.parse(source.ifEmpty { " " }, inlineStyles)
            }
            Text(
                text = annotated,
                modifier = Modifier
                    .alignByBaseline()
                    .width(width)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .fillMaxWidth()
                    .testTag("markdown-table-cell-header-$column"),
                style = resolvedHeaderTextStyle,
                textAlign = TextAlign.Start,
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = Int.MAX_VALUE,
                onTextLayout = { result ->
                    onHeaderCellLayout?.invoke(
                        column,
                        TableHeaderCellLayoutMetadata(
                            fontSizeSp = resolvedHeaderTextStyle.fontSize.value,
                            fontWeight = resolvedHeaderTextStyle.fontWeight,
                            lineHeightSp = resolvedHeaderTextStyle.lineHeight.value,
                            letterSpacingSp = resolvedHeaderTextStyle.letterSpacing.value,
                            fontFamily = resolvedHeaderTextStyle.fontFamily,
                            platformStyle = resolvedHeaderTextStyle.platformStyle,
                            textDirection = resolvedHeaderTextStyle.textDirection,
                            firstBaselinePx = result.firstBaseline,
                            textHeightPx = result.size.height,
                            lineCount = result.lineCount,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    alignments: List<TableAlignment>,
    columnWidths: List<Dp>,
    style: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    rowTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("markdown-table-row-$rowTag"),
        verticalAlignment = Alignment.Top,
    ) {
        columnWidths.forEachIndexed { column, width ->
            TableCell(
                text = cells.getOrElse(column) { "" },
                style = style,
                inlineStyles = inlineStyles,
                alignment = alignments.getOrElse(column) { TableAlignment.Start },
                modifier = Modifier
                    .width(width)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .testTag("markdown-table-cell-$rowTag-$column"),
            )
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    alignment: TableAlignment,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, inlineStyles) {
        InlineMarkdownParser.parse(text.ifEmpty { " " }, inlineStyles)
    }
    val resolvedStyle = style.copy(
        fontFamily = style.fontFamily ?: FontFamily.Default,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
    )
    Text(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = resolvedStyle,
        textAlign = when (alignment) {
            TableAlignment.Start -> TextAlign.Start
            TableAlignment.Center -> TextAlign.Center
            TableAlignment.End -> TextAlign.End
        },
        softWrap = true,
        overflow = TextOverflow.Clip,
        maxLines = Int.MAX_VALUE,
    )
}
