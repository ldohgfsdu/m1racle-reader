package com.m1racle.reader.pdf.export

import kotlin.math.min

enum class PdfSplitMode {
    BY_CHAPTER,
    BY_PAGE_COUNT,
}

sealed interface PdfSplitChapterLevel {
    /** Split the first outline level only. */
    data object TopLevel : PdfSplitChapterLevel

    /** Split one zero-based outline depth. Depth 0 is the top level. */
    data class Depth(val requestedDepth0: Int) : PdfSplitChapterLevel {
        init {
            require(requestedDepth0 >= 0) { "章节层级不能为负" }
        }
    }

    val depth0: Int
        get() = when (this) {
            TopLevel -> 0
            is Depth -> requestedDepth0
        }
}

enum class PdfSplitSamePagePolicy {
    /** Put every same-start chapter title into one continuous output part. */
    MERGE,

    /** Keep the first deterministic chapter and omit later same-start nodes. */
    KEEP_FIRST,
}

enum class PdfSplitPartKind {
    CHAPTER,
    LEADING_CONTENT,
    PAGE_RANGE,
}

/** Inclusive page range using 0-based source PDF indices. */
data class PdfSplitPageRange(
    val startPageIndex: Int,
    val endPageIndex: Int,
) {
    val pageCount: Int
        get() = endPageIndex - startPageIndex + 1
}

data class PdfSplitChapterSource(
    val id: String,
    val title: String,
    val pathTitles: List<String>,
    val range: PdfSplitPageRange,
)

data class PdfSplitPart(
    val index: Int,
    val title: String,
    val outputName: String,
    val sourceRange: PdfSplitPageRange,
    val kind: PdfSplitPartKind = PdfSplitPartKind.CHAPTER,
)

data class PdfSplitPlan(
    val mode: PdfSplitMode,
    val selection: PdfSplitPageRange,
    val sourcePageCount: Int,
    val parts: List<PdfSplitPart>,
    val chapterLevel: PdfSplitChapterLevel? = null,
    val samePagePolicy: PdfSplitSamePagePolicy = PdfSplitSamePagePolicy.MERGE,
) {
    val totalPages: Int
        get() = parts.sumOf { it.sourceRange.pageCount }
}

/**
 * Pure split planning for deterministic JVM tests and UI previews.
 *
 * Page-count chunks always retain a short final group. Chapter planning keeps
 * only one outline depth, resolves same-start chapters, inserts missing leading
 * content, then partitions the complete selection at chapter starts.
 */
object PdfSplitPlanner {

    fun byChapters(
        documentDisplayName: String,
        sourcePageCount: Int,
        chapters: List<PdfSplitChapterSource>,
        selection: PdfSplitPageRange? = null,
        chapterLevel: PdfSplitChapterLevel = PdfSplitChapterLevel.TopLevel,
        samePagePolicy: PdfSplitSamePagePolicy = PdfSplitSamePagePolicy.MERGE,
    ): PdfSplitPlan {
        val documentRange = validatedDocumentRange(sourcePageCount)
        val selected = validateSelection(selection ?: documentRange, documentRange)
        require(chapters.isNotEmpty()) { "至少需要一个章节" }
        chapters.forEach(::validateChapter)

        val requestedDepth = chapterLevel.depth0
        val selectedLevel = chapters
            .filter { it.pathTitles.size == requestedDepth + 1 }
            .filter {
                it.range.startPageIndex <= selected.endPageIndex &&
                    it.range.endPageIndex >= selected.startPageIndex
            }
        require(selectedLevel.isNotEmpty()) {
            "所选页段在第 ${requestedDepth + 1} 层没有章节"
        }

        val boundaries = selectedLevel
            .groupBy { maxOf(it.range.startPageIndex, selected.startPageIndex) }
            .toSortedMap()
            .map { (effectiveStart, sameStart) ->
                // Stable ties retain the document's outline order, including
                // same-page bookmarks whose titles have a different sort order.
                val ordered = sameStart.sortedBy { it.range.startPageIndex }
                val chosen = when (samePagePolicy) {
                    PdfSplitSamePagePolicy.MERGE -> ordered
                    PdfSplitSamePagePolicy.KEEP_FIRST -> listOf(ordered.first())
                }
                ChapterBoundary(
                    startPageIndex = effectiveStart,
                    title = chosen.map { it.title.trim().ifBlank { "未命名章节" } }
                        .distinct()
                        .joinToString(" / "),
                    pathTitles = chosen.first().pathTitles,
                )
            }

        val usedNames = mutableSetOf<String>()
        val parts = ArrayList<PdfSplitPart>()
        val firstBoundaryStart = boundaries.first().startPageIndex
        if (selected.startPageIndex < firstBoundaryStart) {
            val range = PdfSplitPageRange(
                selected.startPageIndex,
                firstBoundaryStart - 1,
            )
            parts += PdfSplitPart(
                index = 1,
                title = "封面与前置内容",
                outputName = uniquePdfName(
                    base = leadingContentBaseName(documentDisplayName, range),
                    usedNames = usedNames,
                ),
                sourceRange = range,
                kind = PdfSplitPartKind.LEADING_CONTENT,
            )
        }

        boundaries.forEachIndexed { index, boundary ->
            val end = if (index < boundaries.lastIndex) {
                boundaries[index + 1].startPageIndex - 1
            } else {
                selected.endPageIndex
            }
            val range = PdfSplitPageRange(boundary.startPageIndex, end)
            parts += PdfSplitPart(
                index = parts.size + 1,
                title = boundary.title,
                outputName = uniquePdfName(
                    base = ExportRangeResolver.defaultPdfName(
                        documentDisplayName,
                        boundary.pathTitles.ifEmpty { listOf(boundary.title) },
                    ).removeSuffix(".pdf"),
                    usedNames = usedNames,
                ),
                sourceRange = range,
                kind = PdfSplitPartKind.CHAPTER,
            )
        }

        assertContinuous(parts, selected)
        return PdfSplitPlan(
            mode = PdfSplitMode.BY_CHAPTER,
            selection = selected,
            sourcePageCount = sourcePageCount,
            parts = parts,
            chapterLevel = chapterLevel,
            samePagePolicy = samePagePolicy,
        )
    }

