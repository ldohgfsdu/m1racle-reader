package com.m1racle.reader.ui.pdf

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.pdf.AnchoredNestedInput
import com.m1racle.reader.pdf.anchoredRevealRect
import com.m1racle.reader.pdf.computeAnchoredNestedTarget
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMutedContentAlpha
import com.m1racle.reader.ui.material.glassMaterial
import kotlin.math.roundToInt

internal const val AnchoredRevealOpenMs = com.m1racle.reader.ui.theme.M1racleMotion.SlowMs
internal const val AnchoredRevealCloseMs = com.m1racle.reader.ui.theme.M1racleMotion.NormalMs

/**
 * Preferred open content height by kind via [AdvancedChildLayoutSpec]
 * (header + body floor; clamped by [maxHeightDp] at the call site).
 */
internal fun nestedMinContentHeightDp(state: ReaderMoreState): Dp =
    AdvancedChildLayoutSpec.preferredHeightDp(state).dp

/**
 * Downward foreground child for all four advanced entries.
 *
 * - Independent overlay above Main (Main x/width/right never change)
 * - Morph starts at frozen source-row bounds; height-first growth into a full card
 * - Single header instance (Main blanks that row); real bodies below
 * - Unified SettingsCard material stacked over the parent Menu material
 */
@Composable
internal fun AnchoredNestedPopover(
    state: ReaderMoreState,
    data: ReaderAdvancedData,
    parentRectRoot: Rect,
    sourceRectRoot: Rect,
    hostOriginRoot: Rect,
    viewportBottomRoot: Float,
    maxHeightDp: Dp,
    closing: Boolean,
    onCollapsed: () -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // r26 §4: left edge expands 80–120dp past the parent left (mild widen).
    val leftExpandPx = with(density) { 96.dp.toPx() }
    // r26 §4/§16: fully-open child keeps ≥16dp from the screen left edge.
    val minLeftPx = with(density) { 16.dp.toPx() }
    val bottomMarginPx = with(density) { 20.dp.toPx() }
    val maxHeightPx = with(density) { maxHeightDp.toPx() }
    val preferredH = with(density) {
        AdvancedChildLayoutSpec.clampedHeightDp(
            state = state,
            maxHeightDp = maxHeightDp.value,
        ).dp.toPx()
    }
    val minContentH = preferredH

    // Prefer layout-spec height; measured body can grow only up to maxHeight.
    var measuredContentH by remember(state) { mutableFloatStateOf(0f) }
    val contentH = (if (measuredContentH > 0f) measuredContentH else minContentH)
        .coerceIn(minContentH * 0.85f, maxHeightPx)
        .coerceAtLeast(minContentH.coerceAtMost(maxHeightPx))

    val target = computeAnchoredNestedTarget(
        AnchoredNestedInput(
            parentRectLeft = parentRectRoot.left,
            parentRectTop = parentRectRoot.top,
            parentRectRight = parentRectRoot.right,
            parentRectBottom = parentRectRoot.bottom,
            sourceRectLeft = sourceRectRoot.left,
            sourceRectTop = sourceRectRoot.top,
            sourceRectRight = sourceRectRoot.right,
            sourceRectBottom = sourceRectRoot.bottom,
            minLeftPx = minLeftPx,
            viewportBottom = viewportBottomRoot,
            leftExpandPx = leftExpandPx,
            contentHeightPx = contentH,
            bottomMarginPx = bottomMarginPx,
            minHeightPx = sourceRectRoot.height.coerceAtLeast(1f),
        ),
    )

    val progress = remember { Animatable(0f) }
    LaunchedEffect(closing, state) {
        if (closing) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            progress.animateTo(
                0f,
                com.m1racle.reader.ui.theme.M1racleMotion.normalIn,
            )
            onCollapsed()
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                com.m1racle.reader.ui.theme.M1racleMotion.slow,
            )
        }
    }

    val p = progress.value
    val reveal = anchoredRevealRect(
        sourceLeft = sourceRectRoot.left,
        sourceTop = sourceRectRoot.top,
        sourceRight = sourceRectRoot.right,
        sourceBottom = sourceRectRoot.bottom,
        targetLeft = target.left,
        targetTop = target.top,
        targetRight = target.right,
        targetBottom = target.bottom,
        p = p,
    )

    val localLeft = reveal.left - hostOriginRoot.left
    val localTop = reveal.top - hostOriginRoot.top
    val widthDp = with(density) { reveal.width.toDp() }
    val heightDp = with(density) { reveal.height.toDp() }

    val cornerStart = with(density) { 14.dp.toPx() } // Registered morph keyframe (matches collapsed menu radius).
    val cornerEnd = with(density) { MoreMenuCorner.toPx() }
    val cornerNow = cornerStart + (cornerEnd - cornerStart) * p
    val cornerDp = with(density) { cornerNow.toDp() }

    // Body alpha after geometry progress ~0.25–0.35; close fades body first.
    val bodyAlpha = if (closing) {
        ((p - 0.45f) / 0.45f).coerceIn(0f, 1f)
    } else {
        ((p - 0.30f) / 0.50f).coerceIn(0f, 1f)
    }
    val bodyMaxH = AdvancedChildLayoutSpec.bodyMaxHeightDp(heightDp.value).dp.coerceAtLeast(48.dp)

    Box(
        modifier = modifier
            .offset { IntOffset(localLeft.roundToInt(), localTop.roundToInt()) }
            .width(widthDp)
            .height(heightDp)
            .clip(RoundedCornerShape(cornerDp))
            .clipToBounds()
            // r26 §15: the child card's own area must consume pointer events —
            // taps on blank card space must never fall through to the PDF or
            // the outside-tap catcher (which would dismiss the whole overlay).
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
            .glassMaterial(
                role = GlassMaterialRole.SettingsCard,
                shape = RoundedCornerShape(cornerDp),
                interaction = true,
            ),
    ) {
        // Content fills the morphing container (same width as card — never laid
        // at full target width while the container is still row-sized).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    if (p > 0.5f) {
                        // Prefer measured height once the card is mostly open.
                        measuredContentH = it.size.height.toFloat()
                            .coerceAtLeast(minContentH)
                    }
                },
        ) {
            NestedSourceHeader(
                state = state,
                onClose = onCloseRequest,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .graphicsLayer {
                        clip = true
                        alpha = bodyAlpha
                    },
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = MenuRowHPad),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = GlassMaterialEngine.resolve(
                            role = GlassMaterialRole.SettingsCard,
                            environment = LocalGlassEnvironment.current,
                        ).contentDividerAlpha,
                    ),
                )
                NestedChildBody(
                    state = state,
                    data = data,
                    bodyMaxHeightDp = bodyMaxH,
                    onClose = onCloseRequest,
                )
            }
        }
    }
}

