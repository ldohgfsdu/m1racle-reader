package com.markleaf.app.markdown

/** One normalization contract shared by generated headings and authored `#fragment` links. */
object MarkdownAnchorSlug {
    fun normalize(value: String): String = value
        .removePrefix("#")
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}\s-]"""), "")
        .trim()
        .replace(Regex("""\s+"""), "-")
        .ifBlank { "heading" }
}
