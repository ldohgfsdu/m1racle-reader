package com.m1racle.reader.markdown

/** Immutable document-local target for one parsed Markdown heading. */
data class MarkdownAnchorTarget(
    val anchorId: String,
    val blockIndex: Int,
    val headingIndex: Int,
)

/**
 * Immutable O(1) registry built together with the block AST.
 * Link activation never scans heading text or reparses the document.
 */
class AnchorRegistry private constructor(
    private val targetsById: Map<String, MarkdownAnchorTarget>,
) {
    val size: Int get() = targetsById.size
    val targets: List<MarkdownAnchorTarget> get() = targetsById.values.toList()

    fun resolve(anchorId: String): MarkdownAnchorTarget? {
        val raw = anchorId.removePrefix("#")
        return targetsById[raw] ?: targetsById[MarkdownAnchorSlug.normalize(raw)]
    }

    fun contains(anchorId: String): Boolean = resolve(anchorId) != null

    companion object {
        val Empty = AnchorRegistry(emptyMap())

        fun fromTargets(targets: List<MarkdownAnchorTarget>): AnchorRegistry = AnchorRegistry(
            targets.associateBy { it.anchorId },
        )
    }
}
