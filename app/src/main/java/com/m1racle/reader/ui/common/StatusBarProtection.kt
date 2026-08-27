package com.m1racle.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Extra height below statusBars so protection fades into body content (no hard edge). */
val StatusBarProtectionExtraHeight = 20.dp

/**
 * X-style status-bar protection: **paint only**.
 *
 * - Fixed to the window top (does not scroll with body).
 * - Does **not** add padding / insets / layout reservation.
 * - Body still draws underneath; system status icons stay above the app.
 * - Soft Surface vertical fade (strong at top → fully transparent at bottom). No hard edge.
 *
 * Uses [Brush.verticalGradient] scrim only (no self-blur).
 *
 * @param strength 0 = fully transparent (chrome covers status band); 1 = full protection.
 *   Reader defaults to full strength; Home should pass chrome [collapseFraction].
 */
@Composable
fun StatusBarProtection(
    modifier: Modifier = Modifier,
    strength: Float = 1f,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val protectionHeight = statusTop + StatusBarProtectionExtraHeight
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0.001f) return

    // Apple Books / Safari style: a very light dark scrim over the content,
    // NOT a surface-colored band (a surface gradient reads as a black block
    // in dark mode). The translucent top bar below carries the readability.
    val scrim = Color.Black
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(protectionHeight)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to scrim.copy(alpha = 0.10f * s),
                        0.35f to scrim.copy(alpha = 0.07f * s),
                        0.62f to scrim.copy(alpha = 0.035f * s),
                        0.82f to scrim.copy(alpha = 0.012f * s),
                        1.00f to Color.Transparent,
                    ),
                ),
            ),
    )
}