    fun byPageCount(
        documentDisplayName: String,
        sourcePageCount: Int,
        pagesPerPart: Int,
        selection: PdfSplitPageRange? = null,
    ): PdfSplitPlan {
        require(pagesPerPart > 0) { "每份页数必须大于 0" }
        val documentRange = validatedDocumentRange(sourcePageCount)
        val selected = validateSelection(selection ?: documentRange, documentRange)
        val usedNames = mutableSetOf<String>()
        val parts = buildList {
            var chunkStart = selected.startPageIndex
            while (chunkStart <= selected.endPageIndex) {
                val remainingPages = selected.endPageIndex - chunkStart + 1
                val chunkSize = min(pagesPerPart, remainingPages)
                val chunkEnd = chunkStart + chunkSize - 1
                val range = PdfSplitPageRange(chunkStart, chunkEnd)
                add(
                    PdfSplitPart(
                        index = size + 1,
                        title = "第 ${range.startPageIndex + 1}-${range.endPageIndex + 1} 页",
                        outputName = uniquePdfName(
                            base = pageCountBaseName(documentDisplayName, range),
                            usedNames = usedNames,
                        ),
                        sourceRange = range,
                        kind = PdfSplitPartKind.PAGE_RANGE,
                    ),
                )
                chunkStart = chunkEnd + 1
            }
        }
        assertContinuous(parts, selected)
        return PdfSplitPlan(
            mode = PdfSplitMode.BY_PAGE_COUNT,
            selection = selected,
            sourcePageCount = sourcePageCount,
            parts = parts,
        )
    }

    private fun validatedDocumentRange(sourcePageCount: Int): PdfSplitPageRange {
        require(sourcePageCount > 0) { "文档无有效页" }
        return PdfSplitPageRange(0, sourcePageCount - 1)
    }

    private fun validateSelection(
        selection: PdfSplitPageRange,
        documentRange: PdfSplitPageRange,
    ): PdfSplitPageRange {
        require(selection.startPageIndex >= documentRange.startPageIndex) {
            "起始页超出文档范围"
        }
        require(selection.endPageIndex <= documentRange.endPageIndex) {
            "结束页超出文档范围"
        }
        require(selection.startPageIndex <= selection.endPageIndex) {
            "起始页不能大于结束页"
        }
        return selection
    }

    private fun validateChapter(chapter: PdfSplitChapterSource) {
        require(chapter.pathTitles.isNotEmpty()) {
            "章节必须包含目录路径与层级"
        }
        require(chapter.range.startPageIndex >= 0) { "章节起始页不能为负" }
        require(chapter.range.endPageIndex >= chapter.range.startPageIndex) {
            "章节起始页不能大于结束页"
        }
    }

    private fun assertContinuous(
        parts: List<PdfSplitPart>,
        selection: PdfSplitPageRange,
    ) {
        require(parts.isNotEmpty()) { "拆分计划不能为空" }
        require(parts.first().sourceRange.startPageIndex == selection.startPageIndex) {
            "拆分计划未覆盖选区起始页"
        }
        require(parts.last().sourceRange.endPageIndex == selection.endPageIndex) {
            "拆分计划未覆盖选区末页"
        }
        parts.zipWithNext().forEach { (current, next) ->
            require(current.sourceRange.endPageIndex + 1 == next.sourceRange.startPageIndex) {
                "拆分计划存在重叠或漏页"
            }
        }
    }

    private fun leadingContentBaseName(
        documentDisplayName: String,
        range: PdfSplitPageRange,
    ): String {
        val documentBase = ExportRangeResolver.sanitizeFileName(
            documentDisplayName.removeSuffix(".pdf").removeSuffix(".PDF"),
            maxLen = 50,
        )
        val end = (range.endPageIndex + 1).toString().padStart(4, '0')
        return "${documentBase}_前置内容_$end"
    }

    private fun pageCountBaseName(
        documentDisplayName: String,
        range: PdfSplitPageRange,
    ): String {
        val documentBase = ExportRangeResolver.sanitizeFileName(
            documentDisplayName.removeSuffix(".pdf").removeSuffix(".PDF"),
            maxLen = 50,
        )
        val start = range.startPageIndex + 1
        val end = range.endPageIndex + 1
        return "${documentBase}_${start.toString().padStart(4, '0')}-" +
            end.toString().padStart(4, '0')
    }

    private fun uniquePdfName(base: String, usedNames: MutableSet<String>): String {
        val cleanBase = ExportRangeResolver.sanitizeFileName(base, maxLen = 100)
        var candidate = "$cleanBase.pdf"
        var suffix = 2
        while (!usedNames.add(candidate.lowercase())) {
            candidate = "${cleanBase}_$suffix.pdf"
            suffix += 1
        }
        return candidate
    }

    private data class ChapterBoundary(
        val startPageIndex: Int,
        val title: String,
        val pathTitles: List<String>,
    )
}
