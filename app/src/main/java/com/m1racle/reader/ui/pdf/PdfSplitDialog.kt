package com.m1racle.reader.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.m1racle.reader.pdf.export.PdfSplitChapterLevel
import com.m1racle.reader.pdf.export.PdfSplitChapterSource
import com.m1racle.reader.pdf.export.PdfSplitMode
import com.m1racle.reader.pdf.export.PdfSplitPageRange
import com.m1racle.reader.pdf.export.PdfSplitPlan
import com.m1racle.reader.pdf.export.PdfSplitPlanner
import com.m1racle.reader.pdf.export.PdfSplitResidue
import com.m1racle.reader.pdf.export.PdfSplitSamePagePolicy
import com.m1racle.reader.pdf.export.PdfSplitState
import com.m1racle.reader.ui.material.GlassAlertDialog

/**
 * Copy for strings.xml when the dialog is localized:
 * pdf_split_title=拆分 PDF
 * pdf_split_by_chapter=按目录章节
 * pdf_split_by_page_count=按固定页数
 * pdf_split_depth=目录层级
 * pdf_split_pages_per_part=每份页数
 * pdf_split_range=只拆分页码范围
 * pdf_split_start=起始页
 * pdf_split_end=结束页
 * pdf_split_choose_directory=选择输出目录
 * pdf_split_level_rule=按此层级划分全部页面。
 * pdf_split_boundary_rule=保留封面与前置内容，同页章节合并。
 * pdf_split_short_tail=末份不足所选页数时仍保留。
 */
