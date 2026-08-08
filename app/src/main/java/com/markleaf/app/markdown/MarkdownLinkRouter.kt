package com.markleaf.app.markdown

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure routing for Markdown link destinations.
 * In-page `#fragment` never goes to the system URI handler / external browser.
 */
object MarkdownLinkRouter {

    sealed class Action {
        data class InPageAnchor(val anchorId: String) : Action()
        data class External(val uri: String) : Action()
    }

    fun isInPageAnchor(destination: String): Boolean =
        destination.trimStart().startsWith("#")

    /**
     * Classify a link destination from [InlineMarkdownParser] annotations.
     * Empty or blank destinations are treated as external no-ops at the call site.
     */
    fun classify(destination: String): Action {
        val trimmed = destination.trim()
        if (trimmed.startsWith("#")) {
            return Action.InPageAnchor(decodeFragment(trimmed.removePrefix("#")))
        }
        return Action.External(trimmed)
    }

    /**
     * Decode a URL fragment (e.g. `%E7%AB%A0` → 章). Fail-soft on malformed input.
     */
    fun decodeFragment(raw: String): String {
        if (raw.isEmpty() || !raw.contains('%')) return raw
        return try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            raw
        }
    }
}
