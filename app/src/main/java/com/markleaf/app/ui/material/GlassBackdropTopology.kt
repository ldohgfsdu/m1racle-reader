package com.markleaf.app.ui.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * The only ownership boundary for a captured glass backdrop source.
 *
 * A capture source owns [backdrop], but its descendants must never sample that
 * same backdrop: doing so makes a RenderNode depend on an ancestor capture that
 * already contains the child and creates a recursive render graph. Masking the
 * host inside this subtree makes that invariant structural; glass surfaces in
 * captured content keep the shared recipe through the engine's soft-plate path.
 *
 * Sampling surfaces must be composed as siblings after this source, under the
 * outer [LocalGlassBackdropHost] provider. Reader chrome, Home FAB and Hub Dock
 * follow that sibling-overlay rule.
 */
@Composable
fun GlassBackdropSource(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGlassBackdropHost provides null) {
        Box(
            modifier = modifier.layerBackdrop(backdrop),
            content = content,
        )
    }
}
