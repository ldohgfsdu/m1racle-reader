package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassCapability
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.theme.DockSelectedLight
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.theme.M1racleGlassPolicy
import com.m1racle.reader.ui.theme.M1racleMotion
import com.m1racle.reader.ui.theme.M1racleGlassUsage
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hub page frame. The navigation owner captures the page for its sibling dock.
 */
@Composable
fun HubGlassShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}

private data class DockEntry(
    val destination: HubDestination,
    val selectedIcon: ImageVector,
    val labelRes: Int,
)

private val dockEntries = listOf(
    DockEntry(HubDestination.HOME, Icons.Filled.Home, R.string.hub_nav_home),
    DockEntry(HubDestination.LIBRARY, Icons.AutoMirrored.Filled.MenuBook, R.string.hub_nav_library),
    DockEntry(HubDestination.BOOKMARKS, Icons.Filled.Bookmarks, R.string.hub_nav_bookmarks),
    DockEntry(HubDestination.SETTINGS, Icons.Filled.Settings, R.string.hub_nav_settings),
)

private val DockTabIconSize = 24.dp

/** Floating tab capsule and separate import control; gaps leave the page untouched. */
@Composable
fun GlassDock(
    onSelect: (HubDestination) -> Unit,
    onImportDocument: () -> Unit,
    importing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    check(M1racleGlassPolicy.allows(M1racleGlassUsage.Dock))
    val motion = LocalHubDockMotion.current
    val barShape = RoundedCornerShape(50)
    val interactions = remember { List(dockEntries.size) { MutableInteractionSource() } }
    val pressedTabs = interactions.map { it.collectIsPressedAsState().value }
    val pressedIndex = pressedTabs.indexOfFirst { it }
    var dragging by remember { mutableStateOf(false) }
    // Touch previews the item under the finger; only release changes the route.
    LaunchedEffect(pressedIndex, dragging) {
        if (!dragging) {
            if (pressedIndex >= 0) motion.preview.moveTo(pressedIndex.toFloat())
            else motion.preview.cancel()
        }
    }
    val engaged = dragging || pressedIndex >= 0
    val pickup = animateFloatAsState(
        targetValue = if (engaged) 1f else 0f,
        animationSpec = M1racleMotion.dockPickup,
        label = "dockLensPickup",
    )
    val barTransform: GraphicsLayerScope.() -> Unit = remember(pickup) {
        {
            scaleX = 1f + 0.008f * pickup.value
            scaleY = 1f + 0.02f * pickup.value
        }
    }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val stretch = animateFloatAsState(
        if (dragging) dragVelocity else 0f,
        M1racleMotion.dockPickup, label = "dockDragStretch",
    )
    val lensTransform: GraphicsLayerScope.() -> Unit = remember(pickup, stretch) {
        {
            val (x, y) = dockLensScale(pickup.value, stretch.value)
            scaleX = x
            scaleY = y
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val optical = LocalGlassBackdropHost.current != null &&
        LocalGlassCapability.current == GlassCapability.FULL_OPTICAL
    val selectionBackdrop = rememberLayerBackdrop()
    // Resolve against the page host before entering the guarded capture.
    // The source is a complete, shorter dock including its glass rim, as in
    // RiseDiary. It never samples the selection layer that consumes it.
    val sampledBarMaterial = Modifier.glassMaterial(GlassMaterialRole.Dock, barShape)
    Box(modifier = modifier.fillMaxSize()) {
        val barHeight = with(LocalDensity.current) {
            (DockTabIconSize.toPx() + 2.dp.toPx() + 12.sp.toPx() +
                (HomeDesignTokens.DockContentPadding * 2).toPx()).toDp()
        }.coerceAtLeast(HomeDesignTokens.DockHeight)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = HomeDesignTokens.DockHorizontalMargin)
                .padding(bottom = HomeDesignTokens.DockBottomGap)
                .widthIn(max = HomeDesignTokens.DockMaxWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                contentAlignment = AbsoluteAlignment.TopLeft,
                modifier = Modifier.weight(1f).height(barHeight),
            ) {
                val slotWidth = (maxWidth - HomeDesignTokens.DockContentPadding * 2) / dockEntries.size
                val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
                val dragModifier = Modifier.pointerInput(motion.selected, slotWidthPx, rtl) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            velocityTracker.resetTracking()
                            dragVelocity = 0f
                            motion.preview.moveTo(dockPhysicalPosition(offset.x / slotWidthPx - 0.5f, rtl))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragVelocity = (velocityTracker.calculateVelocity().x / slotWidthPx / 10f).coerceIn(-1f, 1f)
                            motion.preview.moveTo(dockPhysicalPosition(change.position.x / slotWidthPx - 0.5f, rtl))
                        },
                        onDragEnd = {
                            motion.preview.finish()?.let { target ->
                                if (target != motion.selected) onSelect(target)
                            }
                            dragging = false
                        },
                        onDragCancel = {
                            motion.preview.cancel()
                            dragging = false
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .glassMaterial(
                            GlassMaterialRole.Dock, barShape,
                            layerBlock = barTransform,
                        ),
                )
                val lensWidth = slotWidth - 4.dp
                val lensHeight = barHeight - HomeDesignTokens.DockContentPadding * 2
                if (optical) {
                    GlassBackdropSource(selectionBackdrop,
                        Modifier.matchParentSize().alpha(0f).clearAndSetSemantics {}) {
                        Row(Modifier.align(Alignment.Center).fillMaxWidth().height(lensHeight)
                            .then(sampledBarMaterial)
                            .padding(horizontal = HomeDesignTokens.DockContentPadding),
                            verticalAlignment = Alignment.CenterVertically) {
                            dockEntries.forEach { entry ->
                                Box(Modifier.weight(HomeDesignTokens.DockItemWeight).fillMaxSize(),
                                    contentAlignment = Alignment.Center) {
                                    DockTabVisual(entry, selected = true, sampled = true,
                                        modifier = Modifier.graphicsLayer {
                                            val scale = dockGlyphScale(pickup.value)
                                            scaleX = scale
                                            scaleY = scale
                                        })
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(HomeDesignTokens.DockContentPadding)
                        .selectableGroup()
                        .then(dragModifier)
                        .then(if (optical) Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val (scaleX, scaleY) = dockLensScale(pickup.value, stretch.value)
                                val width = lensWidth.toPx() * scaleX
                                val height = lensHeight.toPx() * scaleY
                                drawRoundRect(Color.Transparent,
                                    Offset(dockPhysicalPosition(motion.position.value, rtl) * slotWidthPx +
                                        (slotWidthPx - width) / 2f, (size.height - height) / 2f),
                                    Size(width, height), CornerRadius(lensHeight.toPx() * scaleX / 2f,
                                        height / 2f), blendMode = BlendMode.Clear)
                            } else Modifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dockEntries.forEachIndexed { index, entry ->
                        val selected = entry.destination == motion.selected
                        val pressed = pressedTabs[index]
                        val contentAlpha by animateFloatAsState(
                            targetValue = if (pressed) 0.9f else 1f,
                            animationSpec = M1racleMotion.fast,
                            label = "dockTabPressAlpha",
                        )
                        Column(
                            modifier = Modifier
                                .weight(HomeDesignTokens.DockItemWeight)
                                .fillMaxSize()
                                .graphicsLayer { alpha = contentAlpha }
                                .selectable(
                                    selected = selected,
                                    interactionSource = interactions[index],
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { if (!selected) onSelect(entry.destination) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            DockTabVisual(
                                entry = entry,
                                // Optical selection is drawn only by the moving lens, including
                                // release/cancel frames while it settles back over the selected tab.
                                selected = !optical && if (engaged)
                                    motion.preview.position?.roundToInt() == index else selected,
                                modifier = Modifier.graphicsLayer {
                                    val scale = if (!optical && pressed) dockGlyphScale(pickup.value) else 1f
                                    scaleX = scale
                                    scaleY = scale
                                },
                            )
                        }
                    }
                }
                Box(
                    Modifier.padding(HomeDesignTokens.DockContentPadding)
                        .width(slotWidth).height(lensHeight)
                        .graphicsLayer {
                            translationX = dockPhysicalPosition(motion.position.value, rtl) * slotWidthPx
                        }
                        .padding(horizontal = 2.dp)
                        .then(if (optical) Modifier.glassMaterial(GlassMaterialRole.SelectionLens, barShape,
                            pressed = engaged, sourceBackdrop = selectionBackdrop,
                            layerBlock = lensTransform) else Modifier.graphicsLayer(lensTransform))
                        .background((if (LocalGlassEnvironment.current.isDark) Color.White else Color.Black)
                            .copy(alpha = 0.08f * (1f - pickup.value.coerceIn(0f, 1f))), barShape)
                        .testTag("dock-selection"),
                )
            }
            val importInteraction = remember { MutableInteractionSource() }
            val importPressed by importInteraction.collectIsPressedAsState()
            val importPickup = animateFloatAsState(
                targetValue = if (importPressed) 1f else 0f,
                animationSpec = M1racleMotion.dockPickup,
                label = "dockImportPickup",
            )
            val importTransform: GraphicsLayerScope.() -> Unit = remember(importPickup) {
                {
                    val scale = 1f + 0.12f * importPickup.value.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
            }
            Box(Modifier.size(HomeDesignTokens.DockHeight)
                .clickable(enabled = !importing, interactionSource = importInteraction, indication = null,
                    onClick = onImportDocument)
                .testTag("dock-import"), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize()
                    .glassMaterial(GlassMaterialRole.Dock, CircleShape, pressed = importPressed,
                        layerBlock = importTransform))
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.import_file),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp).graphicsLayer(importTransform))
            }
        }
    }
}

@Composable
private fun DockTabVisual(
    entry: DockEntry,
    selected: Boolean,
    modifier: Modifier = Modifier,
    sampled: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) {
        if (LocalGlassEnvironment.current.isDark) DockSelectedDark else DockSelectedLight
    } else scheme.onSurface.copy(alpha = 0.82f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = entry.selectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(DockTabIconSize)
                .then(if (sampled) Modifier else Modifier.testTag("dock-icon-${entry.destination.name}")),
        )
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = HomeDesignTokens.DockLabelSize,
                lineHeight = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bounded glass pickup; foreground labels remain separate from the optical source. */
internal fun dockLensScale(progress: Float, velocity: Float): Pair<Float, Float> {
    val pickup = progress.coerceIn(0f, 1f)
    val speed = abs(velocity).coerceIn(0f, 1f) * pickup
    return (1f + 0.38f * pickup) * (1f + 0.08f * speed) to
        (1f + 0.44f * pickup) / (1f + 0.06f * speed)
}

/** The optical source and direct foreground use the same held glyph size. */
private fun dockGlyphScale(progress: Float): Float = 1f + 0.22f * progress.coerceIn(0f, 1f)
