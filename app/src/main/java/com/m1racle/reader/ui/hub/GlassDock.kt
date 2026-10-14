package com.m1racle.reader.ui.hub

import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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

/** One continuous glass capsule: two tabs, the import action, then two tabs. */
@Composable
fun GlassDock(
    onSelect: (HubDestination) -> Unit,
    onImportDocument: () -> Unit,
    importing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val motion = LocalHubDockMotion.current
    // QA variant 2 (Coolapk) changes material only; geometry is always production.
    val dockHeight = HomeDesignTokens.DockHeight
    val dockBottomGap = HomeDesignTokens.DockBottomGap
    val dockMargin = HomeDesignTokens.DockHorizontalMargin
    Box(modifier.fillMaxSize()) {
        OpticalDockTabs(
            selectedSlot = dockSlotForDestination(motion.selected.ordinal),
            onTabSelected = { slot -> dockDestinationForSlot(slot)?.let { onSelect(HubDestination.entries[it]) } },
            containerHeight = dockHeight,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = dockMargin)
                .padding(bottom = dockBottomGap)
                .widthIn(max = HomeDesignTokens.DockMaxWidth).fillMaxWidth().selectableGroup()
                .testTag("dock-capsule"),
        ) { tabBackdrops, importBackdrop ->
            dockEntries.forEachIndexed { index, entry ->
                if (index == 2) DockImportAction(onImportDocument, enabled = !importing, backdrop = importBackdrop)
                OpticalDockTab(
                    onClick = { if (entry.destination != motion.selected) onSelect(entry.destination) },
                    selected = entry.destination == motion.selected,
                ) {
                    DockTabVisual(entry, selected = entry.destination == motion.selected,
                        backdrop = tabBackdrops[index])
                }
            }
        }
    }
}

/**
 * Tinted import glass transmits the processed dock underneath. QA variant 2
 * retains its measured 80% accent tint (QA_GLASS_VARIANTS.md).
 */
private fun dockImportBrush(
    enabled: Boolean,
    coolapk: Boolean,
    dark: Boolean,
    gradient: List<Color>,
): androidx.compose.ui.graphics.Brush = when {
    !enabled -> androidx.compose.ui.graphics.SolidColor(Color.Gray)
    coolapk -> androidx.compose.ui.graphics.SolidColor(qaCoolapkImportTint(dark))
    else -> androidx.compose.ui.graphics.Brush.horizontalGradient(gradient.map { it.copy(alpha = .88f) })
}

/** The middle slot is an action, never a navigation destination. */
internal fun dockSlotForDestination(index: Int): Int = if (index < 2) index else index + 1
internal fun dockDestinationForSlot(slot: Int): Int? = when (slot) {
    0, 1 -> slot
    3, 4 -> slot - 1
    else -> null
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockImportAction(
    onClick: () -> Unit, enabled: Boolean, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
) {
    val visualTranslation = LocalDockVisualTranslation.current
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
    val colors = listOf(if (dark) DockSelectedDark else Color(0xFF6DB5FF), DockSelectedLight)
    Box(
        Modifier.weight(1f).fillMaxHeight()
                .testTag("dock-import")
                .clickable(enabled = enabled, interactionSource = interaction, indication = null,
                    role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val importShape = RoundedCornerShape(50)
        com.m1racle.reader.ui.material.GlassBackdropSource(backdrop,
            Modifier.graphicsLayer { translationX = visualTranslation() }) {
        Box(Modifier.size(width = 50.dp, height = 36.dp)
            .testTag("dock-import-surface")
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(importShape)
            .background(dockImportBrush(enabled, coolapk, dark, colors), importShape)
            .then(if (enabled && coolapk) Modifier.border(
                QaCoolapkImport.RimWidth,
                qaCoolapkImportRim(dark),
                importShape) else Modifier)
            .drawBehind {
                if (enabled && !coolapk) {
                    // The reference action has a horizontal tint, with light
                    // appearing on pickup rather than a permanent top bevel.
                    val pickup = ((scale - 1f) / .085f).coerceIn(0f, 1f)
                    drawRect(Color.White.copy(alpha = .20f * pickup))
                } else if (pressed) drawRect(Color.White.copy(
                    alpha = QaCoolapk.ImportPressOverlayAlpha))
            }, contentAlignment = Alignment.Center) {
            val description = stringResource(R.string.import_file)
            val glyphColor = if (coolapk && enabled) qaCoolapkImportGlyph(dark) else Color.White
            Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
                val arm = 7.dp.toPx()
                val stroke = 2.5.dp.toPx()
                drawLine(glyphColor, center.copy(x = center.x - arm), center.copy(x = center.x + arm),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(glyphColor, center.copy(y = center.y - arm), center.copy(y = center.y + arm),
                    strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        }
    }
}

@Composable
private fun DockTabVisual(
    entry: DockEntry,
    selected: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val visualTranslation = LocalDockVisualTranslation.current
    val dark = LocalGlassEnvironment.current.isDark
    val qa = QaGlass.selection
    // QA-only: night lens B uses white bold selected labels; variants pin unselected text.
    val qaSelected = if (selected) qaDockSelectedText(qa.dockNightLens, dark) else null
    // The visible foreground is neutral. Only pixels passing through the
    // moving optical lens are tinted, regardless of navigation selection.
    val color = qaGlassPrimaryText(qa.variant, dark) ?: scheme.onSurface
    // QA 档 3: selected label is pure black / white bold (blue only on the icon);
    // every label is Medium so it holds up over blurred content.
    val unified = qa.variant == com.m1racle.reader.ui.material.QaGlassVariant.UNIFIED
    val labelColor = if (unified && selected) qaGlassPrimaryText(qa.variant, dark) ?: color else color
    com.m1racle.reader.ui.material.GlassBackdropSource(backdrop,
        modifier = modifier.graphicsLayer {
            translationX = visualTranslation()
        }) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // CJK font padding leaves extra visible space below the icon. Match
        // the reference's ink spacing while keeping the glyphs near the center
        // of the broad optical rim, where they remain legible on pickup.
        verticalArrangement = Arrangement.spacedBy((-1).dp),
    ) {
        Icon(
            imageVector = entry.selectedIcon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(HomeDesignTokens.DockIconSize)
                .testTag("dock-icon-${entry.destination.name}"),
        )
        Text(
            text = stringResource(entry.labelRes),
            style = HomeDesignTokens.DockLabelStyle.let { base ->
                // Night lens B: bold selected label. Label size is production in every variant.
                val sized = base
                when {
                    qaSelected != null || (unified && selected) -> sized.copy(fontWeight = FontWeight.Bold)
                    unified -> sized.copy(fontWeight = FontWeight.Medium)
                    selected -> sized.copy(fontWeight = FontWeight.SemiBold)
                    else -> sized
                }
            },
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    }
}

