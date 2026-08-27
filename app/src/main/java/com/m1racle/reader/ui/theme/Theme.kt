package com.m1racle.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Fixed brand schemes — **dynamic color is intentionally off**.
 * Light: paper neutrals. Dark: graphite black reading UI.
 * Wallpaper / Material You must not tint the chrome.
 */
private val LightColorScheme = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = PaperOnPrimary,
    primaryContainer = PaperPrimaryContainer,
    onPrimaryContainer = PaperOnPrimaryContainer,
    secondary = PaperSecondary,
    onSecondary = PaperOnSecondary,
    secondaryContainer = PaperSecondaryContainer,
    onSecondaryContainer = PaperOnSecondaryContainer,
    tertiary = PaperTertiary,
    onTertiary = PaperOnTertiary,
    tertiaryContainer = PaperTertiaryContainer,
    onTertiaryContainer = PaperOnTertiaryContainer,
    background = PaperBackground,
    onBackground = PaperOnBackground,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    surfaceContainerLowest = PaperSurfaceContainerLowest,
    surfaceContainerLow = PaperSurfaceContainerLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    surfaceContainerHighest = PaperSurfaceContainerHighest,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = PaperError,
    onError = PaperOnError,
    errorContainer = PaperErrorContainer,
    onErrorContainer = PaperOnErrorContainer,
    scrim = PaperScrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = InkPrimary,
    onPrimary = InkOnPrimary,
    primaryContainer = InkPrimaryContainer,
    onPrimaryContainer = InkOnPrimaryContainer,
    secondary = InkSecondary,
    onSecondary = InkOnSecondary,
    secondaryContainer = InkSecondaryContainer,
    onSecondaryContainer = InkOnSecondaryContainer,
    tertiary = InkTertiary,
    onTertiary = InkOnTertiary,
    tertiaryContainer = InkTertiaryContainer,
    onTertiaryContainer = InkOnTertiaryContainer,
    background = InkBackground,
    onBackground = InkOnBackground,
    surface = InkSurface,
    onSurface = InkOnSurface,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainerLowest = InkSurfaceContainerLowest,
    surfaceContainerLow = InkSurfaceContainerLow,
    surfaceContainer = InkSurfaceContainer,
    surfaceContainerHigh = InkSurfaceContainerHigh,
    surfaceContainerHighest = InkSurfaceContainerHighest,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    error = InkError,
    onError = InkOnError,
    errorContainer = InkErrorContainer,
    onErrorContainer = InkOnErrorContainer,
    scrim = InkScrim,
)

private val LightExtra = M1racleReaderExtraColors(pdfCanvas = PaperPdfCanvas)
private val DarkExtra = M1racleReaderExtraColors(pdfCanvas = InkPdfCanvas)

@Composable
fun M1racleReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Reserved for a future settings toggle ("跟随系统取色").
     * Always false for now — fixed graphite / paper palettes only.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // dynamicColor deliberately ignored: no Material You / wallpaper tints.
    @Suppress("UNUSED_VARIABLE")
    val ignoreDynamic = dynamicColor

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extra = if (darkTheme) DarkExtra else LightExtra

    CompositionLocalProvider(LocalM1racleReaderExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = M1racleReaderTypography,
            content = content,
        )
    }
}