@Composable
fun PdfSplitDialog(
    documentDisplayName: String,
    pageCount: Int,
    chapters: List<PdfSplitChapterSource>,
    onConfirm: (PdfSplitPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            if (chapters.isEmpty()) PdfSplitMode.BY_PAGE_COUNT else PdfSplitMode.BY_CHAPTER,
        )
    }
    var chapterDepth0 by remember { mutableIntStateOf(0) }
    var pagesPerPartText by remember { mutableStateOf("20") }
    var depthMenuOpen by remember { mutableStateOf(false) }
    var useRange by remember { mutableStateOf(false) }
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(pageCount.coerceAtLeast(1).toString()) }

    val maxDepth0 = chapters.maxOfOrNull { it.pathTitles.size - 1 }?.coerceAtLeast(0) ?: 0
    val previewResult = runCatching {
        buildSplitPlan(
            documentDisplayName = documentDisplayName,
            pageCount = pageCount,
            chapters = chapters,
            mode = mode,
            chapterDepth0 = chapterDepth0,
            pagesPerPartText = pagesPerPartText,
            useRange = useRange,
            startText = startText,
            endText = endText,
        )
    }
    val preview = previewResult.getOrNull()

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拆分 PDF") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("拆分方式", style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    if (chapters.isNotEmpty()) {
                        SplitChoice(mode == PdfSplitMode.BY_CHAPTER, "按目录章节") {
                            mode = PdfSplitMode.BY_CHAPTER
                        }
                    }
                    SplitChoice(mode == PdfSplitMode.BY_PAGE_COUNT, "按固定页数") {
                        mode = PdfSplitMode.BY_PAGE_COUNT
                    }
                }

                if (mode == PdfSplitMode.BY_CHAPTER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("目录层级", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { depthMenuOpen = true }) {
                            Text("第 ${chapterDepth0 + 1} 级")
                        }
                        DropdownMenu(
                            expanded = depthMenuOpen,
                            onDismissRequest = { depthMenuOpen = false },
                        ) {
                            for (depth0 in 0..maxDepth0) {
                                DropdownMenuItem(
                                    text = { Text("第 ${depth0 + 1} 级") },
                                    onClick = {
                                        chapterDepth0 = depth0
                                        depthMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "按此层级划分全部页面，保留封面和前置内容，同页章节合并。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SplitNumberField("每份页数", pagesPerPartText) {
                        pagesPerPartText = it.filter(Char::isDigit).take(5)
                    }
                    Text(
                        "末份不足所选页数时仍保留。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                SplitChoice(useRange, "只拆分页码范围") { useRange = !useRange }
                if (useRange) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SplitNumberField("起始页", startText, Modifier.weight(1f)) {
                            startText = it.filter(Char::isDigit).take(6)
                        }
                        SplitNumberField("结束页", endText, Modifier.weight(1f)) {
                            endText = it.filter(Char::isDigit).take(6)
                        }
                    }
                }

                if (preview != null) {
                    Text(
                        "将生成 ${preview.parts.size} 份 PDF，覆盖 ${preview.totalPages} 页。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        previewResult.exceptionOrNull()?.message ?: "无法生成拆分计划",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = preview != null,
                onClick = { preview?.let(onConfirm) },
            ) {
                Text("选择输出目录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun PdfSplitProgressDialog(
    state: PdfSplitState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isRunning = state is PdfSplitState.Preparing ||
        state is PdfSplitState.CopyingPart ||
        state is PdfSplitState.VerifyingPart
    GlassAlertDialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        title = {
            Text(
                when (state) {
                    is PdfSplitState.Completed -> "拆分完成"
                    is PdfSplitState.Failed -> "拆分失败"
                    is PdfSplitState.Cancelled -> "已取消拆分"
                    else -> "正在拆分"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state) {
                    is PdfSplitState.Preparing -> {
                        CircularProgressIndicator()
                        Text("准备 ${state.partCount} 份 PDF，共 ${state.totalPages} 页。")
                    }
                    is PdfSplitState.CopyingPart -> {
                        ProgressBar(state.copiedPages, state.totalPages)
                        Text(
                            "正在生成第 ${state.partIndex} / ${state.partCount} 份，" +
                                "已复制 ${state.copiedPages} / ${state.totalPages} 页。",
                        )
                        Text(state.outputName, style = MaterialTheme.typography.bodySmall)
                    }
                    is PdfSplitState.VerifyingPart -> {
                        ProgressBar(state.verifiedPages, state.totalPages)
                        Text(
                            "正在回读第 ${state.partIndex} / ${state.partCount} 份，" +
                                "已校验 ${state.verifiedPages} / ${state.totalPages} 页。",
                        )
                        Text(state.outputName, style = MaterialTheme.typography.bodySmall)
                    }
                    is PdfSplitState.Completed -> {
                        Text("已生成 ${state.outputs.size} 份 PDF，并完成文件回读。")
                    }
                    is PdfSplitState.Cancelled -> {
                        Text(
                            if (state.cleanedOutputs) {
                                "本次生成的输出和临时文件已删除。"
                            } else {
                                "已取消，但部分输出无法由文件提供方删除。"
                            },
                            color = if (state.cleanedOutputs) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        if (!state.cleanedOutputs) {
                            ResidueNames(state.retainedOutputs)
                        }
                    }
                    is PdfSplitState.Failed -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        if (state.outputCleanupFailed) {
                            Text(
                                "部分不完整文件无法由文件提供方删除。",
                                color = MaterialTheme.colorScheme.error,
                            )
                            ResidueNames(state.retainedOutputs)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRunning) {
                TextButton(onClick = onDismiss) { Text("完成") }
            } else {
                Text("")
            }
        },
        dismissButton = {
            if (isRunning) {
                TextButton(onClick = onCancel) { Text("取消") }
            }
        },
    )
}

@Composable
private fun ResidueNames(retained: List<PdfSplitResidue>) {
    if (retained.isEmpty()) return
    Text(
        retained.joinToString("\n") { it.displayName },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ProgressBar(current: Int, total: Int) {
    LinearProgressIndicator(
        progress = {
            if (total <= 0) 0f else current.toFloat() / total.toFloat()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildSplitPlan(
    documentDisplayName: String,
    pageCount: Int,
    chapters: List<PdfSplitChapterSource>,
    mode: PdfSplitMode,
    chapterDepth0: Int,
    pagesPerPartText: String,
    useRange: Boolean,
    startText: String,
    endText: String,
): PdfSplitPlan {
    val selection = if (!useRange) {
        null
    } else {
        PdfSplitPageRange(
            startPageIndex = startText.toIntOrNull()?.minus(1) ?: error("起始页无效"),
            endPageIndex = endText.toIntOrNull()?.minus(1) ?: error("结束页无效"),
        )
    }
    return when (mode) {
        PdfSplitMode.BY_CHAPTER -> PdfSplitPlanner.byChapters(
            documentDisplayName = documentDisplayName,
            sourcePageCount = pageCount,
            chapters = chapters,
            selection = selection,
            chapterLevel = if (chapterDepth0 == 0) {
                PdfSplitChapterLevel.TopLevel
            } else {
                PdfSplitChapterLevel.Depth(chapterDepth0)
            },
            samePagePolicy = PdfSplitSamePagePolicy.MERGE,
        )
        PdfSplitMode.BY_PAGE_COUNT -> PdfSplitPlanner.byPageCount(
            documentDisplayName = documentDisplayName,
            sourcePageCount = pageCount,
            pagesPerPart = pagesPerPartText.toIntOrNull() ?: error("每份页数无效"),
            selection = selection,
        )
    }
}

@Composable
private fun SplitChoice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(label)
    }
}

@Composable
private fun SplitNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    )
}
