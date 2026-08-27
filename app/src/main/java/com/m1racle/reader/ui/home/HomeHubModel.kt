package com.m1racle.reader.ui.home

import com.m1racle.reader.data.local.DocumentEntity
import kotlin.math.roundToInt

/**
 * H2.2 Hub 纯选择器/格式化（宿主可测）。数据源唯一事实源 =
 * DocumentEntity 阅读字段族；输入列表约定为 lastOpenedAt DESC
 * （DocumentDao.observeRecentDocuments 原序）。
 */
object HomeHubSelectors {

    /**
     * 继续阅读目标：最近一条「有阅读记录」的文档。
     * 无任何记录（空库或全部 lastOpenedAt<=0）→ null，UI 优雅降级隐藏卡片。
     */
    fun continueReading(documentsByRecentDesc: List<DocumentEntity>): DocumentEntity? =
        documentsByRecentDesc.firstOrNull { it.lastOpenedAt > 0L }
}

/** 当前阅读位置行：PDF=页码，Markdown=进度语义。复用既有格式化实现。 */
fun readingPositionLine(document: DocumentEntity): String =
    if (document.isPdf) {
        formatPdfProgress(document)
    } else {
        formatReadingProgress(document.readingProgress)
    }

/** 阅读进度百分比 0–100。 */
fun progressPercent(document: DocumentEntity): Int =
    (document.readingProgress.coerceIn(0f, 1f) * 100f).roundToInt()
