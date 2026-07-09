package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Soft block-level highlight for the current search match (P0). */
@Composable
fun Modifier.searchBlockHighlight(active: Boolean): Modifier {
    if (!active) return this
    val color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    return this
        .clip(RoundedCornerShape(6.dp))
        .background(color)
        .padding(horizontal = 4.dp, vertical = 2.dp)
}

@Composable
fun searchFragmentHighlightColor(): Color =
    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
