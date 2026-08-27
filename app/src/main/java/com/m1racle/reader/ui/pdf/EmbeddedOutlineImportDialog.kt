package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m1racle.reader.R
import com.m1racle.reader.pdf.EmbeddedOutlineNodeDraft
import com.m1racle.reader.pdf.OutlineImportMode
import com.m1racle.reader.ui.material.GlassAlertDialog

@Composable
fun EmbeddedOutlineImportDialog(
    drafts: List<EmbeddedOutlineNodeDraft>,
    hasLocalNodes: Boolean,
    alreadyImported: Boolean,
    onImport: (OutlineImportMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = remember(drafts) { drafts.take(40) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.pdf_embedded_title))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pdf_embedded_found, drafts.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (alreadyImported) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pdf_embedded_already),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pdf_embedded_preview),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                ) {
                    items(preview, key = { it.importKey }) { d ->
                        val indent = "　".repeat(d.depth.coerceAtMost(6))
                        Text(
                            text = "$indent${d.title}  ·  p.${d.pageIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (drafts.size > preview.size) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.pdf_embedded_preview_more,
                                    drafts.size - preview.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        // M3 AlertDialog slots must each emit a single root composable.
        confirmButton = {
            if (hasLocalNodes) {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onImport(OutlineImportMode.REPLACE) }) {
                        Text(stringResource(R.string.pdf_embedded_replace))
                    }
                    TextButton(onClick = { onImport(OutlineImportMode.MERGE) }) {
                        Text(stringResource(R.string.pdf_embedded_merge))
                    }
                }
            } else {
                TextButton(onClick = { onImport(OutlineImportMode.REPLACE) }) {
                    Text(stringResource(R.string.pdf_embedded_import))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pdf_embedded_cancel))
            }
        },
    )
}
