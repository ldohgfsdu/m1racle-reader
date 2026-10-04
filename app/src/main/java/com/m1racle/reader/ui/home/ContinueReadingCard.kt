package com.m1racle.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/**
 * Unique Continue Reading hero. L2 surface only — GlassPolicy forbids glass on Hero.
 * Progress appears once as [positionLine]; actions share the same removal confirmation as list rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueReadingCard(
    document: DocumentEntity,
    positionLine: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable(document.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val titles = remember(
        document.id, document.title, document.displayName, document.uri,
        document.isPdf, document.isMarkdownPackage,
    ) { resolveDocumentCardTitles(document) }
    val heroShape = RoundedCornerShape(M1racleReaderTheme.cornerRadius.card)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(M1racleReaderTheme.surface.hero)
                .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
                .padding(
                    horizontal = HomeDesignTokens.HeroPaddingH,
                    vertical = HomeDesignTokens.HeroPaddingV,
                ),
        ) {
            Text(
                text = stringResource(R.string.hub_continue_reading),
                style = M1racleReaderTheme.typography.caption,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = titles.displayTitle,
                style = M1racleReaderTheme.typography.title.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = positionLine,
                style = M1racleReaderTheme.typography.caption,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
            DocumentActionsButton(onClick = { menuExpanded = true })
            DocumentActionsMenu(document, menuExpanded, { menuExpanded = false }, onDelete)
        }
    }
}
