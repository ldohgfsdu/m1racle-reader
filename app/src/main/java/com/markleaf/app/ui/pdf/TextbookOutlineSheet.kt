package com.markleaf.app.ui.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.local.TextbookOutlineNodeEntity
import com.markleaf.app.data.local.TextbookOutlineNodeType
import com.markleaf.app.pdf.TextbookOutlineResolver
import com.markleaf.app.ui.theme.dialogContainer
import com.markleaf.app.ui.theme.selectedRow
import com.markleaf.app.ui.theme.sheetContainer
import androidx.compose.material3.BottomSheetDefaults

data class OutlineEditorState(
    val id: String?,
    val parentId: String?,
    val type: TextbookOutlineNodeType,
    val title: String,
    val startPageIndex: Int,
    val endPageIndex: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextbookOutlineSheet(
    nodes: List<TextbookOutlineNodeEntity>,
    currentPageIndex: Int,
    currentPathLabel: String,
    hasEmbeddedDrafts: Boolean,
    onJumpToNode: (TextbookOutlineNodeEntity) -> Unit,
    onRequestEdit: (OutlineEditorState) -> Unit,
    onDelete: (String) -> Unit,
    onExportNode: (TextbookOutlineNodeEntity) -> Unit,
    onImportEmbedded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val flatAll = remember(nodes) { TextbookOutlineResolver.flattenTree(nodes) }
    val childrenMap = remember(nodes) { nodes.groupBy { it.parentId } }
    val currentId = remember(nodes, currentPageIndex) {
        TextbookOutlineResolver.deepestContaining(currentPageIndex, nodes)?.id
    }
    // Collapse state: ids that are expanded (default: expand path to current + roots)
    var expanded by remember(nodes, currentId) {
        val initial = LinkedHashSet<String>()
        val path = TextbookOutlineResolver.pathToRoot(
            nodes.firstOrNull { it.id == currentId },
            nodes,
        )
        path.forEach { initial.add(it.id) }
        nodes.filter { it.parentId == null }.forEach { initial.add(it.id) }
        mutableStateOf<Set<String>>(initial)
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val visibleRows = remember(flatAll, expanded, childrenMap) {
        flatAll.filter { row ->
            // Visible if every ancestor is expanded
            var parentId = row.node.parentId
            while (parentId != null) {
                if (parentId !in expanded) return@filter false
                parentId = nodes.firstOrNull { it.id == parentId }?.parentId
            }
            true
        }
    }

    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.sheetContainer,
        contentColor = scheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = scheme.onSurfaceVariant)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 580.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pdf_outline_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.pdf_outline_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FloatingActionButton(
                    onClick = {
                        onRequestEdit(
                            OutlineEditorState(
                                id = null,
                                parentId = currentId,
                                type = TextbookOutlineNodeType.SECTION,
                                title = "",
                                startPageIndex = currentPageIndex,
                                endPageIndex = currentPageIndex,
                            ),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.pdf_outline_add),
                    )
                }
            }

            if (hasEmbeddedDrafts) {
                TextButton(
                    onClick = onImportEmbedded,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.pdf_embedded_from_sheet))
                }
            }

            if (currentPathLabel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.pdf_outline_here, currentPathLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = stringResource(R.string.pdf_outline_here_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (visibleRows.isEmpty()) {
                Text(
                    text = stringResource(R.string.pdf_outline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    items(visibleRows, key = { it.node.id }) { row ->
                        val hasChildren = childrenMap[row.node.id].orEmpty().isNotEmpty()
                        val isExpanded = row.node.id in expanded
                        OutlineRow(
                            row = row,
                            selected = row.node.id == currentId,
                            hasChildren = hasChildren,
                            expanded = isExpanded,
                            onToggle = {
                                expanded = if (isExpanded) {
                                    expanded - row.node.id
                                } else {
                                    expanded + row.node.id
                                }
                            },
                            onClick = { onJumpToNode(row.node) },
                            onEdit = {
                                onRequestEdit(
                                    OutlineEditorState(
                                        id = row.node.id,
                                        parentId = row.node.parentId,
                                        type = row.node.nodeType,
                                        title = row.node.title,
                                        startPageIndex = row.node.startPageIndex,
                                        endPageIndex = row.node.endPageIndex,
                                    ),
                                )
                            },
                            onExport = { onExportNode(row.node) },
                            onDelete = { pendingDeleteId = row.node.id },
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    onRequestEdit(
                        OutlineEditorState(
                            id = null,
                            parentId = null,
                            type = TextbookOutlineNodeType.CHAPTER,
                            title = "",
                            startPageIndex = currentPageIndex,
                            endPageIndex = currentPageIndex,
                        ),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.pdf_outline_add_from_page))
            }
        }
    }

    pendingDeleteId?.let { id ->
        val dialogScheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor = dialogScheme.dialogContainer,
            titleContentColor = dialogScheme.onSurface,
            textContentColor = dialogScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.pdf_outline_delete_title)) },
            text = { Text(stringResource(R.string.pdf_outline_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(id)
                        pendingDeleteId = null
                    },
                ) {
                    Text(stringResource(R.string.pdf_outline_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.pdf_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun OutlineRow(
    row: TextbookOutlineResolver.FlatRow,
    selected: Boolean,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val indent = (row.depth * 12).dp
    val scheme = MaterialTheme.colorScheme
    // Selected: soft lift only — not a high-contrast block.
    val bg = if (selected) scheme.selectedRow else Color.Transparent
    val titleColor = scheme.onSurface
    val metaColor = scheme.onSurfaceVariant
    val expandTint = scheme.onSurfaceVariant
    val actionTint = scheme.onSurfaceVariant
    Surface(
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasChildren) {
                IconButton(onClick = onToggle, modifier = Modifier.padding(0.dp)) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (expanded) {
                            stringResource(R.string.pdf_outline_collapse)
                        } else {
                            stringResource(R.string.pdf_outline_expand)
                        },
                        tint = expandTint,
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${row.node.nodeType.displayLabelZh()} · ${row.node.title}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.pdf_outline_page_range,
                        row.node.startPageIndex + 1,
                        row.node.endPageIndex + 1,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor,
                )
            }
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = stringResource(R.string.pdf_export_node),
                    tint = actionTint,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.pdf_outline_edit),
                    tint = actionTint,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.pdf_outline_delete),
                    tint = actionTint,
                )
            }
        }
    }
}

