package com.m1racle.reader.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.markdown.TocItem
import com.m1racle.reader.markdown.activeAt

@Composable
fun TocDrawerContent(
    documentName: String,
    toc: List<TocItem>,
    /**
     * When non-null with [listHeaderCount], active row is derived here so the
     * reader body never subscribes to scroll index for TOC highlighting.
     */
    listState: LazyListState? = null,
    listHeaderCount: Int = 0,
    /** Fallback when [listState] is null (tests / static preview). */
    activeAnchor: String? = null,
    onItemClick: (TocItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val derivedActiveAnchor by remember(toc, listHeaderCount, listState) {
        derivedStateOf {
            val state = listState
            if (state == null || toc.isEmpty()) {
                activeAnchor
            } else {
                val contentIndex =
                    (state.firstVisibleItemIndex - listHeaderCount).coerceAtLeast(0)
                toc.activeAt(contentIndex)?.anchor
            }
        }
    }
    val resolvedActive = if (listState != null) derivedActiveAnchor else activeAnchor

    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.toc_title),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Text(
            text = documentName,
            style = MaterialTheme.typography.bodySmall,
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
                    contentType = { "toc" },
                ) { item ->
                    TocRow(
                        item = item,
                        selected = item.anchor == resolvedActive,
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
        2 -> 22.dp
        else -> 38.dp
    }
    val weight = when (item.level) {
        1 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val fontSize = when (item.level) {
        1 -> ReaderTypography.tocH1Size
        2 -> ReaderTypography.tocH2Size
        else -> ReaderTypography.tocH3Size
    }
    val shape = RoundedCornerShape(8.dp) // Registered drawer-row radius.
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) {
        scheme.surfaceContainerHighest
    } else {
        Color.Transparent
    }
    val resolvedTitle = item.title.ifBlank { stringResource(R.string.toc_untitled) }

    Text(
        text = resolvedTitle,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = weight,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.3f).sp,
        ),
        color = scheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.5.dp)
            .clip(shape)
            .background(bg)
            .semantics { contentDescription = "目录链接：$resolvedTitle" }
            .clickable(onClick = onClick)
            .padding(
                start = startPadding,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
    )
}
