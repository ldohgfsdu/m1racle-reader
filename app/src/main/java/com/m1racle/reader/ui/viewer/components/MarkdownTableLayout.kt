package com.m1racle.reader.ui.viewer.components

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Centralized responsive-table sizing tokens. Values are density-independent pixels (dp). */
internal data class MarkdownTableLayoutTokens(
    val absoluteMinimumDp: Float = 72f,
    val preferredMinimumDp: Float = 136f,
    val normalMaximumDp: Float = 240f,
    val longTextMaximumDp: Float = 280f,
    val horizontalCellPaddingDp: Float = 10f,
    val verticalCellPaddingDp: Float = 10f,
    val tableTextScale: Float = 0.88f,
    val compactTwoColumnSafetyDp: Float = 16f,
    val widePrimaryColumnSafetyDp: Float = 8f,
    val wideIdentifierSafetyDp: Float = 2f,
    val twoColumnReadableTextMaximumDp: Float = 112f,
    val wideTableReadableTextMaximumDp: Float = 120f,
    val readableTextFloorDp: Float = 48f,
    val wideTableReadableTextFloorDp: Float = 64f,
    val wideIdentifierTextFloorDp: Float = 36f,
    val wideIdentifierMinimumDp: Float = 56f,
    val twoColumnCandidateFractions: List<Float> =
        listOf(0.28f, 0.32f, 0.36f, 0.40f, 0.44f, 0.48f, 0.52f, 0.56f, 0.60f, 0.64f, 0.68f, 0.72f),
    val shortLabelCharacterLimit: Int = 12,
    val narrowPillarCharactersPerLine: Float = 3f,
    val longTextThreshold: Int = 28,
)

/** Diagnostic only. This classification never selects widths. */
internal enum class TwoColumnContentMode {
    COMPACT_LABEL_VALUE,
    VERBOSE_COMPARISON,
}

internal data class TableColumnMeasurement(
    /** Largest single-line rendered text width in this column, excluding cell padding. */
    val contentWidthPx: Float,
    val maxCharacterCount: Int,
    /** Original plain-text character counts, before any constrained wrapping. */
    val characterCounts: List<Int> = listOf(maxCharacterCount),
    /** Single-line widths for every header/body cell, before constrained wrapping. */
    val unconstrainedCellWidthsPx: List<Float> = listOf(contentWidthPx),
    val headerWidthPx: Float = unconstrainedCellWidthsPx.firstOrNull() ?: contentWidthPx,
)

internal data class WrappedCellMeasurement(
    val heightPx: Float,
    val lineCount: Int,
    val characterCount: Int,
)

internal data class WrappedRowMeasurement(
    val cells: List<WrappedCellMeasurement>,
    val isHeader: Boolean,
)

/** One finite TextMeasurer-backed width candidate for a two-column table. */
internal data class TwoColumnWidthCandidate(
    val firstFraction: Float,
    val widthsPx: List<Float>,
    val rows: List<WrappedRowMeasurement>,
)

internal data class TableColumnMetrics(
    val widthsPx: List<Float>,
    val totalWidthPx: Float,
    val viewportWidthPx: Float,
    val horizontallyScrollable: Boolean,
    val twoColumnContentMode: TwoColumnContentMode? = null,
    val selectedTwoColumnFraction: Float? = null,
    val selectedTwoColumnCost: Float? = null,
    val usedMeasuredCandidateOptimization: Boolean = false,
    val readableWidthsPx: List<Float> = emptyList(),
    val preferredWidthsPx: List<Float> = emptyList(),
    val maximumWidthsPx: List<Float> = emptyList(),
)

/** Pure column-width policy shared by every row in one Markdown table. */
internal object MarkdownTableLayoutEngine {
    val Tokens = MarkdownTableLayoutTokens()

