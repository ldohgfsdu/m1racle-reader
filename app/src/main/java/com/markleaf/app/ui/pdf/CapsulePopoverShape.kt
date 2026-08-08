package com.markleaf.app.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Popover silhouette for the EXPANDED position capsule ("jump card").
 *
 * The capsule is a fixed-width pill (200dp). When it expands the popover's
 * TOP EDGE keeps the capsule's exact width and position (continuous with the
 * pill — top corners are pixel-identical to the collapsed state), while the
 * body below widens moderately to [bottomWidth]. The side edges slant
 * smoothly between the two widths, so the surface reads as the pill growing
 * a wider body downward — never as a second, boxier card.
 *
 * [topWidth] stays equal to the collapsed pill width in every state;
 * [bottomWidth] follows the animated surface width (200 → 240 while
 * expanding, 240 → 200 while collapsing), so the silhouette is a plain
 * rounded rectangle at both ends of the animation and only flares while the
 * body is actually visible.
 */
data class CapsulePopoverShape(
    val topWidth: Dp,
    val bottomWidth: Dp,
    val topRadius: Dp,
    val bottomRadius: Dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val topW = with(density) { topWidth.toPx() }.coerceAtMost(size.width)
        val bottomW = with(density) { bottomWidth.toPx() }.coerceAtMost(size.width)
        val rT = with(density) { topRadius.toPx() }.coerceAtMost(size.height / 2f)
        val rB = with(density) { bottomRadius.toPx() }.coerceAtMost(size.height / 2f)
        val cx = size.width / 2f

        val path = Path()
        // Top edge: exactly the collapsed pill's width, centered.
        path.moveTo(cx - topW / 2f + rT, 0f)
        path.lineTo(cx + topW / 2f - rT, 0f)
        // Top-right corner (same arc as the collapsed pill's corner).
        path.arcTo(
            rect = Rect(
                cx + topW / 2f - 2f * rT,
                0f,
                cx + topW / 2f,
                2f * rT,
            ),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        // Right side: slants outward to the wider bottom edge.
        path.lineTo(cx + bottomW / 2f, size.height - rB)
        // Bottom-right corner.
        path.arcTo(
            rect = Rect(
                cx + bottomW / 2f - 2f * rB,
                size.height - 2f * rB,
                cx + bottomW / 2f,
                size.height,
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        // Bottom edge.
        path.lineTo(cx - bottomW / 2f + rB, size.height)
        // Bottom-left corner.
        path.arcTo(
            rect = Rect(
                cx - bottomW / 2f,
                size.height - 2f * rB,
                cx - bottomW / 2f + 2f * rB,
                size.height,
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        // Left side: slants outward to the wider bottom edge.
        path.lineTo(cx - topW / 2f, rT)
        // Top-left corner (same arc as the collapsed pill's corner).
        path.arcTo(
            rect = Rect(
                cx - topW / 2f,
                0f,
                cx - topW / 2f + 2f * rT,
                2f * rT,
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        path.close()
        return Outline.Generic(path)
    }
}
