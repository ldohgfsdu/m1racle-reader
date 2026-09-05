package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.glassMaterial

/**
 * Hub backdrop owner: captured page content and the sampling Dock are siblings.
 * The source scope masks its own host, so descendants cannot close an ancestor
 * sampling cycle. The Dock remains under the outer host and keeps real sampling.
 */
@Composable
fun HubGlassShell(
    onSelect: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rootBackdrop = rememberLayerBackdrop()
    val parentExport = rememberLayerBackdrop()
    val host = remember(rootBackdrop, parentExport) {
        GlassBackdropHost(root = rootBackdrop, menuExport = parentExport)
    }
    CompositionLocalProvider(LocalGlassBackdropHost provides host) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            GlassBackdropSource(
                backdrop = rootBackdrop,
                modifier = Modifier.fillMaxSize(),
            ) {
                content()
            }
            GlassDock(
                onSelect = onSelect,
            )
        }
    }
}

private data class DockEntry(
    val destination: HubDestination,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val labelRes: Int,
)

private val dockEntries = listOf(
    DockEntry(HubDestination.HOME, Icons.Outlined.Home, Icons.Filled.Home, R.string.hub_nav_home),
    DockEntry(HubDestination.LIBRARY, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook, R.string.hub_nav_library),
    DockEntry(HubDestination.BOOKMARKS, Icons.Outlined.Bookmarks, Icons.Filled.Bookmarks, R.string.hub_nav_bookmarks),
    DockEntry(HubDestination.SETTINGS, Icons.Outlined.Settings, Icons.Filled.Settings, R.string.hub_nav_settings),
)

/** Route-owned selection with a shared moving glass capsule and stable tab hit regions. */
@Composable
fun GlassDock(
    onSelect: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalHubDockMotion.current
    val scheme = MaterialTheme.colorScheme
    val tabsBackdrop = rememberLayerBackdrop()
    val interactions = remember { List(dockEntries.size) { MutableInteractionSource() } }
    val pressedStates = interactions.map { it.collectIsPressedAsState() }
    val anyPressed = pressedStates.any { it.value }
    val dragging by remember(motion.preview) { derivedStateOf { motion.preview.position != null } }
    val held = anyPressed || dragging
    val pressProgress = animateFloatAsState(
        if (held) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f),
        label = "dockHoldExpansion",
    )
    val capsuleScaleX = animateFloatAsState(
        if (held) DockCapsulePressedScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f),
        label = "dockCapsuleScaleX",
    )
    val capsuleScaleY = animateFloatAsState(
        if (held) DockCapsulePressedScale else 1f,
        spring(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f),
        label = "dockCapsuleScaleY",
    )
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var lastDragUptime by remember { mutableLongStateOf(0L) }
    val velocity = animateFloatAsState(
        if (dragging) dragVelocity else 0f,
        spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "dockCapsuleVelocity",
    )
    val contentScale = 1f + (DockContentPressedScale - 1f) * pressProgress.value
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            contentAlignment = AbsoluteAlignment.TopLeft,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = HomeDesignTokens.DockHorizontalMargin)
                .padding(bottom = HomeDesignTokens.DockBottomGap)
                .widthIn(max = HomeDesignTokens.DockMaxWidth)
                .fillMaxWidth()
                .height(HomeDesignTokens.DockHeight),
        ) {
            // Background is a sibling: its capsule clip must not crop the enlarged selection.
            Box(Modifier.matchParentSize().graphicsLayer {
                val scale = 1f + 16.dp.toPx() / size.width * pressProgress.value
                scaleX = scale
                scaleY = scale
            }.glassMaterial(role = GlassMaterialRole.Dock, shape = HomeDesignTokens.DockShape))
            val slotWidth = (maxWidth - HomeDesignTokens.DockContentPadding * 2) / dockEntries.size
            Box(Modifier.fillMaxSize().padding(HomeDesignTokens.DockContentPadding), contentAlignment = AbsoluteAlignment.TopLeft) {
                val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
                val dragModifier = Modifier.pointerInput(motion.selected, slotWidthPx, rtl) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            lastDragUptime = 0L
                            dragVelocity = 0f
                            motion.preview.moveTo(dockPhysicalPosition(offset.x / slotWidthPx - 0.5f, rtl))
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val dtMs = (change.uptimeMillis - lastDragUptime).coerceAtLeast(1L)
                            if (lastDragUptime != 0L) {
                                dragVelocity = (dragAmount / slotWidthPx) / (dtMs / 1000f)
                            }
                            lastDragUptime = change.uptimeMillis
                            // Absolute pointer position avoids counting the slop-crossing event twice.
                            motion.preview.moveTo(dockPhysicalPosition(change.position.x / slotWidthPx - 0.5f, rtl))
                        },
                        onDragEnd = {
                            lastDragUptime = 0L
                            dragVelocity = 0f
                            motion.preview.finish()?.let { target ->
                                if (target != motion.selected) onSelect(target)
                            }
                        },
                        onDragCancel = {
                            lastDragUptime = 0L
                            dragVelocity = 0f
                            motion.preview.cancel()
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .then(dragModifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dockEntries.forEachIndexed { index, entry ->
                        val selected = entry.destination == motion.selected
                        val interaction = interactions[index]
                        val color = if (selected) scheme.primary else scheme.onSurfaceVariant
                        Column(
                            modifier = Modifier
                                .weight(HomeDesignTokens.DockItemWeight)
                                .fillMaxSize()
                                .selectable(
                                    selected = selected,
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { if (!selected) onSelect(entry.destination) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            DockTabVisual(entry = entry, selected = selected, color = color, scale = contentScale)
                        }
                    }
                }
                GlassBackdropSource(
                    backdrop = tabsBackdrop,
                    modifier = Modifier
                        .matchParentSize()
                        .clearAndSetSemantics {}
                        .alpha(0f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { colorFilter = ColorFilter.tint(scheme.primary) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dockEntries.forEach { entry ->
                            val selected = entry.destination == motion.selected
                            Column(
                                modifier = Modifier
                                    .weight(HomeDesignTokens.DockItemWeight)
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                DockTabVisual(
                                    entry = entry,
                                    selected = selected,
                                    color = Color.White,
                                    scale = contentScale,
                                )
                            }
                        }
                    }
                }
                // Drawn last so the lens sits on the icons; it has no pointer input,
                // so tab hits still land on the row beneath.
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(HomeDesignTokens.DockHeight - HomeDesignTokens.DockContentPadding * 2)
                        .graphicsLayer {
                            val logical = motion.position.value
                            translationX = dockPhysicalPosition(logical, rtl) * slotWidthPx
                            val scales = dockCapsuleScales(
                                capsuleScaleX.value,
                                capsuleScaleY.value,
                                velocity.value,
                            )
                            scaleX = scales.first
                            scaleY = scales.second
                        }
                        .glassMaterial(
                            role = GlassMaterialRole.DockSelection,
                            shape = HomeDesignTokens.DockShape,
                            pressed = held,
                            extraBackdrop = tabsBackdrop,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DockTabVisual(
    entry: DockEntry,
    selected: Boolean,
    color: Color,
    scale: Float,
) {
    Column(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (selected) entry.selectedIcon else entry.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(HomeDesignTokens.DockItemIcon),
        )
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = HomeDesignTokens.DockLabelSize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
