package com.markleaf.app.ui.viewer.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markleaf.app.data.settings.ReaderSettings
import com.markleaf.app.image.LocalImageLoader
import com.markleaf.app.image.LocalImageState
import com.markleaf.app.markdown.MarkdownBlock
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaled
import com.markleaf.app.ui.viewer.components.ReaderTypography.scaledLineHeight
import kotlinx.coroutines.CancellationException

@Composable
fun ImageBlockView(
    block: MarkdownBlock.Image,
    settings: ReaderSettings,
    isCurrentSearchMatch: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember(block.source) {
        mutableStateOf<LocalImageState>(LocalImageState.Loading)
    }

    LaunchedEffect(block.source) {
        state = LocalImageState.Loading
        try {
            state = LocalImageLoader.load(context.applicationContext, block.source)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state = LocalImageState.Error("图片无法读取")
        }
    }

    val shape = RoundedCornerShape(8.dp)
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val captionStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = ReaderTypography.bodyFontSize.scaled(settings.fontScale * 0.85f),
        lineHeight = ReaderTypography.bodyLineHeight.scaledLineHeight(settings.lineHeightScale),
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .searchBlockHighlight(isCurrentSearchMatch),
    ) {
        when (val s = state) {
            LocalImageState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(shape)
                        .background(placeholderBg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            is LocalImageState.Success -> {
                val bmp = s.image
                val ratio = if (bmp.height > 0) {
                    bmp.width.toFloat() / bmp.height.toFloat()
                } else {
                    16f / 9f
                }
                Image(
                    bitmap = bmp,
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.2f, 5f))
                        .clip(shape),
                )
            }

            is LocalImageState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(shape)
                        .background(placeholderBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (s.message.contains("网络")) {
                                Icons.Outlined.Image
                            } else {
                                Icons.Outlined.BrokenImage
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (block.alt.isNotBlank()) {
                            Text(
                                text = block.alt,
                                style = captionStyle.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = s.message,
                            style = captionStyle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        val caption = buildCaption(block.alt, block.title)
        if (caption != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                style = captionStyle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildCaption(alt: String, title: String?): String? {
    val a = alt.trim()
    val t = title?.trim().orEmpty()
    return when {
        a.isNotEmpty() && t.isNotEmpty() -> "$a · $t"
        a.isNotEmpty() -> a
        t.isNotEmpty() -> t
        else -> null
    }
}
