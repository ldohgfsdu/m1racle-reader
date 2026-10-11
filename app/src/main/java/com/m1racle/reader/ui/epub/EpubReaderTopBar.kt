package com.m1racle.reader.ui.epub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import com.m1racle.reader.ui.theme.sheetContainer
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import com.m1racle.reader.ui.theme.DockSelectedLight
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial

/** The same strip is reserved with controls hidden; toggling never repaginates. */
@Composable
internal fun epubTopBarHeight(): Dp = with(LocalDensity.current) {
    maxOf(56.dp, MaterialTheme.typography.titleSmall.lineHeight.toDp() + 8.dp)
}

@Composable
internal fun EpubReaderTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(epubTopBarHeight()).padding(horizontal = 8.dp)
        .testTag("epub-top-controls"), verticalAlignment = Alignment.CenterVertically) {
        EpubToolbarButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        Spacer(Modifier.weight(1f))
        // The single menu component owns both toolbar actions, even while closed.
        Spacer(Modifier.width(96.dp))
    }
}

/** The original toolbar capsule grows left/down; its only two icons never move or duplicate. */
@Composable
internal fun EpubReaderMenu(
    settingsVisible: Boolean,
    tocVisible: Boolean,
    onDismiss: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    settingsContent: @Composable () -> Unit,
    tocContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets(0, 0, 0, 0),
    controlsVisible: Boolean = true,
) {
    val expanded = settingsVisible || tocVisible
    val capsuleInteraction = remember { MutableInteractionSource() }
    val capsulePressed by capsuleInteraction.collectIsPressedAsState()
    val transition = updateTransition(expanded, label = "epubMenuExpansion")
    val progress by transition.animateFloat(transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
        label = "surface") { if (it) 1f else 0f }
    BackHandler(expanded, onDismiss)
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onDismiss).testTag("epub-menu-dismiss-area"))
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            val panelWidth = minOf(288.dp, maxWidth * .82f, maxWidth - 16.dp)
            val panelHeight = minOf(520.dp, maxHeight - 16.dp)
            val width by transition.animateDp(transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
                label = "width") { if (it) panelWidth else 96.dp }
            val height by transition.animateDp(transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
                label = "height") { if (it) panelHeight else 48.dp }
            val top = (epubTopBarHeight() - 48.dp) / 2
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                primary = if (LocalReaderDarkTheme.current) DockSelectedDark else DockSelectedLight,
            )) {
                AnimatedVisibility(controlsVisible || expanded || transition.currentState,
                    enter = fadeIn(tween(180)), exit = fadeOut(tween(120)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = top, end = 8.dp)) {
                    Box {
                        val shape = RoundedCornerShape(24.dp)
                        Surface(Modifier.size(width, height)
                            .glassMaterial(GlassMaterialRole.Control, shape, pressed = capsulePressed && !expanded)
                            .testTag(if (transition.currentState || transition.targetState)
                                "epub-settings-menu" else "epub-menu-capsule"),
                            shape = shape, color = Color.Transparent, shadowElevation = 12.dp * progress) {
                            Box(Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.sheetContainer.copy(alpha = progress))) {
                                // Lay out at final dimensions and reveal through the growing mask.
                                // Typography never scales or reflows as the capsule expands.
                                AnimatedVisibility(expanded, enter = fadeIn(tween(160, delayMillis = 100)),
                                    exit = fadeOut(tween(90)),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                        .wrapContentSize(Alignment.TopEnd, unbounded = true)
                                        .requiredSize(panelWidth, panelHeight)) {
                                    Column(Modifier.fillMaxSize()) {
                                        Box(Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 112.dp),
                                            contentAlignment = Alignment.CenterStart) {
                                            Text(if (settingsVisible) "阅读设置" else "目录",
                                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        HorizontalDivider(Modifier.padding(horizontal = 20.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                                        Crossfade(settingsVisible, animationSpec = tween(140),
                                            modifier = Modifier.fillMaxWidth().weight(1f), label = "epubMenuTab") { settings ->
                                            if (settings) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                                settingsContent()
                                            } else tocContent()
                                        }
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = onDismiss) { Text("关闭") }
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.TopEnd).size(96.dp, 48.dp)) {
                            // Equal-width segments share one track and one moving selection.
                            Box(Modifier.matchParentSize().padding(4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(50)))
                            val selection by animateFloatAsState(if (settingsVisible) 1f else 0f,
                                animationSpec = GlassMaterialEngine.PressSpring, label = "epubSegmentSelection")
                            if (expanded) Box(Modifier.padding(start = 4.dp, top = 6.dp).size(40.dp, 36.dp)
                                .graphicsLayer { translationX = 48.dp.toPx() * selection }
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
                                .testTag("epub-segment-selection"))
                            Row(Modifier.selectableGroup()) {

                            EpubToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, "目录",
                                onClick = onToc, interaction = capsuleInteraction, glassBackground = false,
                                active = if (expanded) tocVisible else null, tag = "epub-menu-tab-toc")
                            EpubToolbarButton(Icons.Outlined.Settings, "设置",
                                onClick = onSettings, interaction = capsuleInteraction, glassBackground = false,
                                active = if (expanded) settingsVisible else null, tag = "epub-menu-tab-settings")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubToolbarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    glassBackground: Boolean = true,
    active: Boolean? = null,
    tag: String = "epub-back-button",
) {
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(if (pressed) 1.085f else 1f,
        animationSpec = spring(.55f, 450f), label = "epubControlPress")
    Box(Modifier.size(48.dp).testTag(tag).semantics { selected = active == true }
        .clickable(interactionSource = interaction,
        indication = null, role = if (active == null) Role.Button else Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (glassBackground) Box(Modifier.matchParentSize()
            .glassMaterial(GlassMaterialRole.Control, CircleShape, pressed = pressed))
        Icon(icon, contentDescription = description,
            tint = if (active == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
    }
}
