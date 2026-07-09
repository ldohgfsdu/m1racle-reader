package com.markleaf.app.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Build plain-text AnnotatedString with soft highlight on [activeStart, activeEnd).
 * Used for Heading / CodeBlock when search is active (P1 fragment highlight).
 */
fun highlightPlainText(
    text: String,
    activeStart: Int?,
    activeEnd: Int?,
    highlightColor: Color,
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    if (activeStart == null || activeEnd == null ||
        activeStart < 0 || activeEnd > text.length || activeStart >= activeEnd
    ) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        append(text.substring(0, activeStart))
        withStyle(SpanStyle(background = highlightColor)) {
            append(text.substring(activeStart, activeEnd))
        }
        append(text.substring(activeEnd))
    }
}