    fun calculate(
        viewportWidthPx: Float,
        density: Float,
        columns: List<TableColumnMeasurement>,
        twoColumnCandidates: List<TwoColumnWidthCandidate> = emptyList(),
        tokens: MarkdownTableLayoutTokens = Tokens,
    ): TableColumnMetrics {
        if (columns.isEmpty()) {
            return TableColumnMetrics(emptyList(), 0f, viewportWidthPx.coerceAtLeast(0f), false)
        }
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val viewport = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val absoluteMinimum = tokens.absoluteMinimumDp * safeDensity
        val preferredMinimum = tokens.preferredMinimumDp * safeDensity
        val normalMaximum = tokens.normalMaximumDp * safeDensity
        val longMaximum = tokens.longTextMaximumDp * safeDensity
        val horizontalPadding = tokens.horizontalCellPaddingDp * 2f * safeDensity
        val compactWideIdentifier = columns.size >= 3 &&
            columns.first().maxCharacterCount <= tokens.shortLabelCharacterLimit

        val readableWidths = columns.mapIndexed { index, column ->
            val isWideIdentifier = compactWideIdentifier && index == 0
            readableWidth(
                column = column,
                safeDensity = safeDensity,
                horizontalPadding = horizontalPadding,
                readableTextMaximumDp = if (columns.size == 2) {
                    tokens.twoColumnReadableTextMaximumDp
                } else {
                    tokens.wideTableReadableTextMaximumDp
                },
                readableTextFloorDp = when {
                    columns.size == 2 -> tokens.readableTextFloorDp
                    isWideIdentifier -> tokens.wideIdentifierTextFloorDp
                    else -> tokens.wideTableReadableTextFloorDp
                },
                absoluteMinimumDp = if (isWideIdentifier) {
                    tokens.wideIdentifierMinimumDp
                } else {
                    tokens.absoluteMinimumDp
                },
            )
        }
        val maximumWidths = columns.map { column ->
            val content = column.contentWidthPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
            if (
                column.maxCharacterCount >= tokens.longTextThreshold ||
                content + horizontalPadding > normalMaximum
            ) longMaximum else normalMaximum
        }
        val preferredWidths = columns.mapIndexed { index, column ->
            val content = if (columns.size >= 3) {
                widePreferredContentWidth(column, tokens)
            } else {
                column.contentWidthPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
            }
            val maximum = maximumWidths[index]
            val primarySafety = when {
                columns.size < 3 -> 0f
                index == 0 && compactWideIdentifier ->
                    tokens.wideIdentifierSafetyDp * safeDensity
                index == 0 -> 0f
                else -> tokens.widePrimaryColumnSafetyDp * safeDensity
            }
            (content + horizontalPadding + primarySafety)
                .coerceIn(readableWidths[index], maximum)
        }

        val mode = if (columns.size == 2) classifyTwoColumn(columns) else null
        val twoColumnTargetWidth = if (columns.size == 2) {
            if (readableWidths.sum() > viewport) readableWidths.sum() else
                (preferredWidths.sum() + tokens.compactTwoColumnSafetyDp * safeDensity)
                    .coerceIn(readableWidths.sum(), viewport)
        } else {
            viewport
        }
        var selectedFraction: Float? = null
        var selectedCost: Float? = null
        var usedOptimization = false
        var widths = when (columns.size) {
            1 -> mutableListOf(max(viewport, preferredWidths.single().coerceAtLeast(preferredMinimum)))
            2 -> {
                if (readableWidths.sum() > viewport) {
                    // Sole two-column overflow condition: the measured readable minima cannot fit.
                    readableWidths.toMutableList()
                } else {
                    val validCandidates = twoColumnCandidates.filter { candidate ->
                        candidate.widthsPx.size == 2 &&
                            candidate.rows.isNotEmpty() &&
                            candidate.widthsPx.indices.all { column ->
                                candidate.widthsPx[column].isFinite() &&
                                    candidate.widthsPx[column] >= readableWidths[column]
                            } &&
                            abs(candidate.widthsPx.sum() - twoColumnTargetWidth) <= 1f
                    }
                    if (validCandidates.isNotEmpty()) {
                        val scored = validCandidates.map { candidate ->
                            candidate to twoColumnCost(candidate, columns, twoColumnTargetWidth, tokens)
                        }
                        val best = scored.minWithOrNull(
                            compareBy<Pair<TwoColumnWidthCandidate, Float>> { it.second }
                                .thenBy { it.first.firstFraction },
                        )!!
                        selectedFraction = best.first.firstFraction
                        selectedCost = best.second
                        usedOptimization = true
                        best.first.widthsPx.toMutableList()
                    } else {
                        val firstCharacters = columns[0].characterCounts.sum().coerceAtLeast(1)
                        val secondCharacters = columns[1].characterCounts.sum().coerceAtLeast(1)
                        val contentFraction = firstCharacters.toFloat() /
                            (firstCharacters + secondCharacters).toFloat()
                        val firstFraction = tokens.twoColumnCandidateFractions
                            .filter { fraction ->
                                twoColumnTargetWidth * fraction >= readableWidths[0] &&
                                    twoColumnTargetWidth * (1f - fraction) >= readableWidths[1]
                            }
                            .minByOrNull { abs(it - contentFraction) }
                            ?: 0.5f
                        selectedFraction = firstFraction
                        mutableListOf(
                            twoColumnTargetWidth * firstFraction,
                            twoColumnTargetWidth * (1f - firstFraction),
                        )
                    }
                }
            }
            // Wide tables keep content-led preferred widths and overflow naturally. Fitting more
            // later columns into the first viewport must not create avoidable wrapping in an
            // earlier primary content column.
            else -> preferredWidths.toMutableList()
        }

        var total = widths.sum()
        // A naturally fitting table expands to the viewport. Overflow is only the result of real
        // content widths after readable compression; no preview/fade shaping is applied.
        if (total < viewport && columns.size != 2) {
            val expandable = if (columns.size >= 3) {
                widths.indices.drop(1)
            } else {
                widths.indices.toList()
            }
            val extraPerColumn = (viewport - total) / expandable.size
            expandable.forEach { widths[it] += extraPerColumn }
            total = widths.sum()
            widths[widths.lastIndex] += viewport - total
        }

        val safeWidths = widths.mapIndexed { index, width ->
            val minimum = if (compactWideIdentifier && index == 0) {
                tokens.wideIdentifierMinimumDp * safeDensity
            } else {
                absoluteMinimum
            }
            width.coerceAtLeast(minimum)
        }
        val safeTotal = safeWidths.sum()
        return TableColumnMetrics(
            widthsPx = safeWidths,
            totalWidthPx = safeTotal,
            viewportWidthPx = viewport,
            horizontallyScrollable = safeTotal > viewport + 0.5f,
            twoColumnContentMode = mode,
            selectedTwoColumnFraction = selectedFraction,
            selectedTwoColumnCost = selectedCost,
            usedMeasuredCandidateOptimization = usedOptimization,
            readableWidthsPx = readableWidths,
            preferredWidthsPx = preferredWidths,
            maximumWidthsPx = maximumWidths,
        )
    }

