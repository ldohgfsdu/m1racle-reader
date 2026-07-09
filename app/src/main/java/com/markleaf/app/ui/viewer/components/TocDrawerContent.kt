package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markleaf.app.R
import com.markleaf.app.markdown.TocItem

@Composable
fun TocDrawerContent(
    documentName: String,
    toc: List<TocItem>,
    activeAnchor: String?,
    onItemClick: (TocItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.toc_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Text(
            text = documentName,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp),
        )

        if (toc.isEmpty()) {
            Text(
                text = stringResource(R.string.toc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 6.dp,
                    bottom = 16.dp,
                ),
            ) {
                items(
                    items = toc,
                    key = { "${it.anchor}-${it.blockIndex}" },
                ) { item ->
                    TocRow(
                        item = item,
                        selected = item.anchor == activeAnchor,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocRow(
    item: TocItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Indent: H2 ~15dp, H3 ~30dp from base.
    val startPadding = when (item.level) {
        1 -> 8.dp
        2 -> 22.dp // 8 + 14
        else -> 38.dp // 8 + 30
    }
    val weight = when (item.level) {
        1 -> FontWeight.SemiBold
        2 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val fontSize = when (item.level) {
        1 -> ReaderTypography.tocH1Size
        2 -> ReaderTypography.tocH2Size
        else -> ReaderTypography.tocH3Size
    }
    val shape = RoundedCornerShape(4.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = item.title.ifBlank { stringResource(R.string.toc_untitled) },
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = weight,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.3f).sp,
        ),
        color = textColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(
                start = startPadding,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
    )
}
