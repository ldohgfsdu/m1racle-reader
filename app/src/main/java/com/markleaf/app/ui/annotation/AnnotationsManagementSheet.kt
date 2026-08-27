package com.markleaf.app.ui.annotation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.ui.hub.BookmarkRowFormatter

/**
 * R28 标注管理面板：高亮 / 批注双分区，共用一个虚拟化滚动容器。
 * - 高亮行：页码/摘录/时间 + [添加批注]（进入 Note 编辑器，锚点继承该高亮）；
 * - 批注区：内容/页码/时间 + 编辑 / 删除；
 * - 高亮行点击仍为跳转；批注行点击为编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsManagementSheet(
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    onDismiss: () -> Unit,
    onJump: (HighlightEntity) -> Unit,
    onDeleteHighlight: (HighlightEntity) -> Unit,
    onAddNoteForHighlight: (HighlightEntity) -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_annotations_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                item(key = "highlight-section") {
                    SectionLabel(stringResource(R.string.reader_highlights_section))
                }
                if (highlights.isEmpty()) {
                    item(key = "highlight-empty") {
                        EmptyLine(stringResource(R.string.reader_annotations_empty))
                    }
                } else {
                    items(highlights.size, key = { "highlight-${highlights[it].id}" }) { index ->
                        val row = highlights[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(row) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                PageLine(row.location)
                                row.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = excerpt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                TimeLine(row.createdAt)
                            }
                            IconButton(onClick = { onAddNoteForHighlight(row) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Chat,
                                    contentDescription = stringResource(R.string.reader_note_add),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { onDeleteHighlight(row) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete_record),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item(key = "section-divider") {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item(key = "note-section") {
                    SectionLabel(stringResource(R.string.reader_notes_section))
                }
                if (notes.isEmpty()) {
                    item(key = "note-empty") {
                        EmptyLine(stringResource(R.string.reader_notes_empty))
                    }
                } else {
                    items(notes.size, key = { "note-${notes[it].id}" }) { index ->
                        val note = notes[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditNote(note) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                PageLine(note.location)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                TimeLine(note.updatedAt)
                            }
                            IconButton(onClick = { onEditNote(note) }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.reader_note_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteNote(note) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete_record),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun PageLine(location: Int) {
    Text(
        text = stringResource(R.string.reader_annotation_page_line, location + 1),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TimeLine(createdAtMs: Long) {
    Text(
        text = BookmarkRowFormatter.createdAtLine(
            createdAtMs = createdAtMs,
            nowMs = System.currentTimeMillis(),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}
