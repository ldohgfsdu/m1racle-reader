package com.markleaf.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extra semantic surfaces not always convenient from [ColorScheme] alone,
 * plus documented aliases for MarkLeaf chrome.
 *
 * Prefer [MaterialTheme.colorScheme] surface roles for ordinary UI.
 * Prefer [MarkLeafTheme.extra] for product-specific tokens (e.g. PDF canvas).
 */
@Immutable
data class MarkLeafExtraColors(
    /** Area around PDF page bitmaps — not the page paper itself. */
    val pdfCanvas: Color,
)

val LocalMarkLeafExtraColors = staticCompositionLocalOf {
    MarkLeafExtraColors(pdfCanvas = PaperPdfCanvas)
}

object MarkLeafTheme {
    val extra: MarkLeafExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMarkLeafExtraColors.current
}

/**
 * Semantic aliases — business UI should call these (or ColorScheme roles)
 * instead of hard-coding grays.
 */
val ColorScheme.cardContainer: Color
    get() = surfaceContainerLow

val ColorScheme.sheetContainer: Color
    get() = surfaceContainer

val ColorScheme.dialogContainer: Color
    get() = surfaceContainerHigh

val ColorScheme.selectedRow: Color
    get() = surfaceContainerHighest

val ColorScheme.inlineCodeBackground: Color
    get() = surfaceContainerHighest

val ColorScheme.imagePlaceholder: Color
    get() = surfaceContainerHigh

val ColorScheme.codeBlockHeader: Color
    get() = surfaceContainerHigh

val ColorScheme.codeBlockBody: Color
    get() = surfaceContainerLow

/** Shared OutlinedTextField colors — no dynamic purple, no fixed white. */
@Composable
fun markLeafOutlinedFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurfaceVariant,
        errorTextColor = scheme.error,
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        errorContainerColor = scheme.surface,
        cursorColor = scheme.primary,
        errorCursorColor = scheme.error,
        focusedBorderColor = scheme.outline,
        unfocusedBorderColor = scheme.outline,
        disabledBorderColor = scheme.outlineVariant,
        errorBorderColor = scheme.error,
        focusedLabelColor = scheme.onSurfaceVariant,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        disabledLabelColor = scheme.onSurfaceVariant,
        errorLabelColor = scheme.error,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
    )
}
