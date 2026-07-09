package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.markdown.MarkdownBlock

@Composable
fun HeadingBlockView(
    block: MarkdownBlock.Heading,
    modifier: Modifier = Modifier,
) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        )
        2 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 30.sp,
        )
        3 -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
        4 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
        else -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }

    val top = when (block.level) {
        1 -> 28.dp
        2 -> 22.dp
        3 -> 18.dp
        else -> 12.dp
    }
    val bottom = when (block.level) {
        1 -> 12.dp
        2 -> 10.dp
        else -> 8.dp
    }

    Text(
        text = block.text.ifBlank { " " },
        style = style,
        color = if (block.level >= 5) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(top = top, bottom = bottom),
    )
}
