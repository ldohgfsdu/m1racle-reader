package com.m1racle.reader.epub

/** In-memory EPUB document model (route B). Never converted to Markdown AST. */
data class EpubMetadata(
    val title: String?,
    val creators: List<String>,
    val language: String?,
    val identifier: String?,
    val version: String?,
    val isFixedLayout: Boolean,
    val hasEncryption: Boolean,
)

data class EpubSpineItem(
    val idref: String,
    val href: String,
    val linear: Boolean,
    val index: Int,
)

data class EpubTocNode(
    val title: String,
    val href: String,
    val spineIndex: Int?,
    val children: List<EpubTocNode>,
)

/** Neutral inline run; links degrade to plain text in MVP (no click handling). */
data class EpubInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

sealed interface EpubBlock {
    data class Heading(val level: Int, val text: String) : EpubBlock
    data class Paragraph(val inlines: List<EpubInline>) : EpubBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<EpubInline>>) : EpubBlock
    data class Quote(val blocks: List<EpubBlock>) : EpubBlock
    data class Code(val text: String) : EpubBlock
    data class Image(val alt: String?, val src: String? = null, val resolvedPath: String? = null) : EpubBlock
    data object Divider : EpubBlock
    data class Unsupported(val label: String) : EpubBlock
}

data class EpubChapter(
    val spineIndex: Int,
    val href: String,
    val title: String?,
    val blocks: List<EpubBlock>,
    /** Element id -> block index, for TOC fragment jumps. */
    val idAnchors: Map<String, Int>,
)

data class EpubBook(
    val metadata: EpubMetadata,
    val spine: List<EpubSpineItem>,
    val toc: List<EpubTocNode>,
    val chapters: List<EpubChapter>,
) {
    /** Reading order: linear spine items only. */
    val readingOrder: List<EpubChapter> = chapters.filterIndexed { i, _ ->
        spine.getOrNull(i)?.linear != false
    }
}

sealed class EpubException(message: String) : Exception(message) {
    class NotAnEpub(message: String) : EpubException(message)
    class Protected(message: String) : EpubException(message)
    class FixedLayout(message: String) : EpubException(message)
    class Corrupt(message: String) : EpubException(message)
    class UnsafeEntry(message: String) : EpubException(message)
    class TooLarge(message: String) : EpubException(message)
}
