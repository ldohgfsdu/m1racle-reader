package com.markleaf.app.ui.viewer.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * P1: character-level search highlight colors.
 * Normal matches are restrained; active match is clearly stronger but not harsh.
 */
@Composable
fun searchMatchNormalBackground(): Color {
    return MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
}

@Composable
fun searchMatchActiveBackground(): Color {
    return MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f)
}

/** @deprecated P0 whole-block yellow — kept as no-op so call sites can be deleted safely. */
@Composable
fun Modifier.searchBlockHighlight(active: Boolean): Modifier {
    // P1: do not paint entire block background for search hits.
    return this
}

/** @deprecated Use [searchMatchActiveBackground]. */
@Composable
fun searchFragmentHighlightColor(): Color = searchMatchActiveBackground()
