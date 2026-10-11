package com.m1racle.reader.ui.hub

import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R
import com.m1racle.reader.ui.home.HomeDesignTokens
import com.m1racle.reader.ui.material.LocalGlassEnvironment
import com.m1racle.reader.ui.theme.DockSelectedLight
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.material.QaCoolapk
import com.m1racle.reader.ui.material.QaGlass
import com.m1racle.reader.ui.material.QaCoolapkImport
import com.m1racle.reader.ui.material.qaCoolapkImportGlyph
import com.m1racle.reader.ui.material.qaCoolapkImportRim
import com.m1racle.reader.ui.material.qaCoolapkImportTint
import com.m1racle.reader.ui.material.coolapk
import com.m1racle.reader.ui.material.qaDockSelectedText
import com.m1racle.reader.ui.material.qaGlassPrimaryText

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

/** One continuous glass capsule: two tabs, the import action, then two tabs. */
@Composable
fun GlassDock(
    onSelect: (HubDestination) -> Unit,
    onImportDocument: () -> Unit,
    importing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val motion = LocalHubDockMotion.current
    val backdrop = LocalGlassBackdropHost.current?.root ?: rememberLayerBackdrop()
    val fadeColor = MaterialTheme.colorScheme.background
    // QA variant 2 (Coolapk) changes material only; geometry is always production.
    val dockHeight = HomeDesignTokens.DockHeight
    val dockBottomGap = HomeDesignTokens.DockBottomGap
    val dockMargin = HomeDesignTokens.DockHorizontalMargin
    val fadeHeight = dockHeight + dockBottomGap +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
    Box(modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(fadeHeight)
            .drawBehind {
                drawRect(Brush.verticalGradient(
                    0f to fadeColor.copy(alpha = 0f),
                    .7f to fadeColor.copy(alpha = .28f),
                    1f to fadeColor.copy(alpha = .85f)))
            }.clearAndSetSemantics {})
        LiquidBottomTabs(
            selectedTabIndex = { dockSlotForDestination(motion.selected.ordinal) },
            onTabSelected = { slot -> dockDestinationForSlot(slot)?.let { onSelect(HubDestination.entries[it]) } },
            backdrop = backdrop,
            tabsCount = 5,
            actionSlot = 2,
            containerHeight = dockHeight,
            lensExtraWidth = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = dockMargin)
                .padding(bottom = dockBottomGap)
                .widthIn(max = HomeDesignTokens.DockMaxWidth).fillMaxWidth().selectableGroup()
                .testTag("dock-capsule"),
        ) {
            dockEntries.forEachIndexed { index, entry ->
                if (index == 2) DockImportAction(onImportDocument, enabled = !importing)
                LiquidBottomTab(
                    onClick = { if (entry.destination != motion.selected) onSelect(entry.destination) },
                    selected = entry.destination == motion.selected,
                ) {
                    DockTabVisual(entry, selected = LocalLiquidBottomTabSampling.current,
                        sampled = LocalLiquidBottomTabSampling.current)
                }
            }
        }
    }
}

/**
 * Import button fill. QA variant 2 replaces the opaque gradient with an 80% accent tint,
 * so the Dock's blurred glass shows through underneath (QA_GLASS_VARIANTS.md).
 */
private fun dockImportBrush(
    enabled: Boolean,
    coolapk: Boolean,
    dark: Boolean,
    gradient: List<Color>,
): androidx.compose.ui.graphics.Brush = when {
    !enabled -> androidx.compose.ui.graphics.SolidColor(Color.Gray)
    coolapk -> androidx.compose.ui.graphics.SolidColor(qaCoolapkImportTint(dark))
    else -> androidx.compose.ui.graphics.Brush.horizontalGradient(gradient)
}

/** The middle slot is an action, never a navigation destination. */
internal fun dockSlotForDestination(index: Int): Int = if (index < 2) index else index + 1
internal fun dockDestinationForSlot(slot: Int): Int? = when (slot) {
    0, 1 -> slot
    3, 4 -> slot - 1
    else -> null
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockImportAction(onClick: () -> Unit, enabled: Boolean) {
    val sampled = LocalLiquidBottomTabSampling.current
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val coolapk = QaGlass.selection.coolapk
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) (if (coolapk) QaCoolapk.ImportPressScale else 1.085f) else 1f,
        if (!coolapk) androidx.compose.animation.core.spring(0.55f, 450f)
        else if (pressed) androidx.compose.animation.core.tween(QaCoolapk.PressInMillis,
            easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        else androidx.compose.animation.core.spring(QaCoolapk.ReleaseDampingRatio, QaCoolapk.ReleaseStiffness),
        label = "dock-import-press")
    val dark = LocalGlassEnvironment.current.isDark
    val colors = if (dark) listOf(DockSelectedDark, DockSelectedLight)
        else listOf(DockSelectedLight, Color(0xFF0064D6))
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .then(if (sampled) Modifier.clearAndSetSemantics {} else Modifier
                .testTag("dock-import")
                .clickable(enabled = enabled, interactionSource = interaction, indication = null,
                    role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 49.dp, height = 48.dp)
            .testTag(if (sampled) "dock-import-sample" else "dock-import-surface")
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(dockImportBrush(enabled, coolapk, dark, colors), androidx.compose.foundation.shape.CircleShape)
            // QA variant 2: dense tinted glass keeps a 1dp rim like the capsule.
            .then(if (coolapk && enabled) Modifier.border(QaCoolapkImport.RimWidth,
                qaCoolapkImportRim(dark), androidx.compose.foundation.shape.CircleShape) else Modifier)
            .drawBehind {
                if (pressed) drawRect(Color.White.copy(
                    alpha = if (coolapk) QaCoolapk.ImportPressOverlayAlpha else .20f))
            }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, if (sampled) null else stringResource(R.string.import_file),
                tint = if (coolapk && enabled) qaCoolapkImportGlyph(dark) else Color.White, modifier = Modifier.size(28.dp))
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
    val dark = LocalGlassEnvironment.current.isDark
    val qa = QaGlass.selection
    // QA-only: night lens B uses white bold selected labels; variants pin unselected text.
    val qaSelected = if (selected) qaDockSelectedText(qa.dockNightLens, dark) else null
    val color = if (selected) {
        qaSelected ?: if (dark) DockSelectedDark else DockSelectedLight
    } else qaGlassPrimaryText(qa.variant, dark) ?: scheme.onSurface
    // QA 档 3: selected label is pure black / white bold (blue only on the icon);
    // every label is Medium so it holds up over blurred content.
    val unified = qa.variant == com.m1racle.reader.ui.material.QaGlassVariant.UNIFIED
    val labelColor = if (unified && selected) qaGlassPrimaryText(qa.variant, dark) ?: color else color
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
            modifier = if (sampled) Modifier.clearAndSetSemantics {} else Modifier,
            style = HomeDesignTokens.DockLabelStyle.let { base ->
                // Night lens B: bold selected label. Label size is production in every variant.
                val sized = base
                when {
                    qaSelected != null || (unified && selected) -> sized.copy(fontWeight = FontWeight.Bold)
                    unified -> sized.copy(fontWeight = FontWeight.Medium)
                    else -> sized
                }
            },
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

