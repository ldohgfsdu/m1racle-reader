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
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.toc_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = documentName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )

        if (toc.isEmpty()) {
            Text(
                text = stringResource(R.string.toc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 24.dp,
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
    val startPadding = when (item.level) {
        1 -> 8.dp
        2 -> 24.dp // 8 + 16
        else -> 40.dp // 8 + 32 for H3
    }
    val weight = when (item.level) {
        1 -> FontWeight.SemiBold
        2 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val shape = RoundedCornerShape(8.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
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
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight),
        color = textColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(
                start = startPadding,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    )
}
