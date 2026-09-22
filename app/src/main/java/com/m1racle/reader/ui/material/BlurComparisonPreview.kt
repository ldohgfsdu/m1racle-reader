package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import com.m1racle.reader.ui.viewer.readerGradient
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Local comparison only. Legacy plates stay on the production preview path. */
@Composable
fun BlurComparisonPreview() {
    var layer by remember { mutableStateOf(PreviewLayer.Mapping) }
    var family by remember { mutableStateOf(SoftGlassCandidate.H0) }
    var rejected by remember { mutableStateOf(RejectedPlate.Current) }
    var playing by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var textBackground by remember { mutableStateOf(true) }
    var checksOpen by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf(List(VisualChecks.size) { false }) }
    var stressStage by remember { mutableStateOf(GlassStressStage.Full) }
    var stressTarget by remember { mutableStateOf(GlassStressTarget.Both) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val chipScroll = rememberScrollState()
    val backdrop = rememberLayerBackdrop()
    val travel = remember { Animatable(0f) }
    val density = LocalDensity.current
    val densityScale = density.density
    val chipStride = with(density) { 100.dp.roundToPx() }
    val contentWidthPx = with(density) { (StressMotion.PageWidthDp * StressMotion.PageCount).dp.toPx() }
    val maxTravelPx = StressMotion.maxTravelPx(contentWidthPx, viewportWidthPx.toFloat())
    val foreground = if (dark) Color.White else Color.Black
    LaunchedEffect(layer, family, rejected, chipStride) {
        withFrameNanos { }
        withFrameNanos { }
        val index = when (layer) {
            PreviewLayer.Mapping -> 0
            PreviewLayer.Family -> SoftGlassCandidate.entries.indexOf(family)
            PreviewLayer.Rejected -> RejectedPlate.entries.indexOf(rejected)
        }
        chipScroll.scrollTo((index * chipStride).coerceAtMost(chipScroll.maxValue))
    }
    LaunchedEffect(playing, maxTravelPx) {
        if (!playing || maxTravelPx <= 0f) return@LaunchedEffect
        while (isActive) {
            val end = if (travel.value < maxTravelPx / 2f) maxTravelPx else 0f
            try {
                travel.animateTo(end, tween(5500, easing = LinearEasing))
            } catch (e: CancellationException) {
                coroutineContext.ensureActive()
                playing = false
                break
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "玻璃配方对照",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { playing = !playing }) {
                Text(if (playing) "暂停" else "播放")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeChip("浅色", selected = !dark) { dark = false }
            ThemeChip("暗色", selected = dark) { dark = true }
            ThemeChip("正文", selected = textBackground) { textBackground = true }
            ThemeChip("图案", selected = !textBackground) { textBackground = false }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeChip("生产映射", selected = layer == PreviewLayer.Mapping) { layer = PreviewLayer.Mapping }
            ThemeChip("家族参照", selected = layer == PreviewLayer.Family) { layer = PreviewLayer.Family }
            ThemeChip("已淘汰", selected = layer == PreviewLayer.Rejected) { layer = PreviewLayer.Rejected }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeChip("原始", selected = stressStage == GlassStressStage.RawText) {
                stressStage = GlassStressStage.RawText
            }
            ThemeChip("无模糊", selected = stressStage == GlassStressStage.GlassNoBlur) {
                stressStage = GlassStressStage.GlassNoBlur
            }
            ThemeChip("仅模糊", selected = stressStage == GlassStressStage.BlurOnly) {
                stressStage = GlassStressStage.BlurOnly
            }
            ThemeChip("完整", selected = stressStage == GlassStressStage.Full) {
                stressStage = GlassStressStage.Full
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeChip("两者", selected = stressTarget == GlassStressTarget.Both) {
                stressTarget = GlassStressTarget.Both
            }
            ThemeChip("只胶囊", selected = stressTarget == GlassStressTarget.ControlOnly) {
                stressTarget = GlassStressTarget.ControlOnly
            }
            ThemeChip("只面板", selected = stressTarget == GlassStressTarget.PanelOnly) {
                stressTarget = GlassStressTarget.PanelOnly
            }
        }
        if (!PreviewTemporalIsolation.refractionEnabled) {
            Text(
                "折射已关闭，用来隔离闪烁，不是新的材质。背景用图形层平移，采样区域尺寸不变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "生产映射是收口候选：Control 偏 H1，Panel 偏 H0。柔光玻璃家族参照保留 H0、H1、H2，H2 只作厚玻璃参考。当前配方和旧折射参考已淘汰，不回退。仅预览，不改变阅读页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().height(48.dp).horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (layer) {
                PreviewLayer.Mapping -> PreviewChip("Control + Panel", selected = true) {}
                PreviewLayer.Family -> SoftGlassCandidate.entries.forEach { candidate ->
                    val label = if (candidate == SoftGlassCandidate.H2) "H2 厚参考" else candidate.name
                    PreviewChip(label, selected = family == candidate) { family = candidate }
                }
                PreviewLayer.Rejected -> RejectedPlate.entries.forEach { plate ->
                    PreviewChip(plate.label, selected = rejected == plate) { rejected = plate }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                schemeCaption(layer, family, rejected, dark, densityScale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            GlassBackdropSource(
                backdrop,
                Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewportWidthPx = it.width },
            ) {
                Row(
                    Modifier
                        .width((StressMotion.PageWidthDp * StressMotion.PageCount).dp)
                        .fillMaxHeight()
                        .graphicsLayer { translationX = StressMotion.translationX(travel.value) },
                ) {
                    repeat(StressMotion.PageCount) { index ->
                        Box(
                            Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .background(stressBackdropColor(textBackground, dark)),
                        ) {
                            if (textBackground) {
                                Column(Modifier.padding(16.dp)) {
                                    repeat(14) { line ->
                                        Text(
                                            "${line + 1} 光落在书页上，阅读让细节渐渐清晰。山川与城市之间，留下文字与记忆。",
                                            color = foreground,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            } else {
                                ColorStressField(index)
                            }
                        }
                    }
                }
            }
            CompositionLocalProvider(
                LocalGlassBackdropHost provides remember(backdrop) { GlassBackdropHost(backdrop, backdrop) },
                LocalGlassEnvironment provides GlassEnvironment.reader(dark),
                LocalReaderDarkTheme provides dark,
            ) {
                if (stressStage != GlassStressStage.RawText &&
                    layer == PreviewLayer.Rejected &&
                    rejected == RejectedPlate.Gradient
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(152.dp)
                            .legacyPlate(backdrop, GlassPreviewPreset.ProgressiveLegacy, RectangleShape),
                    ) {
                        Text(
                            "渐变模糊",
                            Modifier.padding(20.dp),
                            color = foreground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else if (stressStage != GlassStressStage.RawText) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (stressTarget != GlassStressTarget.PanelOnly) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("‹ 返回", "第 12 页 · 书名").forEach { label ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .plate(
                                                backdrop,
                                                layer,
                                                SoftGlassRole.Control,
                                                RoundedCornerShape(50),
                                                dark,
                                                family,
                                                rejected,
                                                stressStage,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        if (stressTarget != GlassStressTarget.ControlOnly) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .plate(
                                        backdrop,
                                        layer,
                                        SoftGlassRole.Panel,
                                        RoundedCornerShape(24.dp),
                                        dark,
                                        family,
                                        rejected,
                                        stressStage,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "目录     阅读设置\n\n书签     搜索正文",
                                    color = foreground,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            parameterCaption(layer, family, rejected, dark, densityScale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { checksOpen = !checksOpen }) {
            Text(if (checksOpen) "收起视觉检查" else "视觉检查")
        }
        if (checksOpen) {
            Text(
                "人工勾选。不会自动通过，也不写入配方。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VisualChecks.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checks[index],
                        onCheckedChange = { checked ->
                            checks = checks.toMutableList().also { it[index] = checked }
                        },
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (Build.VERSION.SDK_INT < 31) {
            Text(
                "当前系统没有 backdrop blur。模糊与色彩调节需要 Android 12 及以上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (Build.VERSION.SDK_INT < 33 &&
            !(layer == PreviewLayer.Rejected && rejected == RejectedPlate.Current)
        ) {
            Text(
                "当前系统可预览模糊与色彩调节。渐变、折射和边缘透镜需要 Android 13 及以上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class PreviewLayer {
    Mapping,
    Family,
    Rejected,
}

private enum class RejectedPlate(val label: String, val preset: GlassPreviewPreset) {
    Current("当前配方", GlassPreviewPreset.CurrentThick),
    Gradient("渐变", GlassPreviewPreset.ProgressiveLegacy),
    Refraction("旧折射参考", GlassPreviewPreset.CapsuleReference),
}

private val VisualChecks = listOf(
    "背景文字不可辨认",
    "背景颜色仍进入玻璃",
    "没有白色塑料感",
    "边缘不是明显描边",
    "前景始终清晰",
)

@Composable
private fun PreviewChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(96.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun stressBackdropColor(textBackground: Boolean, dark: Boolean): Color = when {
    !textBackground -> Color(0xFF2C3138)
    dark -> Color(0xFF182027)
    else -> Color(0xFFF4EEE3)
}

@Composable
private fun ColorStressField(page: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val shift = page * 0.16f
        drawRect(Color(0xFF2C3138))
        drawCircle(
            Color(0xFF2F6FE8),
            size.minDimension * 0.78f,
            Offset(size.width * (0.20f + shift), size.height * 0.46f),
        )
        drawCircle(
            Color(0xFFE4376A),
            size.minDimension * 0.56f,
            Offset(size.width * (0.88f - shift * 0.25f), size.height * 0.28f),
        )
        drawCircle(
            Color(0xFFFFB01A),
            size.minDimension * 0.50f,
            Offset(size.width * 0.50f, size.height * (0.96f - shift * 0.15f)),
        )
        drawCircle(
            Color(0xFF19D0C8),
            size.minDimension * 0.36f,
            Offset(size.width * (0.70f - shift), size.height * 0.64f),
        )
        drawCircle(
            Color(0xFFFFF6D8),
            size.minDimension * 0.20f,
            Offset(size.width * 0.36f, size.height * 0.16f),
        )
    }
}

private fun schemeCaption(
    layer: PreviewLayer,
    family: SoftGlassCandidate,
    rejected: RejectedPlate,
    dark: Boolean,
    density: Float,
): String = when (layer) {
    PreviewLayer.Mapping -> SoftGlassComparison.mappingSchemeLine(dark, density)
    PreviewLayer.Family -> SoftGlassComparison.schemeLine(family, dark, density)
    PreviewLayer.Rejected -> rejectedCaption(legacyCaption(rejected.preset, dark, density), rejected)
}

private fun parameterCaption(
    layer: PreviewLayer,
    family: SoftGlassCandidate,
    rejected: RejectedPlate,
    dark: Boolean,
    density: Float,
): String = when (layer) {
    PreviewLayer.Mapping -> SoftGlassComparison.mappingSummary(dark, density)
    PreviewLayer.Family -> SoftGlassComparison.parameterSummary(family, dark, density)
    PreviewLayer.Rejected -> {
        val spec = legacySpec(rejected.preset, dark)
        val px = formatPx(SoftGlassComparison.rendererBlurPx(spec.blur, density))
        rejectedCaption(legacyCaption(rejected.preset, dark, density), rejected) + "\n" +
            "logicalBlur ${formatDp(spec.blur.value)}dp / rendererBlur ${px}px（px = dp × density，线性）\n" +
            "sat ${spec.saturation} · bright ${spec.brightness} · contrast ${spec.contrast} · " +
            "highlight ${formatDp(spec.highlightWidth.value)}dp / ${spec.highlightColor.alpha} · " +
            "refraction ${formatDp(spec.lensHeight.value)}/${formatDp(spec.lensAmount.value)}dp · " +
            "foreground ${spec.foreground.contentAlpha}"
    }
}

private fun rejectedCaption(caption: String, plate: RejectedPlate): String = when (plate) {
    RejectedPlate.Current, RejectedPlate.Refraction -> "已淘汰 · $caption"
    RejectedPlate.Gradient -> "旧遮罩 · $caption"
}

private fun legacyCaption(preset: GlassPreviewPreset, dark: Boolean, density: Float): String {
    val spec = legacySpec(preset, dark)
    val px = formatPx(SoftGlassComparison.rendererBlurPx(spec.blur, density))
    val name = when (preset) {
        GlassPreviewPreset.CurrentThick -> "当前配方"
        GlassPreviewPreset.ProgressiveLegacy -> "渐变"
        GlassPreviewPreset.CapsuleReference -> "旧折射参考"
        else -> preset.name
    }
    return "$name · blur ${formatDp(spec.blur.value)}dp/${px}px · tint ${spec.surfaceColor.alpha} · " +
        "sat ${spec.saturation} · bright ${spec.brightness} · contrast ${spec.contrast} · " +
        "highlight ${spec.highlightColor.alpha} · " +
        "refraction ${formatDp(spec.lensHeight.value)}/${formatDp(spec.lensAmount.value)}dp"
}

private fun formatDp(value: Float): String {
    val nearest = value.roundToInt()
    return if (abs(value - nearest) < 0.001f) nearest.toString()
    else String.format(Locale.ROOT, "%.2f", value)
}

private fun formatPx(value: Float): String {
    val nearest = value.roundToInt()
    return if (abs(value - nearest) < 0.05f) nearest.toString()
    else String.format(Locale.ROOT, "%.1f", value)
}

private fun legacySpec(preset: GlassPreviewPreset, dark: Boolean) = GlassMaterialEngine.resolve(
    GlassMaterialEngine.previewRole(preset),
    GlassEnvironment.reader(dark),
    previewPreset = preset,
)

@Composable
private fun Modifier.plate(
    backdrop: LayerBackdrop,
    layer: PreviewLayer,
    role: SoftGlassRole,
    shape: Shape,
    dark: Boolean,
    family: SoftGlassCandidate,
    rejected: RejectedPlate,
    stage: GlassStressStage,
): Modifier = when (layer) {
    PreviewLayer.Mapping -> {
        val mapping = if (role == SoftGlassRole.Control) {
            SoftGlassMapping.ControlCandidate
        } else {
            SoftGlassMapping.PanelCandidate
        }
        softGlassPreview(
            backdrop,
            shape,
            SoftGlassComparison.resolveMapping(mapping, dark = dark),
            stage,
        )
    }
    PreviewLayer.Family -> softGlassPreview(
        backdrop,
        shape,
        SoftGlassComparison.resolve(family, role, dark = dark),
        stage,
    )
    PreviewLayer.Rejected -> legacyPlate(backdrop, rejected.preset, shape)
}

/** Preview uses the same engine and progressive renderer as document chrome. */
@Composable
private fun Modifier.legacyPlate(
    backdrop: LayerBackdrop,
    preset: GlassPreviewPreset,
    shape: Shape,
): Modifier {
    if (preset != GlassPreviewPreset.ProgressiveLegacy) {
        return glassMaterial(
            GlassMaterialEngine.previewRole(preset),
            shape,
            sourceBackdrop = backdrop,
            previewPreset = preset,
        )
    }
    return readerGradient(backdrop)
}
