package com.m1racle.reader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Product roles share the same three sizes as the Material UI. */
@Immutable
data class M1racleTypography(
    val display: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

val DefaultM1racleTypography = M1racleTypography(
    display = M1racleReaderTypography.titleLarge,
    title = M1racleReaderTypography.titleMedium,
    section = M1racleReaderTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    body = M1racleReaderTypography.bodyMedium,
    caption = M1racleReaderTypography.bodySmall,
)

val LocalM1racleTypography = staticCompositionLocalOf { DefaultM1racleTypography }