    internal fun twoColumnTargetWidth(
        viewportWidthPx: Float,
        density: Float,
        columns: List<TableColumnMeasurement>,
        tokens: MarkdownTableLayoutTokens = Tokens,
    ): Float {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val viewport = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (columns.size != 2) return viewport
        val horizontalPadding = tokens.horizontalCellPaddingDp * 2f * safeDensity
        val readable = columns.map { column ->
            readableWidth(
                column = column,
                safeDensity = safeDensity,
                horizontalPadding = horizontalPadding,
                readableTextMaximumDp = tokens.twoColumnReadableTextMaximumDp,
                readableTextFloorDp = tokens.readableTextFloorDp,
                absoluteMinimumDp = tokens.absoluteMinimumDp,
            )
        }
        val normalMaximum = tokens.normalMaximumDp * safeDensity
        val longMaximum = tokens.longTextMaximumDp * safeDensity
        val preferred = columns.mapIndexed { index, column ->
            val content = column.contentWidthPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
            val maximum = if (
                column.maxCharacterCount >= tokens.longTextThreshold ||
                content + horizontalPadding > normalMaximum
            ) longMaximum else normalMaximum
            (content + horizontalPadding).coerceIn(readable[index], maximum)
        }
        return if (readable.sum() > viewport) readable.sum() else
            (preferred.sum() + tokens.compactTwoColumnSafetyDp * safeDensity)
                .coerceIn(readable.sum(), viewport)
    }

    private fun readableWidth(
        column: TableColumnMeasurement,
        safeDensity: Float,
        horizontalPadding: Float,
        readableTextMaximumDp: Float,
        readableTextFloorDp: Float,
        absoluteMinimumDp: Float,
    ): Float {
        val finiteWidths = column.unconstrainedCellWidthsPx
            .filter { it.isFinite() && it >= 0f }
            .ifEmpty { listOf(column.contentWidthPx.coerceAtLeast(0f)) }
            .sorted()
        val representativeWidth = finiteWidths[(finiteWidths.lastIndex * 3) / 4]
        val headerWidth = column.headerWidthPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val readableText = max(headerWidth, representativeWidth * 0.65f).coerceIn(
            readableTextFloorDp * safeDensity,
            readableTextMaximumDp * safeDensity,
        )
        return (readableText + horizontalPadding)
            .coerceAtLeast(absoluteMinimumDp * safeDensity)
    }

