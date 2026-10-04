package com.m1racle.reader.ui.epub

import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubLocation
import com.m1racle.reader.epub.EpubReadingAnchors
import com.m1racle.reader.epub.EpubRestoreTier
import com.m1racle.reader.epub.EpubSavedPosition

/**
 * One settled reading position. [anchor] is what restore uses; the flat row,
 * pixel offset and progress are still persisted for downgrade and fallback.
 */
internal data class EpubPosition(
    val anchor: DocumentAnchor.EpubText,
    val sourceRow: Int,
    val pixelOffset: Int,
    val progress: Float,
    val canSaveAnchor: Boolean = true,
) {
    fun saved() = EpubSavedPosition(anchor, sourceRow, pixelOffset, progress)
}

/**
 * Maps the paged reader's flat block rows to parser coordinates and back.
 * [EpubTextRow.blockIndex]/[EpubTextRow.sourceTextStart] already carry in-block
 * offsets, so measurement splits, list item rows and continuations need no
 * conversion beyond the row lookup.
 */
internal class EpubRowIndex(private val book: EpubBook, rows: List<EpubTextRow>) {
    private val locationBySource = HashMap<Int, Pair<Int, Int>>()
    private val sourceByBlock = HashMap<Pair<Int, Int>, Int>()

    init {
        rows.forEach { row ->
            locationBySource.putIfAbsent(row.sourceRow, row.spineIndex to row.blockIndex)
            sourceByBlock.putIfAbsent(row.spineIndex to row.blockIndex, row.sourceRow)
        }
    }

    fun location(sourceRow: Int, offset: Int): EpubLocation? =
        locationBySource[sourceRow]?.let { (spine, block) -> EpubLocation(spine, block, offset) }

    fun sourceRow(location: EpubLocation): Int? = sourceByBlock[location.spineIndex to location.blockIndex]

    fun anchorAt(sourceRow: Int, offset: Int): DocumentAnchor.EpubText? =
        location(sourceRow, offset)?.let { EpubReadingAnchors.anchorAt(book, it) }
}

/** Restored text coordinates of the paged layout, plus why when degraded. */
internal data class EpubRestoredText(val sourceRow: Int, val offset: Int, val tier: EpubRestoreTier)

/**
 * Applies [EpubReadingAnchors.restore] to a paged [layout]. Only the legacy
 * pixel migration and the progress fallback need page geometry; an anchor
 * resolves without it, so typography never changes where it lands.
 */
internal fun restoreEpubText(
    book: EpubBook,
    rows: EpubRowIndex,
    layout: EpubTextLayout,
    saved: EpubSavedPosition,
): EpubRestoredText {
    val result = EpubReadingAnchors.restore(book, saved)
    fun pageStart(page: Int): EpubRestoredText {
        val first = layout.pages[page.coerceIn(0, layout.pages.lastIndex)].slices.first()
        val position = layout.positionAt(first)
        return EpubRestoredText(position.first, position.third, result.tier)
    }
    result.location?.let { location ->
        rows.sourceRow(location)?.let { return EpubRestoredText(it, location.charOffset, result.tier) }
    }
    return when (result.tier) {
        EpubRestoreTier.LegacyPosition ->
            pageStart(layout.pageForPosition(result.legacyRow ?: 0, result.legacyPixelOffset ?: 0))
        EpubRestoreTier.Progress ->
            pageStart(kotlin.math.round((result.progress ?: 0f) * layout.pages.lastIndex).toInt())
        else -> pageStart(0)
    }
}
