/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only AND Apache-2.0
 *
 * This file is part of RiseDiary. RiseDiary's own code is licensed
 * under GPL-3.0-only (see LICENSE).
 *
 * Portions of this file are adapted and modified from AndroidLiquidGlass
 * by Kyant0 (https://github.com/Kyant0/AndroidLiquidGlass), which is
 * licensed under the Apache License, Version 2.0. Those portions remain
 * subject to Apache-2.0 (see LICENSES/Apache-2.0.txt).
 */
package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import com.m1racle.reader.ui.hub.liquidglass.DampedDragAnimation
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.GlassCapability
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.floor
import com.m1racle.reader.ui.hub.liquidglass.opticalDeformationX
import com.m1racle.reader.ui.hub.liquidglass.opticalDeformationY
import com.m1racle.reader.ui.material.GlassMaterialEngine
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.m1racle.reader.ui.material.QaCoolapk
import com.m1racle.reader.ui.material.QaGlass
import com.m1racle.reader.ui.material.coolapk
import com.m1racle.reader.ui.material.qaDockNightLensStrokeAlpha
import com.m1racle.reader.ui.material.frostShadow
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.theme.DockSelectedLight
import com.m1racle.reader.ui.material.GlassMaterialSpec
import com.m1racle.reader.ui.material.GlassMaterialRole

private val ContinuousCapsule = Capsule(RoundedCornerStyle.Continuous)

private val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * 主三页（首页/记录/设置）内容底部与玻璃底栏之间的安全间距：
 * 导航栏高度 + 底栏(8dp 边距 + 64dp 高) + ~16dp 呼吸空间。
 * 底栏 top 位于 navBar + 72dp，此处取 navBar + 88dp，保证约 16dp 空隙。
 */
@Composable
fun mainPageBottomSpacing(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp

/**
 * 捕获层（隐藏采样 Row）标记：该层只画实心图标供玻璃胶囊采样，
 * 可见层始终画空心图标，选中实心只通过胶囊覆盖区域渐显。
 */
internal val LocalLiquidBottomTabSampling =
    staticCompositionLocalOf { false }

internal fun originalLiquidSelectionPressedScale(): Float = 67f / 48f

internal data class CompactLensMaskBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal fun compactLensMaskBounds(
    containerWidth: Float,
    containerHeight: Float,
    contentPadding: Float,
    tabWidth: Float,
    lensPosition: Float,
    selectionHeight: Float,
    scaleX: Float,
    scaleY: Float,
    isLtr: Boolean,
    overscan: Float = 0f,
    lensWidth: Float = tabWidth,
): CompactLensMaskBounds {
    val unscaledLeft =
        if (isLtr) {
            contentPadding + lensPosition * tabWidth
        } else {
            containerWidth -
                contentPadding -
                (lensPosition + 1f) * tabWidth
        }
    val width = lensWidth * scaleX + overscan * 2f
    val height = selectionHeight * scaleY + overscan * 2f
    return CompactLensMaskBounds(
        left = unscaledLeft + (tabWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height,
    )
}

@Composable
internal fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val sampling = LocalLiquidBottomTabSampling.current
    Column(
        modifier
            .clip(ContinuousCapsule)
            .then(if (sampling) Modifier else Modifier.selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ))
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
internal fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    actionSlot: Int? = null,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    contentPadding: Dp = 4.dp,
    showSelectionShadow: Boolean = true,
    /** Selection lens width beyond one cell (production 6dp; QA variant 2: 8dp). */
    lensExtraWidth: Dp = 6.dp,
    content: @Composable RowScope.() -> Unit
) {
    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val optical = LocalGlassCapability.current == GlassCapability.FULL_OPTICAL
    val isLightTheme = !LocalReaderDarkTheme.current
    val dockStyle = GlassMaterialEngine.resolve(GlassMaterialRole.Dock, !isLightTheme)
    val lensStyle = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, !isLightTheme, pressed = true)
    val restingLens = GlassMaterialEngine.resolve(GlassMaterialRole.SelectionLens, !isLightTheme)
    val lensStrokeAlpha = qaDockNightLensStrokeAlpha(QaGlass.selection.dockNightLens, !isLightTheme)
    val coolapk = QaGlass.selection.coolapk

    val tabsBackdrop = rememberLayerBackdrop()
    val selectionHeight = containerHeight - contentPadding * 2
    val selectionLensRadius = lensStyle.lensHeight
    val selectionChromaticRadius = lensStyle.lensAmount

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - (contentPadding * 2).toPx()) / tabsCount
        }
        // The reference lens extends beyond its stationary slot, including at rest.
        val selectionWidth = with(density) { tabWidth.toDp() } + lensExtraWidth

        var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        var offsetSettleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction =
                    (dragOffset / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        var currentIndex by remember {
            mutableIntStateOf(currentSelectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope, tabWidth, isLtr, coolapk) {
            DampedDragAnimation(
                animationScope = animationScope,
                // QA variant 2: 120ms decelerate press, spring 0.7 / 500 release.
                pressInSpec = if (coolapk) tween(QaCoolapk.PressInMillis, easing = LinearOutSlowInEasing)
                    else DampedDragAnimation.DefaultPressSpec,
                pressOutSpec = if (coolapk) spring(QaCoolapk.ReleaseDampingRatio, QaCoolapk.ReleaseStiffness, 0.001f)
                    else DampedDragAnimation.DefaultPressSpec,
                initialValue = currentSelectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = originalLiquidSelectionPressedScale(),
                acceptGesture = { position, _ ->
                    val physical = floor((position.x - with(density) { contentPadding.toPx() }) / tabWidth)
                        .toInt().coerceIn(0, tabsCount - 1)
                    (if (isLtr) physical else tabsCount - 1 - physical) != actionSlot
                },
                onDragStarted = { position ->
                    offsetSettleJob?.cancel()
                    val physicalIndex = floor((position.x - with(density) { contentPadding.toPx() }) / tabWidth)
                        .toInt().coerceIn(0, tabsCount - 1)
                    updateValue((if (isLtr) physicalIndex else tabsCount - 1 - physicalIndex).toFloat())
                },
                onDragStopped = {
                    val targetIndex =
                        targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (targetIndex != currentSelectedTabIndex()) {
                        currentOnTabSelected(targetIndex)
                    }
                    animationScope.launch {
                        withFrameNanos {}
                        if (!isPressed) animateToValue(currentSelectedTabIndex().toFloat())
                    }
                    offsetSettleJob = animationScope.launch {
                        androidx.compose.animation.core.animate(dragOffset, 0f,
                            animationSpec = spring(1f, 300f, 0.5f)) { offset, _ -> dragOffset = offset }
                    }
                },
                onDragCancelled = {
                    animateToValue(currentSelectedTabIndex().toFloat())
                    offsetSettleJob = animationScope.launch {
                        androidx.compose.animation.core.animate(dragOffset, 0f,
                            animationSpec = spring(1f, 300f, .5f)) { offset, _ -> dragOffset = offset }
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    dragOffset += dragAmount.x
                },
                consumeDragChanges = true
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { currentSelectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        // The gesture hitbox is the stationary dock, never the expanding optical layer.
        Box(Modifier.height(containerHeight).fillMaxWidth()
            .then(dampedDragAnimation.modifier), contentAlignment = androidx.compose.ui.AbsoluteAlignment.CenterLeft) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .dockSurface(backdrop = backdrop, style = dockStyle, optical = optical,
                        pressProgress = { dampedDragAnimation.pressProgress }, coolapkPress = coolapk)
                    .height(containerHeight)
                    .fillMaxWidth()
                    .testTag("dock-material")
            )

            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val maskScaleX = dampedDragAnimation.scaleX * opticalDeformationX(dampedDragAnimation.deformation)
                        val maskScaleY = dampedDragAnimation.scaleY * opticalDeformationY(dampedDragAnimation.deformation)
                        val mask = compactLensMaskBounds(
                            containerWidth = size.width,
                            containerHeight = size.height,
                            contentPadding = contentPadding.toPx(),
                            tabWidth = tabWidth,
                            lensPosition = dampedDragAnimation.value,
                            selectionHeight = selectionHeight.toPx(),
                            scaleX = maskScaleX,
                            scaleY = maskScaleY,
                            isLtr = isLtr,
                            overscan = 1.5.dp.toPx(),
                            lensWidth = selectionWidth.toPx(),
                        )
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(mask.left, mask.top),
                            size = Size(mask.width, mask.height),
                            cornerRadius = CornerRadius(mask.height / 2f),
                            blendMode = BlendMode.Clear
                        )
                    }
                    .height(containerHeight)
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(
                    1f,
                    if (coolapk) QaCoolapk.PressedCellScale else 1.2f,
                    dampedDragAnimation.pressProgress
                )
            },
            LocalLiquidBottomTabSampling provides true
        ) {
            GlassBackdropSource(tabsBackdrop, Modifier.clearAndSetSemantics {}.alpha(0f)) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .dockSurface(backdrop = backdrop, style = dockStyle, optical = optical)
                    .height(containerHeight)
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
            }
        }

        val combinedSelectionBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
        Box(
            Modifier
                .padding(horizontal = contentPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) {
                            dampedDragAnimation.value * tabWidth + panelOffset - with(density) { (lensExtraWidth / 2).toPx() }
                        } else {
                            (tabsCount - 1f - dampedDragAnimation.value) * tabWidth + panelOffset - with(density) { (lensExtraWidth / 2).toPx() }
                        }
                }
                .drawBackdrop(
                    backdrop = combinedSelectionBackdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (optical) lens(
                            selectionLensRadius.toPx() * progress,
                            selectionChromaticRadius.toPx() * progress,
                            depthEffect = false,
                            chromaticAberration = lensStyle.chromaticAberration
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = lensStyle.highlightColor.alpha * progress)
                    },
                    shadow = if (showSelectionShadow) {
                        {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = lensStyle.shadowColor.alpha * progress)
                        }
                    } else {
                        null
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = lensStyle.innerShadow * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX * opticalDeformationX(dampedDragAnimation.deformation)
                        scaleY = dampedDragAnimation.scaleY * opticalDeformationY(dampedDragAnimation.deformation)
                    },
                    onDrawBehind = {
                        drawRect(Color.Red, blendMode = BlendMode.Clear)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            restingLens.surfaceColor,
                            alpha = 1f - progress
                        )
                        // QA-only night lens B: 1dp white rim, fading out like the resting fill.
                        if (lensStrokeAlpha > 0f) {
                            val stroke = 1.dp.toPx()
                            drawRoundRect(
                                color = Color.White.copy(alpha = lensStrokeAlpha * (1f - progress)),
                                topLeft = Offset(stroke / 2f, stroke / 2f),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius((size.height - stroke) / 2f),
                                style = Stroke(width = stroke),
                            )
                        }
                    }
                )
                .height(selectionHeight)
                .width(selectionWidth)
                .testTag("dock-selection")
        )
        }
    }
}

