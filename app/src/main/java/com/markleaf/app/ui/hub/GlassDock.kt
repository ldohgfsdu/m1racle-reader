package com.markleaf.app.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.markleaf.app.R
import com.markleaf.app.ui.home.HomeDesignTokens
import com.markleaf.app.ui.material.GlassBackdropHost
import com.markleaf.app.ui.material.GlassBackdropSource
import com.markleaf.app.ui.material.GlassMaterialRole
import com.markleaf.app.ui.material.LocalGlassBackdropHost
import com.markleaf.app.ui.material.glassMaterial

/**
 * Hub backdrop owner: captured page content and the sampling Dock are siblings.
 * The source scope masks its own host, so descendants cannot close an ancestor
 * sampling cycle. The Dock remains under the outer host and keeps real sampling.
 */
@Composable
fun HubGlassShell(
    selected: HubDestination,
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
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

private data class DockEntry(
    val destination: HubDestination,
    val icon: ImageVector,
    val labelRes: Int,
)

/**
 * H2.2 Floating Glass Dock：底部悬浮胶囊（GlassMaterialRole.Control，
 * 强度低于内容表面——继续阅读 hero(Menu) > 实色文档卡 > Dock(Control)）。
 * 四入口 Home/Library/Bookmarks/Settings；静态选中态、无滚动隐藏逻辑，
 * 内容经底部 inset 避让——不遮挡、零新增重绘路径。
 */
@Composable
fun GlassDock(
    selected: HubDestination,
    onSelect: (HubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        DockEntry(HubDestination.HOME, Icons.Filled.Home, R.string.hub_nav_home),
        DockEntry(HubDestination.LIBRARY, Icons.AutoMirrored.Outlined.MenuBook, R.string.hub_nav_library),
        DockEntry(HubDestination.BOOKMARKS, Icons.Outlined.Bookmarks, R.string.hub_nav_bookmarks),
        DockEntry(HubDestination.SETTINGS, Icons.Outlined.Settings, R.string.hub_nav_settings),
    )
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = HomeDesignTokens.DockHorizontalMargin,
                    end = HomeDesignTokens.DockHorizontalMargin,
                    bottom = HomeDesignTokens.DockBottomGap,
                )
                .fillMaxWidth()
                .height(HomeDesignTokens.DockHeight)
                .glassMaterial(
                    role = GlassMaterialRole.Control,
                    shape = HomeDesignTokens.DockShape,
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                val isSelected = entry.destination == selected
                val interaction = remember(entry.destination) { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(HomeDesignTokens.DockItemWeight)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { if (!isSelected) onSelect(entry.destination) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = stringResource(entry.labelRes),
                        tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(HomeDesignTokens.DockItemIcon),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = HomeDesignTokens.DockLabelSize,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
