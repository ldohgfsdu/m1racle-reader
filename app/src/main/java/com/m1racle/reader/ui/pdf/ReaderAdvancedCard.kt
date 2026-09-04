package com.m1racle.reader.ui.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMutedContentAlpha
import com.m1racle.reader.R
import com.m1racle.reader.pdf.ThumbnailGridColumns
import com.m1racle.reader.pdf.thumbnailInitialItemIndex
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.pdf.PdfThumbnailLoader
import com.m1racle.reader.pdf.TextbookOutlineResolver
import com.m1racle.reader.pdf.export.ChapterExportFormat
import com.m1racle.reader.pdf.export.ExportRangePlan
import com.m1racle.reader.data.local.TextbookOutlineNodeType
import com.m1racle.reader.pdf.export.ExportRangeResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class OutlineEditorState(
    val id: String?,
    val parentId: String?,
    val type: TextbookOutlineNodeType,
    val title: String,
    val startPageIndex: Int,
    val endPageIndex: Int,
)

// ── Advanced Card data (aggregated by PdfViewerScreen; feature state stays
//    in the feature, only presentation wiring lives here) ──────────────────

data class TextbookPageAdvancedData(
    val currentPdfHumanPage: Int,
    val currentPrint: Int?,
    val isExact: Boolean,
    val onConfirm: (Int) -> Unit,
    val onClear: () -> Unit,
)

data class OutlineAdvancedData(
    val nodes: List<TextbookOutlineNodeEntity>,
    val currentPageIndex: Int,
    val hasEmbeddedDrafts: Boolean,
    val onJumpToNode: (TextbookOutlineNodeEntity) -> Unit,
    val onRequestEdit: (OutlineEditorState) -> Unit,
    val onDelete: (String) -> Unit,
    val onImportEmbedded: () -> Unit,
)

data class ThumbnailsAdvancedData(
    val pageCount: Int,
    val currentPageIndex: Int,
    val printAnchors: List<PdfPrintPageAnchorEntity>,
    val loader: PdfThumbnailLoader,
    val rangePickMode: Boolean,
    val onPageSelected: (Int) -> Unit,
    val onRangePicked: ((start: Int, end: Int) -> Unit)?,
)

data class ExportAdvancedData(
    val plan: ExportRangePlan?,
    val documentPageCount: Int,
    val allNodes: List<TextbookOutlineNodeEntity>,
    val initialFormat: ChapterExportFormat,
    val onSelectNode: (TextbookOutlineNodeEntity) -> Unit,
    val onConfirm: (startIndex0: Int, endIndex0: Int, format: ChapterExportFormat) -> Unit,
)

/** Aggregated payload for the Foreground Advanced Card. */
data class ReaderAdvancedData(
    val textbookPage: TextbookPageAdvancedData? = null,
    val outline: OutlineAdvancedData? = null,
    val thumbnails: ThumbnailsAdvancedData? = null,
    val export: ExportAdvancedData? = null,
)

// R25.3: NestedCardHost (full-rect morph + parent de-emphasis) DELETED.
// AdvancedGeometry (advancedMorphRect/computeAdvancedTargetRect) DELETED —
// the only production geometry is AnchoredNestedGeometry + AnchoredNestedPopover.

