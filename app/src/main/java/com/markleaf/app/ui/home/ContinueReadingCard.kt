package com.markleaf.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.R
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.ui.material.GlassMaterialRole
import com.markleaf.app.ui.material.glassMaterial

/**
 * H2.2 Phase 1 继续阅读 hero 卡：Hub 核心区域，视觉强度最高的一层玻璃
 * （GlassMaterialRole.Menu > 实色文档卡 > Dock Control）。数据来自
 * DocumentEntity 阅读字段族（唯一事实源），点击走既有 onOpenDocument 流程。
 */
@Composable
fun ContinueReadingCard(
    document: DocumentEntity,
    positionLine: String,
    progressPercent: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val titles = remember(
        document.id, document.title, document.displayName, document.uri,
        document.isPdf, document.isMarkdownPackage,
    ) { resolveDocumentCardTitles(document) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassMaterial(role = GlassMaterialRole.Menu, shape = HomeDesignTokens.HeroShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(
                horizontal = HomeDesignTokens.HeroPaddingH,
                vertical = HomeDesignTokens.HeroPaddingV,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hub_continue_reading),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = HomeDesignTokens.HeroCaptionSize,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = titles.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = HomeDesignTokens.HeroTitleSize,
                        lineHeight = HomeDesignTokens.HeroTitleLine,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${titles.typeLabel} · $positionLine",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = HomeDesignTokens.HeroMetaSize,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = HomeDesignTokens.HeroMetaSize,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = scheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ReadingProgressBar(
                    progress = progressPercent / 100f,
                    activeColor = scheme.primary,
                    trackColor = scheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
