package com.m1racle.reader.ui.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.m1racle.reader.R

private const val BRAND_NAME = "Reader"
private const val BRAND_MARK_ASPECT_RATIO = 74f / 144f

internal data class SettingsBrandMetrics(
    val expandedHeight: Dp,
    val collapsedHeight: Dp,
    val collapseDistance: Dp,
    val horizontalPadding: Dp,
)

@Composable
internal fun rememberSettingsBrandMetrics(width: Dp, horizontalPadding: Dp): SettingsBrandMetrics {
    val density = LocalDensity.current
    val textWidth = (width - horizontalPadding * 2).coerceAtLeast(1.dp)
    val textWidthPx = with(density) { textWidth.roundToPx() }
    val nameStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val textMeasurer = rememberTextMeasurer()
    val nameLayout = textMeasurer.measure(
        text = BRAND_NAME,
        style = nameStyle,
        maxLines = 2,
        constraints = Constraints(maxWidth = textWidthPx),
    )
    val collapsed = with(density) {
        (48.dp.toPx() + 28.sp.toPx()).toDp()
    }.coerceIn(72.dp, 112.dp)
    return remember(width, horizontalPadding, density, collapsed, nameLayout) {
        val nameHeight = with(density) { nameLayout.size.height.toDp() }
        val expanded = 18.dp + 74.dp + 18.dp + nameHeight + 16.dp
        SettingsBrandMetrics(
            expandedHeight = expanded.coerceAtLeast(collapsed + 24.dp),
            collapsedHeight = collapsed,
            collapseDistance = expanded.coerceAtLeast(collapsed + 24.dp) - collapsed,
            horizontalPadding = horizontalPadding,
        )
    }
}

@Composable
internal fun SettingsBrandHeader(
    scroll: ScrollState,
    metrics: SettingsBrandMetrics,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val distancePx = with(density) { metrics.collapseDistance.roundToPx() }
    val progress by remember(scroll, distancePx) {
        derivedStateOf {
            if (distancePx <= 0) 1f
            else (scroll.value / distancePx.toFloat()).coerceIn(0f, 1f)
        }
    }
    val logoWidth = lerp(144.dp, 32.dp, progress)
    val logoHeight = logoWidth * BRAND_MARK_ASPECT_RATIO
    val headerHeight = lerp(metrics.expandedHeight, metrics.collapsedHeight, progress)
    val logoY = lerp(18.dp, 12.dp, progress)
    val logoX = lerp((width - logoWidth) / 2, metrics.horizontalPadding, progress)
    val nameSize = lerp(28.sp, 18.sp, progress)
    val nameLine = lerp(36.sp, 24.sp, progress)
    val nameTop = 18.dp + 74.dp + 18.dp - 16.dp * progress
    val nameAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
    val labelAlpha = ((progress - .72f) / .28f).coerceIn(0f, 1f)
    val brandMarkColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
            .testTag("settings-brand-header"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(metrics.expandedHeight),
        ) {
            Box(
                Modifier
                    .offset(x = logoX, y = logoY)
                    .size(width = logoWidth, height = logoHeight)
                    .testTag("settings-brand-logo"),
            ) {
                Image(
                    painterResource(R.drawable.ic_reader_brand_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(brandMarkColor),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = BRAND_NAME,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = nameSize,
                    lineHeight = nameLine,
                    fontWeight = FontWeight.SemiBold,
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(y = nameTop)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding)
                    .graphicsLayer { alpha = nameAlpha }
                    .testTag("settings-brand-name")
                    .semantics { heading() },
            )
            Text(
                stringResource(R.string.hub_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = logoX + logoWidth + 12.dp, end = metrics.horizontalPadding)
                    .offset(y = logoY + (logoHeight - 24.dp) / 2)
                    .heightIn(min = 24.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}
