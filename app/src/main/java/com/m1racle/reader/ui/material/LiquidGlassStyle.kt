package com.m1racle.reader.ui.material

import com.m1racle.reader.design.RadixColors
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared liquid material for production surfaces. */
@Immutable
internal data class LiquidGlassStyle(
    val blur: Dp,
    val tint: Color,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val shadowAlpha: Float,
    val shadow: Dp = 8.dp,
    val highlightWidth: Dp = 0.75.dp,
    val highlightAlpha: Float = 0.60f,
)

internal fun liquidGlassStyle(role: GlassMaterialRole, dark: Boolean): LiquidGlassStyle =
    when (role) {
        GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens -> LiquidGlassStyle(
            blur = 0.dp,
            tint = if (dark) RadixColors.White.copy(
                // The overlay also blends into the captured blue foreground.
                // Keep selection quiet without washing out its text contrast.
                alpha = if (role == GlassMaterialRole.DockLens) 0.08f else 0.06f,
            ) else RadixColors.Black.copy(alpha = 0.06f),
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            shadow = 0.dp,
            shadowAlpha = if (dark) 0.24f else 0.12f,
            highlightAlpha = 0f,
        )
        GlassMaterialRole.Dock -> LiquidGlassStyle(
            blur = 4.dp,
            // The night reference retains a charcoal rail even over pure black.
            // Lift the neutral tint, keeping the same background transmission;
            // increasing opacity would hide the reference's colored cards.
            tint = if (dark) RadixColors.GrayDark9.copy(alpha = 0.22f) else RadixColors.White.copy(alpha = 0.32f),
            lensHeight = 8.dp,
            lensAmount = 24.dp,
            shadowAlpha = if (dark) 0.24f else 0.12f,
            // This is the actual rail contour captured by the held lens.
            // Its light cap needs contrast without broadening the spectral rim.
            highlightAlpha = if (dark) 0.60f else 0.90f,
        )
        GlassMaterialRole.Control, GlassMaterialRole.Capsule -> LiquidGlassStyle(
            blur = 4.dp,
            tint = if (dark) RadixColors.GrayDark3.copy(alpha = 0.22f) else RadixColors.White.copy(alpha = 0.32f),
            lensHeight = 8.dp,
            lensAmount = 24.dp,
            shadowAlpha = if (dark) 0.24f else 0.12f,
        )
        GlassMaterialRole.Menu, GlassMaterialRole.SettingsCard -> LiquidGlassStyle(
            blur = 4.dp,
            tint = if (dark) RadixColors.GrayDark3.copy(alpha = 0.64f) else RadixColors.White.copy(alpha = 0.60f),
            lensHeight = 8.dp,
            lensAmount = 24.dp,
            shadowAlpha = if (dark) 0.24f else 0.12f,
        )
        else -> LiquidGlassStyle(
            blur = 24.dp,
            tint = if (dark) RadixColors.GrayDark3.copy(alpha = 0.90f) else RadixColors.Gray2.copy(alpha = 0.90f),
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            shadowAlpha = if (dark) 0.22f else 0.10f,
        )
    }