/** Both the displayed dock and the lens source resolve exactly the same material. */
private fun Modifier.dockSurface(
    backdrop: Backdrop,
    style: GlassMaterialSpec,
    optical: Boolean,
    pressProgress: () -> Float = { 0f },
    /** QA variant 2: whole bar press = tint +0.08, rim +0.15, scale 1.03. */
    coolapkPress: Boolean = false,
) =
    drawBackdrop(
        backdrop = backdrop,
        shape = { ContinuousCapsule },
        effects = {
            vibrancy()
            // Match the canonical material renderer: prefilter before Android downsamples blur.
            blur(1.dp.toPx())
            blur(style.blur.toPx())
            if (optical) lens(style.lensHeight.toPx(), style.lensAmount.toPx(), depthEffect = false)
        },
        layerBlock = {
            // Expand only the material; the stationary hitbox and finger coordinates stay fixed.
            val progress = pressProgress()
            val grow = if (coolapkPress) QaCoolapk.PressScale - 1f else .05f
            scaleX = 1f + grow * progress
            scaleY = 1f + grow * progress
        },
        highlight = {
            val progress = pressProgress().coerceIn(0f, 1f)
            Highlight.Default.copy(width = style.highlightWidth,
                alpha = if (coolapkPress) {
                    (style.highlightColor.alpha + QaCoolapk.PressRimAlphaBoost * progress).coerceAtMost(1f)
                } else {
                    lerp(style.highlightColor.alpha, 1f, progress)
                })
        },
        shadow = { Shadow(radius = style.shadow, alpha = style.shadowColor.alpha) },
        onDrawSurface = {
            if (coolapkPress) {
                val progress = pressProgress().coerceIn(0f, 1f)
                drawRect(style.surfaceColor.copy(alpha = (style.surfaceColor.alpha +
                    QaCoolapk.PressSurfaceAlphaBoost * progress).coerceAtMost(1f)))
            } else {
                drawRect(style.surfaceColor)
            }
        },
    )



