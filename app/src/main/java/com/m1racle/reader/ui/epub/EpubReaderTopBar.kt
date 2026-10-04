package com.m1racle.reader.ui.epub

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial

/** The same strip is reserved with controls hidden; toggling never repaginates. */
@Composable
internal fun epubTopBarHeight(): Dp = with(LocalDensity.current) {
    maxOf(56.dp, MaterialTheme.typography.titleSmall.lineHeight.toDp() + 8.dp)
}

@Composable
internal fun EpubReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tocInteraction = remember { MutableInteractionSource() }
    val settingsInteraction = remember { MutableInteractionSource() }
    val tocPressed by tocInteraction.collectIsPressedAsState()
    val settingsPressed by settingsInteraction.collectIsPressedAsState()
    Row(
        modifier.fillMaxWidth().height(epubTopBarHeight()).padding(horizontal = 8.dp)
            .testTag("epub-top-controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EpubToolbarButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.size(96.dp, 48.dp)) {
            Box(Modifier.matchParentSize().padding(4.dp)
                .glassMaterial(GlassMaterialRole.Control, RoundedCornerShape(50),
                    pressed = tocPressed || settingsPressed))
            Row {
                EpubToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, "目录", onToc,
                    interaction = tocInteraction, glassBackground = false)
                EpubToolbarButton(Icons.Outlined.Settings, "设置", onSettings,
                    interaction = settingsInteraction, glassBackground = false)
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
) {
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(if (pressed) 0.9f else 1f,
        animationSpec = GlassMaterialEngine.PressSpring, label = "epubControlPress")
    Box(Modifier.size(48.dp).clickable(interactionSource = interaction,
        indication = ripple(bounded = true, radius = 24.dp), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        if (glassBackground) Box(Modifier.matchParentSize().padding(4.dp)
            .glassMaterial(GlassMaterialRole.Control, CircleShape, pressed = pressed))
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
    }
}
