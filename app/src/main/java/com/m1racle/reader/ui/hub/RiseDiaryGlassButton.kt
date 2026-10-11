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

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.m1racle.reader.ui.hub.liquidglass.InteractiveHighlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedCornerStyle
import kotlin.math.*
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.GlassCapability

private val ContinuousCapsule = Capsule(RoundedCornerStyle.Continuous)

@Composable
internal fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    onLongClick: (() -> Unit)? = null,
    highlightIntensity: Float = 1f,
    highlightRadiusMultiplier: Float = 1.5f,
    pressExpansion: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit
) {
    val optical = LocalGlassCapability.current == GlassCapability.FULL_OPTICAL
    val animationScope = rememberCoroutineScope()
    val currentHighlightIntensity by rememberUpdatedState(highlightIntensity)
    val currentHighlightRadius by rememberUpdatedState(highlightRadiusMultiplier)
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope,
            intensity = { currentHighlightIntensity },
            radiusMultiplier = { currentHighlightRadius }
        )
    }
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = { vibrancy(); blur(2.dp.toPx()); if (optical) lens(12.dp.toPx(), 24.dp.toPx()) },
                // State changes block new input, while an existing press spring keeps drawing.
                layerBlock = {
                    val width = size.width
                    val heightPx = size.height
                    if (width > 0f && heightPx > 0f) {
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + pressExpansion.toPx() / heightPx, progress)
                        val maxOffset = size.minDimension
                        val offset = interactiveHighlight.offset
                        if (maxOffset > 0f) {
                            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        }
                        val maxDragScale = pressExpansion.toPx() / heightPx
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / heightPx).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (heightPx / width).fastCoerceAtMost(1f)
                    }
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = tint.alpha * 0.75f))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                }
            )
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = if (isInteractive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        interactionSource = null,
                        indication = if (isInteractive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            )
            .then(interactiveHighlight.modifier)
            .then(if (isInteractive && enabled) interactiveHighlight.gestureModifier else Modifier)
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

