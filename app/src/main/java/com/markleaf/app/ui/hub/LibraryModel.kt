package com.markleaf.app.ui.hub

import com.markleaf.app.data.local.DocumentEntity

/** 文库分类过滤器。EPUB 为 PRD 外扩展位：新增枚举值即可，不实现。 */
enum class LibraryFilter {
    ALL,
    PDF,
    MARKDOWN,
}

object LibraryFilters {

    /**
     * 应用过滤（保持 lastOpenedAt DESC 原序）。
     * MARKDOWN 涵盖单文件 Markdown 与 Markdown 教材包。
     */
    fun apply(documents: List<DocumentEntity>, filter: LibraryFilter): List<DocumentEntity> =
        when (filter) {
            LibraryFilter.ALL -> documents
            LibraryFilter.PDF -> documents.filter { it.isPdf }
            LibraryFilter.MARKDOWN -> documents.filter { !it.isPdf }
        }
}
