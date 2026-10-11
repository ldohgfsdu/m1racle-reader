package com.m1racle.reader.ui.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.ui.material.GlassAlertDialog
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.theme.M1racleReaderTheme

/**
 * Light reading-entry row: icon, filename, metadata.
 * Not a card. GlassPolicy forbids glass on list / file items.
 * Long-press and the action button open the same solid removal menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListRow(
    document: DocumentEntity,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by rememberSaveable(document.id) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val titles = remember(
        document.id, document.title, document.displayName, document.uri, document.isPdf,
        document.isMarkdownPackage,
    ) { resolveDocumentCardTitles(document) }
    val metadata = remember(
        document.id, document.readingProgress, document.isPdf,
        document.pageCount, document.lastScrollIndex,
    ) { formatListMetadata(document) }
    val spokenText = remember(titles.displayTitle, titles.typeLabel, metadata) {
        listOf(titles.displayTitle, titles.typeLabel, metadata).joinToString(", ")
    }
    val icon = if (document.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description
    val rowModifier = if (onDelete != null) {
        Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = { menuExpanded = true },
        )
    } else {
        Modifier.clickable(onClick = onOpen)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowModifier)
                .semantics { contentDescription = spokenText }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(M1racleReaderTheme.cornerRadius.control))
                    .background(scheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                com.m1racle.reader.image.DocumentCover(document, Modifier.fillMaxSize()) { Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                ) }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titles.displayTitle,
                    style = M1racleReaderTheme.typography.title.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${titles.typeLabel} · $metadata",
                    style = M1racleReaderTheme.typography.caption,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDelete != null) {
                Box {
                    DocumentActionsButton(onClick = { menuExpanded = true })
                    DocumentActionsMenu(document, menuExpanded, { menuExpanded = false }, onDelete)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp),
            color = scheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

/** Reading position only. Type stays on the row icon, not in the copy. */
internal fun formatListMetadata(document: DocumentEntity): String =
    readingPositionLine(document)

/** The platform menu positions itself below/above its actual action-button anchor. */
@Composable
internal fun DocumentActionsMenu(
    document: DocumentEntity,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmRemoval by rememberSaveable(document.id) { mutableStateOf(false) }
    var showInformation by rememberSaveable(document.id) { mutableStateOf(false) }
    var renaming by rememberSaveable(document.id) { mutableStateOf(false) }
    var renameText by rememberSaveable(document.id) { mutableStateOf(document.displayName) }
    val operations = LocalDocumentOperations.current
    val editor = com.m1racle.reader.ui.documents.LocalDocumentEditor.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(document.mimeType ?: "application/octet-stream"),
    ) { uri -> if (uri != null) operations?.export(document.id, uri) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fullName = document.displayName.ifBlank { resolveDocumentCardTitles(document).displayTitle }
    if (showInformation) {
        GlassAlertDialog(
            onDismissRequest = { showInformation = false },
            title = { Text(stringResource(R.string.home_document_information)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(fullName) }
                    Spacer(Modifier.height(12.dp))
                    Text(listOfNotNull(
                        resolveDocumentCardTitles(document).typeLabel,
                        document.fileSize?.let { android.text.format.Formatter.formatShortFileSize(context, it) },
                    ).joinToString(" · "))
                    Spacer(Modifier.height(12.dp))
                    Text("本地副本", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(documentLocation(document.uri)) }
                    document.sourceUri?.takeIf { it != document.uri }?.let { source ->
                        Spacer(Modifier.height(12.dp))
                        Text("原文件位置", style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(documentLocation(source)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInformation = false }) { Text(stringResource(R.string.home_close_information)) } },
        )
    }
    if (renaming) GlassAlertDialog(
        onDismissRequest = { renaming = false }, title = { Text("重命名") },
        text = { Column {
            OutlinedTextField(renameText, { renameText = it }, label = { Text("文件名") }, singleLine = true)
            Text("修改书库名称，保留阅读记录与批注；原文件名称不变。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { operations?.rename(document.id, renameText) { renaming = false } }, enabled = renameText.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
    )
    if (confirmRemoval) {
        GlassAlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text(stringResource(R.string.home_remove_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(fullName) }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_remove_warning))
                }
            },
            confirmButton = { TextButton(onClick = { confirmRemoval = false; onDelete() }) {
                Text(stringResource(R.string.home_remove_confirm))
            } },
            dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val overlay = GlassMaterialEngine.resolve(
        role = GlassMaterialRole.Dock,
        environment = LocalGlassEnvironment.current,
        capability = GlassCapability.SOLID_FALLBACK,
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(0.dp, 4.dp),
        shape = HomeDesignTokens.MenuShape,
        containerColor = overlay.surfaceColor,
        tonalElevation = 0.dp,
        shadowElevation = overlay.shadow,
    ) {
        if (editor != null && document.type == com.m1racle.reader.data.local.DocumentType.MARKDOWN) {
            DropdownMenuItem(text = { Text("编辑正文") }, onClick = { onDismissRequest(); editor(document.id) })
        }
        if (operations != null) {
            DropdownMenuItem(text = { Text("重命名") }, onClick = { onDismissRequest(); renaming = true })
            DropdownMenuItem(text = { Text("导出文件") }, onClick = { onDismissRequest(); exportLauncher.launch(document.displayName) })
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_document_information)) },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = { onDismissRequest(); showInformation = true },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_remove_title)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = { onDismissRequest(); confirmRemoval = true },
        )
    }
}

@Composable
internal fun DocumentActionsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.home_document_actions),
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
