package com.m1racle.reader.ui.hub

import com.m1racle.reader.data.local.DocumentEntity

/** 文库分类过滤器。 */
enum class LibraryFilter {
    ALL,
    PDF,
    MARKDOWN,
    EPUB,
}

object LibraryFilters {

    /**
     * 应用过滤（保持 lastOpenedAt DESC 原序）。
     * MARKDOWN 涵盖单文件 Markdown 与 Markdown 教材包，不含 EPUB。
     */
    fun apply(documents: List<DocumentEntity>, filter: LibraryFilter): List<DocumentEntity> =
        when (filter) {
            LibraryFilter.ALL -> documents
            LibraryFilter.PDF -> documents.filter { it.isPdf }
            LibraryFilter.MARKDOWN -> documents.filter { !it.isPdf && !it.isEpub }
            LibraryFilter.EPUB -> documents.filter { it.isEpub }
        }
}