@Composable
private fun NestedSourceHeader(
    state: ReaderMoreState,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = glassMutedContentAlpha(GlassMaterialRole.SettingsCard)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuRowHeight)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClose,
            )
            .padding(horizontal = MenuRowHPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(MenuIconColWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = nestedHeaderIcon(state),
                contentDescription = null,
                modifier = Modifier.size(MenuIconSize),
                tint = scheme.onSurface.copy(alpha = muted),
            )
        }
        Spacer(modifier = Modifier.width(MenuIconGap))
        Text(
            text = stringResource(nestedHeaderLabel(state)),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
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
                .graphicsLayer { rotationZ = 90f },
            tint = scheme.onSurface.copy(alpha = muted),
        )
    }
}

@Composable
private fun NestedChildBody(
    state: ReaderMoreState,
    data: ReaderAdvancedData,
    bodyMaxHeightDp: Dp,
    onClose: () -> Unit,
) {
    when (state) {
        ReaderMoreState.CHILD_PAGE_MAPPING -> {
            val textbook = data.textbookPage
            if (textbook != null) {
                TextbookPageAdvancedContent(
                    params = textbook,
                    onCancel = onClose,
                    bodyMaxHeightDp = bodyMaxHeightDp,
                )
            }
        }
        ReaderMoreState.CHILD_TOC -> {
            val outline = data.outline
            if (outline != null) {
                OutlineAdvancedContent(params = outline, bodyMaxHeightDp = bodyMaxHeightDp)
            }
        }
        ReaderMoreState.CHILD_THUMBNAILS -> {
            val thumbs = data.thumbnails
            if (thumbs != null) {
                ThumbnailsAdvancedContent(params = thumbs, bodyMaxHeightDp = bodyMaxHeightDp)
            }
        }
        ReaderMoreState.CHILD_EXPORT -> {
            val export = data.export
            if (export != null) {
                ExportAdvancedContent(
                    params = export,
                    onCancel = onClose,
                    bodyMaxHeightDp = bodyMaxHeightDp,
                )
            }
        }
        else -> Unit
    }
}

private fun nestedHeaderIcon(state: ReaderMoreState): ImageVector = when (state) {
    ReaderMoreState.CHILD_PAGE_MAPPING -> Icons.Outlined.Tag
    ReaderMoreState.CHILD_TOC -> Icons.AutoMirrored.Outlined.MenuBook
    ReaderMoreState.CHILD_THUMBNAILS -> Icons.Outlined.GridView
    ReaderMoreState.CHILD_EXPORT -> Icons.Outlined.IosShare
    else -> Icons.Outlined.Tag
}

private fun nestedHeaderLabel(state: ReaderMoreState): Int = when (state) {
    ReaderMoreState.CHILD_PAGE_MAPPING -> R.string.pdf_set_print_page
    ReaderMoreState.CHILD_TOC -> R.string.pdf_outline_open
    ReaderMoreState.CHILD_THUMBNAILS -> R.string.pdf_thumbs_open
    ReaderMoreState.CHILD_EXPORT -> R.string.pdf_export_entry
    else -> R.string.pdf_outline_open
}
