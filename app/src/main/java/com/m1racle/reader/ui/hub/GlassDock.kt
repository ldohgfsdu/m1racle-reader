package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.theme.M1racleGlassPolicy
import com.m1racle.reader.ui.theme.M1racleGlassUsage

/**
 * Hub page frame. No document backdrop capture: hub pages have no glass
 * consumers, so an unused LayerBackdrop would only add a graphics layer.
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

private val DockBarCorner = 16.dp
private val DockIndicatorWidth = 16.dp
private val DockIndicatorHeight = 3.dp
private val DockTabIconSize = 24.dp

/** Route-owned selection with a light L3 tab bar and a small sliding indicator. */
@Composable
fun GlassDock(
    onSelect: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    check(M1racleGlassPolicy.allows(M1racleGlassUsage.Dock))
    val motion = LocalHubDockMotion.current
    val scheme = MaterialTheme.colorScheme
    val dockSpec = GlassMaterialEngine.resolve(
        role = GlassMaterialRole.Dock,
        environment = LocalGlassEnvironment.current,
        capability = GlassCapability.SOLID_FALLBACK,
    )
    val barShape = RoundedCornerShape(DockBarCorner)
    val interactions = remember { List(dockEntries.size) { MutableInteractionSource() } }
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
            val slotWidth = (maxWidth - HomeDesignTokens.DockContentPadding * 2) / dockEntries.size
            val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
            val dragModifier = Modifier.pointerInput(motion.selected, slotWidthPx, rtl) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        motion.preview.moveTo(dockPhysicalPosition(offset.x / slotWidthPx - 0.5f, rtl))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        motion.preview.moveTo(dockPhysicalPosition(change.position.x / slotWidthPx - 0.5f, rtl))
                    },
                    onDragEnd = {
                        motion.preview.finish()?.let { target ->
                            if (target != motion.selected) onSelect(target)
                        }
                    },
                    onDragCancel = { motion.preview.cancel() },
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = dockSpec.shadow,
                        shape = barShape,
                    )
                    .clip(barShape)
                    .background(dockSpec.surfaceColor),
            )
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(HomeDesignTokens.DockContentPadding)
                    .selectableGroup()
                    .then(dragModifier),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dockEntries.forEachIndexed { index, entry ->
                    val selected = entry.destination == motion.selected
                    val pressed by interactions[index].collectIsPressedAsState()
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (pressed) 0.7f else 1f,
                        animationSpec = com.m1racle.reader.ui.theme.M1racleMotion.fast,
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
                            selected = selected,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(HomeDesignTokens.DockContentPadding)
                    .width(slotWidth)
                    .height(HomeDesignTokens.DockHeight - HomeDesignTokens.DockContentPadding * 2)
                    .graphicsLayer {
                        translationX = dockPhysicalPosition(motion.position.value, rtl) * slotWidthPx
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 5.dp)
                        .size(width = DockIndicatorWidth, height = DockIndicatorHeight)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary),
                )
            }
        }
    }
}

@Composable
private fun DockTabVisual(
    entry: DockEntry,
    selected: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (selected) entry.selectedIcon else entry.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(DockTabIconSize)
                .testTag("dock-icon-${entry.destination.name}"),
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