@Composable
internal fun TextbookPageAdvancedContent(
    params: TextbookPageAdvancedData,
    onCancel: () -> Unit,
    bodyMaxHeightDp: Dp,
) {
    var text by remember { mutableStateOf(params.currentPrint?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    val invalidMsg = stringResource(R.string.pdf_print_invalid)
    val stateLabel = when {
        params.currentPrint == null -> stringResource(R.string.pdf_print_unset)
        params.isExact -> stringResource(R.string.pdf_print_exact, params.currentPrint)
        else -> stringResource(R.string.pdf_print_inferred, params.currentPrint)
    }
    val summary = stringResource(R.string.pdf_set_print_hint, params.currentPdfHumanPage) + " · " + stateLabel

    // NEVER auto-focus / auto-show the IME on open. Only an explicit tap on
    // the field requests focus (BasicTextField does that natively). Close path
    // clears focus + hides keyboard in AnchoredNestedPopover.

    fun submit() {
        val n = text.trim().toIntOrNull()
        if (n == null || n < 1) {
            error = invalidMsg
            return
        }
        error = null
        params.onConfirm(n)
    }

    // Single in-body column (no sticky black Save pill clipped by card bounds).
    // IME Done submits; optional full-height Save sits inside the measured body.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = bodyMaxHeightDp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MenuRowHPad)
            .padding(top = AdvancedChildLayoutSpec.Space8Dp.dp, bottom = AdvancedChildLayoutSpec.Space16Dp.dp),
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface.copy(alpha = muted),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space8Dp.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AdvancedChildLayoutSpec.ControlHeightDp.dp)
                .clip(RoundedCornerShape(AdvancedChildLayoutSpec.InputCornerDp.dp))
                .background(scheme.onSurface.copy(alpha = 0.05f))
                .border(
                    width = 1.dp,
                    color = if (error != null) {
                        scheme.error.copy(alpha = 0.55f)
                    } else {
                        scheme.onSurface.copy(alpha = 0.08f)
                    },
                    shape = RoundedCornerShape(AdvancedChildLayoutSpec.InputCornerDp.dp),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it.filter { ch -> ch.isDigit() }.take(6)
                    error = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pdf_print_label),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = scheme.onSurface.copy(alpha = muted),
                                ),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val errorMsg = error
        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space4Dp.dp))
            Text(
                text = errorMsg,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space12Dp.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (params.isExact) {
                TextButton(onClick = params.onClear) {
                    Text(
                        text = stringResource(R.string.pdf_print_clear),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.pdf_dialog_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(AdvancedChildLayoutSpec.Space4Dp.dp))
            // Full in-body save (≥44dp) — never a clipped absolute footer pill.
            Button(
                onClick = { submit() },
                modifier = Modifier.height(AdvancedChildLayoutSpec.ButtonHeightDp.dp),
                shape = RoundedCornerShape(AdvancedChildLayoutSpec.ActionCornerDp.dp),
                contentPadding = PaddingValues(horizontal = MenuRowHPad, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.pdf_print_save),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}


/**
 * 教材目录 — complete flattened catalog in one list.
 * Opens with the current 章 at the top; later 章/节 stay in order and
 * scroll with the same list. Current row keeps the marker. Not sticky.
 */
@Composable
internal fun OutlineAdvancedContent(
    params: OutlineAdvancedData,
    bodyMaxHeightDp: Dp = 360.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    val flatAll = remember(params.nodes) { TextbookOutlineResolver.flattenTree(params.nodes) }
    val currentId = remember(params.nodes, params.currentPageIndex) {
        TextbookOutlineResolver.deepestContaining(params.currentPageIndex, params.nodes)?.id
    }
    val startIndex = remember(flatAll, currentId) {
        TextbookOutlineResolver.outlineScrollStartIndex(flatAll, currentId).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(bodyMaxHeightDp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(start = MenuRowHPad, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (flatAll.isEmpty()) {
                    stringResource(R.string.pdf_outline_count_empty)
                } else {
                    stringResource(R.string.pdf_outline_count, flatAll.size)
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = scheme.onSurface.copy(alpha = muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = params.onImportEmbedded,
                contentPadding = PaddingValues(horizontal = AdvancedChildLayoutSpec.Space8Dp.dp, vertical = 0.dp),
                modifier = Modifier.height(AdvancedChildLayoutSpec.TextButtonHeightDp.dp),
            ) {
                Text(
                    stringResource(R.string.pdf_outline_scan_ready),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = {
                    params.onRequestEdit(
                        OutlineEditorState(
                            id = null,
                            parentId = null,
                            type = TextbookOutlineNodeType.CHAPTER,
                            title = "",
                            startPageIndex = params.currentPageIndex,
                            endPageIndex = params.currentPageIndex,
                        ),
                    )
                },
                contentPadding = PaddingValues(horizontal = AdvancedChildLayoutSpec.Space8Dp.dp, vertical = 0.dp),
                modifier = Modifier.height(AdvancedChildLayoutSpec.TextButtonHeightDp.dp),
            ) {
                Text(
                    stringResource(R.string.pdf_outline_new_short),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = AdvancedChildLayoutSpec.Space16Dp.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(flatAll, key = { it.node.id }) { row ->
                OutlineNodeRow(
                    row = row,
                    selected = row.node.id == currentId,
                    onClick = { params.onJumpToNode(row.node) },
                )
            }
        }
    }
}

@Composable
private fun OutlineNodeRow(
    row: TextbookOutlineResolver.FlatRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val rowBg = if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(end = MenuRowHPad)
            .height(AdvancedChildLayoutSpec.OutlineRowHeightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = AdvancedChildLayoutSpec.Space8Dp.dp)
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(AdvancedChildLayoutSpec.CardCornerDp.dp))
                .background(if (selected) scheme.primary else Color.Transparent),
        )
        Text(
            text = row.node.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp + (row.depth * 14).dp),
        )
        if (selected) {
            Text(
                text = stringResource(R.string.pdf_outline_current_badge),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AdvancedChildLayoutSpec.CardCornerDp.dp))
                    .background(scheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * 页面缩略图 — compact 3-col grid. Current page is the first visible cell;
 * the page image fills the cell (no letterbox, no caption stack).
 */
@Composable
internal fun ThumbnailsAdvancedContent(
    params: ThumbnailsAdvancedData,
    bodyMaxHeightDp: Dp = 360.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    val indices = remember(params.pageCount) { (0 until params.pageCount).toList() }
    var rangeStart by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = thumbnailInitialItemIndex(params.currentPageIndex),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(bodyMaxHeightDp),
    ) {
        Text(
            text = if (params.rangePickMode) {
                if (rangeStart == null) {
                    stringResource(R.string.pdf_thumbs_range_pick_start)
                } else {
                    stringResource(R.string.pdf_thumbs_range_pick_end, rangeStart!! + 1)
                }
            } else {
                stringResource(R.string.pdf_thumbs_status, params.currentPageIndex + 1, params.pageCount)
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = scheme.onSurface.copy(alpha = muted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MenuRowHPad, vertical = AdvancedChildLayoutSpec.Space6Dp.dp),
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(ThumbnailGridColumns),
            contentPadding = PaddingValues(
                start = AdvancedChildLayoutSpec.Space10Dp.dp,
                top = AdvancedChildLayoutSpec.Space2Dp.dp,
                end = AdvancedChildLayoutSpec.Space10Dp.dp,
                bottom = AdvancedChildLayoutSpec.Space12Dp.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(AdvancedChildLayoutSpec.Space6Dp.dp),
            verticalArrangement = Arrangement.spacedBy(AdvancedChildLayoutSpec.Space6Dp.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(indices, key = { it }) { pageIndex ->
                val selected = if (params.rangePickMode) {
                    pageIndex == rangeStart
                } else {
                    pageIndex == params.currentPageIndex
                }
                ThumbCell(
                    pageIndex = pageIndex,
                    selected = selected,
                    loader = params.loader,
                    onClick = {
                        if (params.rangePickMode && params.onRangePicked != null) {
                            val start = rangeStart
                            if (start == null) {
                                rangeStart = pageIndex
                            } else {
                                val a = minOf(start, pageIndex)
                                val b = maxOf(start, pageIndex)
                                params.onRangePicked(a, b)
                            }
                        } else {
                            params.onPageSelected(pageIndex) // card stays open
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun ThumbCell(
    pageIndex: Int,
    selected: Boolean,
    loader: PdfThumbnailLoader,
    onClick: () -> Unit,
) {
    var ui by remember(pageIndex) { mutableStateOf<ThumbCellUi>(ThumbCellUi.Loading) }
    var retryToken by remember(pageIndex) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(pageIndex, retryToken, loader) {
        ui = ThumbCellUi.Loading
        try {
            when (val r = loader.load(pageIndex)) {
                is PdfThumbnailLoader.ThumbResult.Ready -> ui = ThumbCellUi.Ready(r.image)
                is PdfThumbnailLoader.ThumbResult.Failed -> ui = ThumbCellUi.Failed(r.message)
                PdfThumbnailLoader.ThumbResult.Closed -> ui = ThumbCellUi.Failed("已关闭")
                PdfThumbnailLoader.ThumbResult.Loading -> ui = ThumbCellUi.Loading
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ui = ThumbCellUi.Failed(e.message ?: "失败")
        }
    }

    val borderColor = if (selected) {
        scheme.primary.copy(alpha = 0.85f)
    } else {
        scheme.onSurface.copy(alpha = 0.08f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.707f)
            .clip(RoundedCornerShape(AdvancedChildLayoutSpec.CardCornerDp.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(enabled = ui is ThumbCellUi.Ready, onClick = onClick)
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(AdvancedChildLayoutSpec.CardCornerDp.dp),
            ),
    ) {
        when (val s = ui) {
            ThumbCellUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            is ThumbCellUi.Ready -> Image(
                bitmap = s.image,
                contentDescription = stringResource(R.string.pdf_thumb_cd, pageIndex + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            is ThumbCellUi.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.pdf_thumb_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                )
            }
        }
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(AdvancedChildLayoutSpec.CardCornerDp.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private sealed class ThumbCellUi {
    data object Loading : ThumbCellUi()
    data class Ready(val image: androidx.compose.ui.graphics.ImageBitmap) : ThumbCellUi()
    data class Failed(val message: String) : ThumbCellUi()
}

/**
 * 导出章节 — compact advanced task card (R25 §10, R25.1 §5). Business fields
 * kept, explanatory redundancy compressed; sticky footer keeps
 * 取消 / 选择保存位置 always visible.
 */
@Composable
internal fun ExportAdvancedContent(
    params: ExportAdvancedData,
    onCancel: () -> Unit,
    bodyMaxHeightDp: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    val plan = params.plan
    var startText by remember(plan) {
        mutableStateOf((plan?.pdfPageStart1Based ?: 1).toString())
    }
    var endText by remember(plan) {
        mutableStateOf((plan?.pdfPageEnd1Based ?: 1).toString())
    }
    var format by remember(plan, params.initialFormat) { mutableStateOf(params.initialFormat) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerExpanded by remember { mutableStateOf(false) }
    val flat = remember(params.allNodes) {
        TextbookOutlineResolver.flattenTree(params.allNodes)
    }

    fun submit() {
        val s1 = startText.toIntOrNull()
        val e1 = endText.toIntOrNull()
        if (s1 == null || e1 == null) {
            error = "请输入有效整数页码"
            return
        }
        val s0 = s1 - 1
        val e0 = e1 - 1
        val err = ExportRangeResolver.validateRange(s0, e0, params.documentPageCount)
        if (err != null) {
            error = err
            return
        }
        params.onConfirm(s0, e0, format)
    }

    // Single scrolling body (actions included) so start/end fields are never
    // clipped by a fixed footer against the card corner.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = bodyMaxHeightDp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MenuRowHPad)
            .padding(top = AdvancedChildLayoutSpec.Space8Dp.dp, bottom = AdvancedChildLayoutSpec.Space16Dp.dp),
    ) {
        if (plan == null) {
            Text(
                text = stringResource(R.string.pdf_export_no_nodes),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = muted),
            )
            Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space12Dp.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.pdf_dialog_cancel))
                }
            }
            return@Column
        }
        val summary = plan.pathTitles.joinToString(" / ").ifBlank { plan.node.title } +
            " · PDF ${plan.pdfPageStart1Based}–${plan.pdfPageEnd1Based}"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AdvancedChildLayoutSpec.ControlHeightDp.dp)
                .clickable { pickerExpanded = !pickerExpanded }
                .padding(horizontal = AdvancedChildLayoutSpec.Space2Dp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (pickerExpanded) 90f else 0f),
                tint = scheme.onSurface.copy(alpha = muted),
            )
        }
        AnimatedVisibility(visible = pickerExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                flat.forEach { row ->
                    val selected = row.node.id == plan.node.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = {
                                    params.onSelectNode(row.node)
                                    pickerExpanded = false
                                    error = null
                                },
                                role = Role.RadioButton,
                            )
                            .padding(start = (row.depth * 16).dp, top = AdvancedChildLayoutSpec.Space4Dp.dp, bottom = AdvancedChildLayoutSpec.Space4Dp.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(AdvancedChildLayoutSpec.RadioSizeDp.dp))
                        Spacer(modifier = Modifier.width(AdvancedChildLayoutSpec.Space8Dp.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.node.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.pdf_outline_page_range,
                                    row.node.startPageIndex + 1,
                                    row.node.endPageIndex + 1,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurface.copy(alpha = muted),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space8Dp.dp))
        // Format radios: top-aligned, title ≤2 lines, hint secondary.
        ChapterExportFormat.entries.forEach { option ->
            val selected = format == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { format = option },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = AdvancedChildLayoutSpec.Space4Dp.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier
                        .size(AdvancedChildLayoutSpec.RadioSizeDp.dp)
                        .padding(top = 1.dp),
                )
                Spacer(modifier = Modifier.width(AdvancedChildLayoutSpec.Space10Dp.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exportFormatTitle(option),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = exportFormatShortHint(option),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface.copy(alpha = muted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space8Dp.dp))
        // Start/end half-width fields; list/actions stay in one scroll.
        Row(modifier = Modifier.fillMaxWidth()) {
            ExportCompactField(
                label = stringResource(R.string.pdf_export_start_page),
                value = startText,
                onValueChange = {
                    startText = it.filter { c -> c.isDigit() }.take(6)
                    error = null
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(AdvancedChildLayoutSpec.Space12Dp.dp))
            ExportCompactField(
                label = stringResource(R.string.pdf_export_end_page),
                value = endText,
                onValueChange = {
                    endText = it.filter { c -> c.isDigit() }.take(6)
                    error = null
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space6Dp.dp))
            Text(
                text = error!!,
                color = scheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space12Dp.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.pdf_dialog_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(AdvancedChildLayoutSpec.Space8Dp.dp))
            Button(
                onClick = { submit() },
                modifier = Modifier.height(AdvancedChildLayoutSpec.ButtonHeightDp.dp),
                shape = RoundedCornerShape(AdvancedChildLayoutSpec.ActionCornerDp.dp),
                contentPadding = PaddingValues(horizontal = MenuRowHPad, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.pdf_export_confirm),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

/** Compact format titles — avoid unnatural mid-width hard breaks of long labels. */
private fun exportFormatTitle(format: ChapterExportFormat): String = when (format) {
    ChapterExportFormat.ZIP_PACKAGE -> "ZIP 处理包"
    ChapterExportFormat.PDF_ANNOTATED -> "纯 PDF"
}

/** Secondary format hints (13–14sp). */
private fun exportFormatShortHint(format: ChapterExportFormat): String = when (format) {
    ChapterExportFormat.ZIP_PACKAGE -> "PDF + 元数据 JSON"
    ChapterExportFormat.PDF_ANNOTATED -> "标注章节与页码"
}

@Composable
private fun ExportCompactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = scheme.onSurface.copy(alpha = muted),
        )
        Spacer(modifier = Modifier.height(AdvancedChildLayoutSpec.Space6Dp.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AdvancedChildLayoutSpec.ControlHeightDp.dp)
                .clip(RoundedCornerShape(AdvancedChildLayoutSpec.InputCornerDp.dp))
                .background(scheme.onSurface.copy(alpha = 0.05f))
                .border(
                    width = 1.dp,
                    color = scheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(AdvancedChildLayoutSpec.InputCornerDp.dp),
                )
                .padding(horizontal = AdvancedChildLayoutSpec.Space12Dp.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)
