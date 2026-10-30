package com.m1racle.reader.ui.theme

import com.m1racle.reader.design.RadixColors

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Extra semantic surfaces not always convenient from [ColorScheme] alone,
 * plus documented aliases for M1racleReader chrome.
 *
 * Prefer [MaterialTheme.colorScheme] surface roles for ordinary UI.
 * Prefer [M1racleReaderTheme.extra] for product-specific tokens (e.g. PDF canvas).
 */
@Immutable
data class M1racleReaderExtraColors(
    /** Area around PDF page bitmaps — not the page paper itself. */
    val pdfCanvas: Color,
    val link: Color = RadixColors.Blue11,
    val warning: Color = RadixColors.Amber11,
    val success: Color = RadixColors.Green11,
    val highlight: Color = RadixColors.Amber4,
    val highlightText: Color = RadixColors.Amber12,
)

val LocalM1racleReaderExtraColors = staticCompositionLocalOf {
    M1racleReaderExtraColors(pdfCanvas = PaperPdfCanvas)
}

internal const val ReaderUiHairlinePx = 0.5f

object M1racleReaderTheme {
    /** Physical pixels, independent of display density and user font scale. */
    val hairline: Dp
        @Composable
        @ReadOnlyComposable
        get() = with(LocalDensity.current) { ReaderUiHairlinePx.toDp() }

    val extra: M1racleReaderExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalM1racleReaderExtraColors.current

    val typography: M1racleTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalM1racleTypography.current

    val surface: M1racleSurface
        @Composable
        @ReadOnlyComposable
        get() = LocalM1racleSurface.current

    val cornerRadius: M1racleCornerRadius
        get() = M1racleCornerRadius

    val elevation: M1racleElevation
        get() = M1racleElevation

    val glass: M1racleGlassPolicy
        get() = M1racleGlassPolicy

    val motion: M1racleMotion
        get() = M1racleMotion
}

/**
 * Semantic aliases — business UI should call these (or ColorScheme roles)
 * instead of hard-coding grays.
 */
val ColorScheme.sheetContainer: Color
    get() = surfaceContainer

/** Shared OutlinedTextField colors — no dynamic purple, no fixed white. */
@Composable
fun m1racleReaderOutlinedFieldColors(): TextFieldColors {
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
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.primary,
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
