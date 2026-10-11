package com.m1racle.reader.ui.viewer.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
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
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.markdown.InlineMarkdownParser
import com.m1racle.reader.markdown.InlineMarkdownStyles
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.TableAlignment
import com.m1racle.reader.search.BlockSearchHighlights
import com.m1racle.reader.search.SearchHighlightRange
import com.m1racle.reader.search.withSearchHighlights
// EmptyRanges / short-circuit when searchHighlights is null
import com.m1racle.reader.ui.viewer.components.ReaderTypography.scaled
import kotlin.math.roundToInt

@Composable
fun TableBlockView(
    block: MarkdownBlock.Table,
    settings: ReaderSettings,
    searchHighlights: BlockSearchHighlights? = null,
    modifier: Modifier = Modifier,
    /** Test/diagnostic hook; invoked only when the remembered whole-table measurement is rebuilt. */
    onColumnMetricsCalculated: (() -> Unit)? = null,
    /** Test/diagnostic hook exposing resolved header metrics without OCR. */
    onHeaderCellLayout: ((Int, TableHeaderCellLayoutMetadata) -> Unit)? = null,
    stableTableId: String? = null,
    layoutCache: TableLayoutCache? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val tokens = MarkdownTableLayoutEngine.Tokens
    val tableFontScale = settings.fontScale * tokens.tableTextScale
    val tableBodySize = ReaderTypography.bodyFontSize.scaled(tableFontScale)

    val bodyStyle = TextStyle(
        fontFamily = com.m1racle.reader.ui.components.LocalReaderImportedFont.current ?: FontFamily.Default,
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
    val tableContentHash = remember(block) { block.hashCode() }
    val resolvedTableId = stableTableId ?: "table-${tableContentHash.toUInt().toString(16)}"
    val effectiveCache = layoutCache ?: remember { TableLayoutCache("standalone") }
    val savedHorizontalOffset = remember(effectiveCache, resolvedTableId) {
        effectiveCache.horizontalOffset(resolvedTableId)
    }
    val scrollState = rememberScrollState(savedHorizontalOffset)
    DisposableEffect(effectiveCache, resolvedTableId, scrollState) {
        onDispose {
            effectiveCache.saveHorizontalOffset(resolvedTableId, scrollState.value)
        }
    }

    val hasSearch = searchHighlights != null && !searchHighlights.isEmpty
    val normalHl = if (hasSearch) searchMatchNormalBackground() else Color.Unspecified
    val activeHl = if (hasSearch) searchMatchActiveBackground() else Color.Unspecified
    // Cell ordinal matches MarkdownSearchTextExtractor: headers then row-major body cells.
    val annotationOffsets = remember(block) {
        var offset = 0
        (block.headers + block.rows.flatten()).map { cell -> offset.also { offset += InlineMarkdownParser.toPlainText(cell).length + 1 } }
    }
    val columnCount = block.headers.size.coerceAtLeast(1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("markdown-table"),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        // Raw vertical/horizontal offsets are deliberately absent from this document-scoped key.
        val cacheKey = effectiveCache.key(
            stableTableId = resolvedTableId,
            tableContentHash = tableContentHash,
            viewportWidthPx = viewportWidthPx,
            density = density.density,
            systemFontScale = density.fontScale,
            textSizeSetting = settings.fontScale,
            lineHeightSetting = settings.lineHeightScale,
            pageMarginSetting = settings.contentPaddingLevel.name,
            typographyIdentity = 31 * bodyStyle.hashCode() + resolvedHeaderTextStyle.hashCode(),
            layoutDirection = layoutDirection.name,
        )
        val cachedLayout = remember(effectiveCache, cacheKey) {
            effectiveCache.getOrSolve(cacheKey) {
            var measuredCellCount = 0L
            val cellMeasureCache = mutableMapOf<CellMeasureKey, TextLayoutResult>()
            fun measureCell(
                source: String,
                isHeader: Boolean,
                availableWidth: Int?,
                softWrap: Boolean,
            ): TextLayoutResult {
                val key = CellMeasureKey(
                    text = source,
                    styleIdentity = if (isHeader) {
                        resolvedHeaderTextStyle.hashCode()
                    } else {
                        bodyStyle.hashCode()
                    },
                    availableWidthPx = availableWidth,
                    softWrap = softWrap,
                )
                return cellMeasureCache.getOrPut(key) {
                    measuredCellCount++
                    com.m1racle.reader.markdown.MarkdownPerformance.trace("MarkdownTableMeasure") {
                        textMeasurer.measure(
                            text = InlineMarkdownParser.parse(
                                source.ifEmpty { " " },
                                inlineStyles,
                            ),
                            style = if (isHeader) resolvedHeaderTextStyle else bodyStyle,
                            softWrap = softWrap,
                            maxLines = if (softWrap) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Clip,
                            constraints = availableWidth?.let { Constraints(maxWidth = it) }
                                ?: Constraints(),
                        )
                    }
                }
            }
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
                    val measured = measureCell(
                        source = source,
                        isHeader = isHeader,
                        availableWidth = null,
                        softWrap = false,
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
                                val measured = measureCell(
                                    source = source,
                                    isHeader = isHeader,
                                    availableWidth = availableWidth,
                                    softWrap = true,
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
                CachedTableLayout(
                    metrics = MarkdownTableLayoutEngine.calculate(
                        viewportWidthPx = viewportWidthPx,
                        density = density.density,
                        columns = measurements,
                        twoColumnCandidates = candidates,
                    ),
                    measuredCellCount = measuredCellCount,
                ).also { currentMetricsObserver?.invoke() }
            }
        }
        val metrics = cachedLayout.metrics
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
            // Single shared width list for every row (header + body). Content never
            // re-solves widths per cell at draw time; horizontal scroll when total > viewport.
            Column(modifier = Modifier.width(tableWidth)) {
                TableHeaderRow(
                    cells = block.headers,
                    annotationOffsets = annotationOffsets.take(block.headers.size),
                    alignments = block.alignments,
                    columnWidths = columnWidths,
                    resolvedHeaderTextStyle = resolvedHeaderTextStyle,
                    inlineStyles = inlineStyles,
                    horizontalPadding = tokens.horizontalCellPaddingDp.dp,
                    verticalPadding = tokens.verticalCellPaddingDp.dp,
                    onHeaderCellLayout = onHeaderCellLayout,
                    onLinkClick = onLinkClick,
                    cellHighlight = { col ->
                        if (!hasSearch) {
                            BlockSearchHighlights.EmptyRanges
                        } else {
                            searchHighlights!!.forCell(col)
                        }
                    },
                    normalHl = normalHl,
                    activeHl = activeHl,
                )
                HorizontalDivider(
                    modifier = Modifier.testTag("markdown-table-header-divider"),
                    color = dividerColor,
                    thickness = 0.75.dp,
                )
                block.rows.forEachIndexed { rowIndex, row ->
                    val rowBaseOrdinal = columnCount + rowIndex * columnCount
                    TableRow(
                        cells = row,
                        annotationOffsets = annotationOffsets.drop(rowBaseOrdinal).take(row.size),
                        alignments = block.alignments,
                        columnWidths = columnWidths,
                        style = bodyStyle,
                        inlineStyles = inlineStyles,
                        horizontalPadding = tokens.horizontalCellPaddingDp.dp,
                        verticalPadding = tokens.verticalCellPaddingDp.dp,
                        rowTag = "body-$rowIndex",
                        onLinkClick = onLinkClick,
                        cellHighlight = { col ->
                            if (!hasSearch) {
                                BlockSearchHighlights.EmptyRanges
                            } else {
                                searchHighlights!!.forCell(rowBaseOrdinal + col)
                            }
                        },
                        normalHl = normalHl,
                        activeHl = activeHl,
                    )
                    if (rowIndex < block.rows.lastIndex) {
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

private data class CellMeasureKey(
    val text: String,
    val styleIdentity: Int,
    val availableWidthPx: Int?,
    val softWrap: Boolean,
)

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

/**
 * Header typography only (weight / platform style). Column [TextAlign] is applied
 * per-cell from [TableAlignment] so header and body share the same alignment policy.
 */
internal fun resolveTableHeaderTextStyle(bodyStyle: TextStyle): TextStyle = bodyStyle.copy(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.sp,
    // Default; each cell overwrites with [tableColumnTextAlign].
    textAlign = TextAlign.Start,
    textDirection = TextDirection.ContentOrLtr,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Shared header/body mapping from GFM column alignment to Compose [TextAlign]. */
internal fun tableColumnTextAlign(alignment: TableAlignment): TextAlign = when (alignment) {
    TableAlignment.Start -> TextAlign.Start
    TableAlignment.Center -> TextAlign.Center
    TableAlignment.End -> TextAlign.End
}

/**
 * Pure geometry for a cell's text box inside a fixed column width.
 * Header and body must use the same [cellWidthPx] / padding / [alignment] so
 * start/center/end edges line up regardless of different text lengths.
 */
internal data class TableCellTextPlacement(
    val cellWidthPx: Float,
    val paddingStartPx: Float,
    val paddingEndPx: Float,
    val textWidthPx: Float,
    val alignment: TableAlignment,
) {
    val contentWidthPx: Float
        get() = (cellWidthPx - paddingStartPx - paddingEndPx).coerceAtLeast(0f)

    val textStartPx: Float
        get() {
            val free = (contentWidthPx - textWidthPx).coerceAtLeast(0f)
            return paddingStartPx + when (alignment) {
                TableAlignment.Start -> 0f
                TableAlignment.Center -> free / 2f
                TableAlignment.End -> free
            }
        }

    val textEndPx: Float get() = textStartPx + textWidthPx.coerceAtMost(contentWidthPx)
    val textCenterPx: Float get() = (textStartPx + textEndPx) / 2f
}

@Composable
private fun TableHeaderRow(
    cells: List<String>,
    annotationOffsets: List<Int>,
    alignments: List<TableAlignment>,
    columnWidths: List<Dp>,
    resolvedHeaderTextStyle: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onHeaderCellLayout: ((Int, TableHeaderCellLayoutMetadata) -> Unit)?,
    onLinkClick: ((String) -> Unit)? = null,
    cellHighlight: (Int) -> List<SearchHighlightRange> = { emptyList() },
    normalHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    activeHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    Row(modifier = Modifier.testTag("markdown-table-row-header")) {
        columnWidths.forEachIndexed { column, width ->
            val alignment = alignments.getOrElse(column) { TableAlignment.Start }
            val source = cells.getOrElse(column) { "" }
            val ranges = cellHighlight(column)
            val baseAnnotated = remember(source, inlineStyles) {
                InlineMarkdownParser.parse(source.ifEmpty { " " }, inlineStyles)
            }
            val annotated = if (ranges.isEmpty()) {
                baseAnnotated
            } else {
                remember(baseAnnotated, ranges, normalHl, activeHl) {
                    baseAnnotated.withSearchHighlights(ranges, normalHl, activeHl)
                }
            }
            // Same geometry as body: fixed column width + identical horizontal padding.
            val cellModifier = Modifier
                .alignByBaseline()
                .width(width)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .fillMaxWidth()
                .testTag("markdown-table-cell-header-$column")
            val onLayout: (TextLayoutResult) -> Unit = { result ->
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
            }
            TableAnnotatedText(
                annotated = annotated,
                annotationOffset = annotationOffsets.getOrElse(column) { 0 },
                style = resolvedHeaderTextStyle,
                textAlign = tableColumnTextAlign(alignment),
                onLinkClick = onLinkClick,
                onTextLayout = onLayout,
                modifier = cellModifier,
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    annotationOffsets: List<Int>,
    alignments: List<TableAlignment>,
    columnWidths: List<Dp>,
    style: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    rowTag: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    cellHighlight: (Int) -> List<SearchHighlightRange> = { emptyList() },
    normalHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    activeHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    Row(
        modifier = modifier.testTag("markdown-table-row-$rowTag"),
        verticalAlignment = Alignment.Top,
    ) {
        columnWidths.forEachIndexed { column, width ->
            TableCell(
                text = cells.getOrElse(column) { "" },
                annotationOffset = annotationOffsets.getOrElse(column) { 0 },
                style = style,
                inlineStyles = inlineStyles,
                alignment = alignments.getOrElse(column) { TableAlignment.Start },
                searchRanges = cellHighlight(column),
                normalHl = normalHl,
                activeHl = activeHl,
                onLinkClick = onLinkClick,
                // Same width + padding as header cells (shared columnWidths + tokens).
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
    annotationOffset: Int,
    style: TextStyle,
    inlineStyles: InlineMarkdownStyles,
    alignment: TableAlignment,
    modifier: Modifier = Modifier,
    searchRanges: List<SearchHighlightRange> = emptyList(),
    normalHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    activeHl: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val baseAnnotated = remember(text, inlineStyles) {
        InlineMarkdownParser.parse(text.ifEmpty { " " }, inlineStyles)
    }
    // Rendering text equals the search plain text, so offsets apply directly.
    val rawRanges = searchRanges
    val annotated = if (rawRanges.isEmpty()) {
        baseAnnotated
    } else {
        remember(baseAnnotated, rawRanges, normalHl, activeHl) {
            baseAnnotated.withSearchHighlights(rawRanges, normalHl, activeHl)
        }
    }
    val resolvedStyle = style.copy(
        fontFamily = style.fontFamily ?: FontFamily.Default,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
    )
    TableAnnotatedText(
        annotated = annotated,
        annotationOffset = annotationOffset,
        style = resolvedStyle,
        textAlign = tableColumnTextAlign(alignment),
        onLinkClick = onLinkClick,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Table cell text that activates inline Markdown link annotations when present.
 * Keeps non-link cells on plain [Text] for lower cost.
 */
@Composable
private fun TableAnnotatedText(
    annotated: AnnotatedString,
    annotationOffset: Int,
    style: TextStyle,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val selection = rememberMarkdownSelectedText(annotated, annotationOffset)
    val hasLinks = remember(annotated, onLinkClick != null) {
        onLinkClick != null && annotated.getStringAnnotations(
            tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
            start = 0,
            end = annotated.length,
        ).isNotEmpty()
    }
    val resolvedStyle = style.copy(textAlign = textAlign)
    if (hasLinks) {
        @Suppress("DEPRECATION")
        ClickableText(
            text = selection.text,
            style = resolvedStyle,
            softWrap = true,
            overflow = TextOverflow.Clip,
            maxLines = Int.MAX_VALUE,
            modifier = modifier.then(selection.modifier),
            onTextLayout = { selection.layout(it); onTextLayout?.invoke(it) },
            onClick = { offset ->
                annotated.getStringAnnotations(
                    tag = InlineMarkdownParser.LINK_ANNOTATION_TAG,
                    start = offset,
                    end = offset,
                ).firstOrNull()?.let { onLinkClick?.invoke(it.item) }
            },
        )
    } else {
        Text(
            text = selection.text,
            modifier = modifier.then(selection.modifier),
            style = resolvedStyle,
            textAlign = textAlign,
            softWrap = true,
            overflow = TextOverflow.Clip,
            maxLines = Int.MAX_VALUE,
            onTextLayout = { selection.layout(it); onTextLayout?.invoke(it) },
        )
    }
}
