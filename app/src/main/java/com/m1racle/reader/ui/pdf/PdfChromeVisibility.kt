package com.m1racle.reader.ui.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.offset
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme

/**
 * Alpha transitions allocate a rectangular offscreen buffer sized to the fading
 * node. Keep that node large enough for the drop shadow, and report the original
 * control footprint from an outer wrapper so anchors and hit targets stay put.
 */
@Composable
internal fun PdfChromeVisibility(
    visible: Boolean,
    role: GlassMaterialRole,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spec = GlassMaterialEngine.resolve(role, dark = LocalReaderDarkTheme.current)
    // Three blur radii cover the soft tail and the downward radius/3 offset.
    val shadowSpace = spec.shadow * 3f
    Box(
        modifier.layout { measurable, constraints ->
            val inset = shadowSpace.roundToPx()
            val layer = measurable.measure(constraints.offset(inset * 2, inset * 2))
            layout(
                (layer.width - inset * 2).coerceAtLeast(0),
                (layer.height - inset * 2).coerceAtLeast(0),
            ) {
                layer.place(-inset, -inset)
            }
        },
    ) {
        // Fade must live on this expanded child, not the wrapper that reports
        // the original footprint. Shrinking AnimatedVisibility itself clips the
        // offscreen alpha buffer back to the control rectangle.
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
        ) {
            Box(Modifier.testTag("pdf-chrome-fade-buffer").padding(shadowSpace)) { content() }
        }
    }
}
