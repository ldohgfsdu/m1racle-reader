package com.markleaf.app.ui.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.local.PdfPrintPageAnchorEntity
import com.markleaf.app.pdf.PdfThumbnailLoader
import com.markleaf.app.pdf.PrintPageResolver
import com.markleaf.app.ui.theme.sheetContainer
import androidx.compose.material3.BottomSheetDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Virtualized thumbnail grid. Only visible cells request render via [loader].
 * Leaving a cell cancels its [LaunchedEffect] so off-screen results are ignored.
 */
/**
 * @param rangePickMode when true, first click = start, second = end, then [onRangePicked].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailSheet(
    pageCount: Int,
    currentPageIndex: Int,
    printAnchors: List<PdfPrintPageAnchorEntity>,
    loader: PdfThumbnailLoader,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    rangePickMode: Boolean = false,
    onRangePicked: ((start: Int, end: Int) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = currentPageIndex.coerceIn(
            0,
            (pageCount - 1).coerceAtLeast(0),
        ),
    )
    val indices = remember(pageCount) { (0 until pageCount).toList() }
    var rangeStart by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(currentPageIndex, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        val target = currentPageIndex.coerceIn(0, pageCount - 1)
        val anchor = (target - 3).coerceAtLeast(0)
        try {
            gridState.scrollToItem(anchor)
        } catch (_: Exception) {
            // layout race
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
                .heightIn(min = 280.dp, max = 560.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = if (rangePickMode) {
                    stringResource(R.string.pdf_thumbs_range_title)
                } else {
                    stringResource(R.string.pdf_thumbs_title)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = if (rangePickMode) {
                    if (rangeStart == null) {
                        stringResource(R.string.pdf_thumbs_range_pick_start)
                    } else {
                        stringResource(R.string.pdf_thumbs_range_pick_end, rangeStart!! + 1)
                    }
                } else {
                    stringResource(R.string.pdf_thumbs_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                items(
                    items = indices,
                    key = { it },
                    contentType = { "thumb" },
                ) { pageIndex ->
                    val print = PrintPageResolver.resolve(pageIndex, printAnchors)
                    val selected = if (rangePickMode) {
                        pageIndex == rangeStart
                    } else {
                        pageIndex == currentPageIndex
                    }
                    ThumbnailCell(
                        pageIndex = pageIndex,
                        selected = selected,
                        printPage = print,
                        loader = loader,
                        onClick = {
                            if (rangePickMode && onRangePicked != null) {
                                val start = rangeStart
                                if (start == null) {
                                    rangeStart = pageIndex
                                } else {
                                    val a = minOf(start, pageIndex)
                                    val b = maxOf(start, pageIndex)
                                    onRangePicked(a, b)
                                }
                            } else {
                                onPageSelected(pageIndex)
                            }
                        },
                    )
                }
            }
        }
    }
}

private sealed class CellUi {
    data object Loading : CellUi()
    data class Ready(val image: ImageBitmap) : CellUi()
    data class Failed(val message: String) : CellUi()
}

@Composable
private fun ThumbnailCell(
    pageIndex: Int,
    selected: Boolean,
    printPage: Int?,
    loader: PdfThumbnailLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ui by remember(pageIndex) { mutableStateOf<CellUi>(CellUi.Loading) }
    var retryToken by remember(pageIndex) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pageIndex, retryToken, loader) {
        ui = CellUi.Loading
        try {
            when (val r = loader.load(pageIndex)) {
                is PdfThumbnailLoader.ThumbResult.Ready -> ui = CellUi.Ready(r.image)
                is PdfThumbnailLoader.ThumbResult.Failed -> ui = CellUi.Failed(r.message)
                PdfThumbnailLoader.ThumbResult.Closed ->
                    ui = CellUi.Failed("已关闭")
                PdfThumbnailLoader.ThumbResult.Loading -> ui = CellUi.Loading
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ui = CellUi.Failed(e.message ?: "失败")
        }
    }

    val scheme = MaterialTheme.colorScheme
    val borderColor = if (selected) {
        scheme.primary
    } else {
        scheme.outlineVariant
    }
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = ui is CellUi.Ready, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = shape,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                )
                .clip(shape),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = ui) {
                    CellUi.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    is CellUi.Ready -> {
                        Image(
                            bitmap = s.image,
                            contentDescription = stringResource(
                                R.string.pdf_thumb_cd,
                                pageIndex + 1,
                            ),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                        )
                    }
                    is CellUi.Failed -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_thumb_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.pdf_thumb_retry),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        scope.launch {
                                            loader.invalidate(pageIndex)
                                            retryToken++
                                        }
                                    },
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.pdf_thumb_page, pageIndex + 1),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (printPage != null) {
            Text(
                text = stringResource(R.string.pdf_thumb_print, printPage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
