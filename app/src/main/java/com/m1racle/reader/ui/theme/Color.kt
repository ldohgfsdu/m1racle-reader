package com.m1racle.reader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * M1racleReader fixed brand palettes — neutral only (no Material You purple).
 *
 * Light: paper white / charcoal ink.
 * Dark: graphite black reading surfaces.
 */

// ---------------------------------------------------------------------------
// Light — paper
// ---------------------------------------------------------------------------

val PaperBackground = Color(0xFFFFFFFF)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceContainerLowest = Color(0xFFFFFFFF)
val PaperSurfaceContainerLow = Color(0xFFF7F7F8)
val PaperSurfaceContainer = Color(0xFFF5F5F5)
val PaperSurfaceContainerHigh = Color(0xFFF0F0F0)
val PaperSurfaceContainerHighest = Color(0xFFECECEC)
val PaperSurfaceVariant = Color(0xFFF0F0F0)

val PaperOnBackground = Color(0xFF0D0D0D)
val PaperOnSurface = Color(0xFF0D0D0D)
val PaperOnSurfaceVariant = Color(0xFF5D5D5D)

val PaperPrimary = Color(0xFF171717)
/** Navigation accent stays local to the iOS-inspired glass dock. */
val DockSelectedLight = Color(0xFF007AFF)
val DockSelectedDark = Color(0xFF0A9FFF)
val PaperOnPrimary = Color(0xFFFFFFFF)
val PaperPrimaryContainer = Color(0xFFF2F2F2)
val PaperOnPrimaryContainer = Color(0xFF0D0D0D)

val PaperSecondary = Color(0xFF5D5D5D)
val PaperOnSecondary = Color(0xFFFFFFFF)
val PaperSecondaryContainer = Color(0xFFF2F2F2)
val PaperOnSecondaryContainer = Color(0xFF0D0D0D)

val PaperTertiary = Color(0xFF2F2F2F)
val PaperOnTertiary = Color(0xFFFFFFFF)
val PaperTertiaryContainer = Color(0xFFFFF1B8)
val PaperOnTertiaryContainer = Color(0xFF3F2E00)

val PaperOutline = Color(0xFFE5E5E5)
val PaperOutlineVariant = Color(0xFFEFEFEF)

val PaperError = Color(0xFFB3261E)
val PaperOnError = Color(0xFFFFFFFF)
val PaperErrorContainer = Color(0xFFF9DEDC)
val PaperOnErrorContainer = Color(0xFF410E0B)

val PaperScrim = Color(0xFF000000)

/** Soft surround behind PDF pages in light mode (page content stays white). */
val PaperPdfCanvas = Color(0xFFE8E8E8)

// ---------------------------------------------------------------------------
// Dark — graphite black (fixed visual polish palette)
// ---------------------------------------------------------------------------

val InkBackground = Color(0xFF0F0F0F)
val InkSurface = Color(0xFF171717)
val InkSurfaceContainerLowest = Color(0xFF0F0F0F)
val InkSurfaceContainerLow = Color(0xFF1B1B1B)
val InkSurfaceContainer = Color(0xFF1C1C1C)
val InkSurfaceContainerHigh = Color(0xFF202020)
val InkSurfaceContainerHighest = Color(0xFF292929)
val InkSurfaceVariant = Color(0xFF292929)

val InkOnBackground = Color(0xFFF2F2F2)
val InkOnSurface = Color(0xFFF2F2F2)
val InkOnSurfaceVariant = Color(0xFFB3B3B3)

val InkPrimary = Color(0xFFE8E8E8)
val InkOnPrimary = Color(0xFF111111)
val InkPrimaryContainer = Color(0xFF292929)
val InkOnPrimaryContainer = Color(0xFFF2F2F2)

val InkSecondary = Color(0xFFB3B3B3)
val InkOnSecondary = Color(0xFF111111)
val InkSecondaryContainer = Color(0xFF292929)
val InkOnSecondaryContainer = Color(0xFFF2F2F2)

val InkTertiary = Color(0xFFC4C4C4)
val InkOnTertiary = Color(0xFF111111)
val InkTertiaryContainer = Color(0xFF3A3520)
val InkOnTertiaryContainer = Color(0xFFFFF1B8)

val InkOutline = Color(0xFF343434)
val InkOutlineVariant = Color(0xFF292929)

val InkError = Color(0xFFFFB4AB)
val InkOnError = Color(0xFF690005)
val InkErrorContainer = Color(0xFF93000A)
val InkOnErrorContainer = Color(0xFFFFDAD6)

val InkScrim = Color(0xFF000000)

/** Fixed surround behind PDF pages in dark mode (page content not inverted). */
val InkPdfCanvas = Color(0xFF252525)