    private fun widePreferredContentWidth(
        column: TableColumnMeasurement,
        tokens: MarkdownTableLayoutTokens,
    ): Float {
        val finitePairs = column.unconstrainedCellWidthsPx
            .zip(column.characterCounts)
            .filter { (width, count) -> width.isFinite() && width >= 0f && count >= 0 }
        val shortContentWidth = finitePairs
            .filter { (_, count) -> count <= tokens.shortLabelCharacterLimit }
            .maxOfOrNull { (width, _) -> width }
            ?: 0f
        val headerWidth = column.headerWidthPx.takeIf { it.isFinite() && it >= 0f } ?: 0f
        return max(headerWidth, shortContentWidth)
    }

    internal fun twoColumnCost(
        candidate: TwoColumnWidthCandidate,
        columns: List<TableColumnMeasurement>,
        viewport: Float,
        tokens: MarkdownTableLayoutTokens = Tokens,
    ): Float {
        val rowHeightCost = candidate.rows.sumOf { row ->
            row.cells.maxOfOrNull { it.heightPx.toDouble() } ?: 0.0
        }.toFloat()
        val maxLineCount = candidate.rows.maxOfOrNull { row ->
            row.cells.maxOfOrNull { it.lineCount } ?: 1
        } ?: 1
        var shortLabelWrapPenalty = 0f
        var headerWrapPenalty = 0f
        var narrowPillarPenalty = 0f
        var rowHeightImbalancePenalty = 0f
        candidate.rows.forEach { row ->
            if (row.cells.size >= 2) {
                rowHeightImbalancePenalty +=
                    abs(row.cells[0].heightPx - row.cells[1].heightPx) * 0.2f
            }
            row.cells.forEach { cell ->
                if (cell.characterCount in 1..tokens.shortLabelCharacterLimit && cell.lineCount > 1) {
                    shortLabelWrapPenalty += (cell.lineCount - 1) * viewport * 2f
                }
                if (row.isHeader && cell.lineCount > 1) {
                    headerWrapPenalty += (cell.lineCount - 1) * viewport
                }
                if (cell.characterCount > tokens.shortLabelCharacterLimit && cell.lineCount > 1) {
                    val charactersPerLine = cell.characterCount.toFloat() / cell.lineCount
                    if (charactersPerLine < tokens.narrowPillarCharactersPerLine) {
                        narrowPillarPenalty +=
                            (tokens.narrowPillarCharactersPerLine - charactersPerLine) * viewport
                    }
                }
            }
        }
        val firstCharacters = columns.getOrNull(0)?.characterCounts?.sum()?.coerceAtLeast(1) ?: 1
        val secondCharacters = columns.getOrNull(1)?.characterCounts?.sum()?.coerceAtLeast(1) ?: 1
        val contentFraction = (firstCharacters.toFloat() / (firstCharacters + secondCharacters))
            .coerceIn(0.28f, 0.72f)
        val contentBalancePenalty = abs(candidate.firstFraction - contentFraction) * viewport * 0.25f
        val firstColumnNeeded = columns.firstOrNull()?.contentWidthPx?.coerceAtLeast(0f) ?: 0f
        val firstColumnExcessPenalty =
            (candidate.widthsPx.firstOrNull()?.minus(firstColumnNeeded) ?: 0f)
                .coerceAtLeast(0f) * 0.5f
        val maximumLinePenalty = maxLineCount * viewport * 0.01f
        return rowHeightCost + shortLabelWrapPenalty + headerWrapPenalty + narrowPillarPenalty +
            rowHeightImbalancePenalty + contentBalancePenalty + firstColumnExcessPenalty +
            maximumLinePenalty
    }

    internal fun classifyTwoColumn(columns: List<TableColumnMeasurement>): TwoColumnContentMode {
        if (columns.size != 2) return TwoColumnContentMode.VERBOSE_COMPARISON
        val firstCounts = columns[0].characterCounts
            .map { it.coerceAtLeast(0) }
            .ifEmpty { listOf(columns[0].maxCharacterCount.coerceAtLeast(0)) }
            .sorted()
        val median = percentileNearestRank(firstCounts, 0.5f)
        val p90 = percentileNearestRank(firstCounts, 0.9f)
        val longRatio = firstCounts.count { it > 20 }.toFloat() / firstCounts.size
        return if (median <= 8 && p90 <= 14 && longRatio <= 0.15f) {
            TwoColumnContentMode.COMPACT_LABEL_VALUE
        } else {
            TwoColumnContentMode.VERBOSE_COMPARISON
        }
    }

    private fun percentileNearestRank(sortedValues: List<Int>, percentile: Float): Int {
        val index = ceil(sortedValues.size * percentile).toInt()
            .coerceIn(1, sortedValues.size) - 1
        return sortedValues[index]
    }
}
