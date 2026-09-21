package com.m1racle.reader.ui.material

import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var selected by remember { mutableStateOf(ComparisonChoice.H0) }
    var playing by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var textBackground by remember { mutableStateOf(true) }
    var checksOpen by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf(List(VisualChecks.size) { false }) }
    val backdropScroll = rememberScrollState()
    val chipScroll = rememberScrollState()
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current.density
    val chipStride = with(LocalDensity.current) { 100.dp.roundToPx() }
    val foreground = if (dark) Color.White else Color.Black
    LaunchedEffect(selected, chipStride) {
        withFrameNanos { }
        withFrameNanos { }
        val index = ComparisonChoice.entries.indexOf(selected)
        chipScroll.scrollTo((index * chipStride).coerceAtMost(chipScroll.maxValue))
    }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            val end = if (backdropScroll.value < backdropScroll.maxValue / 2) backdropScroll.maxValue else 0
            if (backdropScroll.maxValue == 0) {
                withFrameNanos { }
                continue
            }
            try {
                backdropScroll.animateScrollTo(end, tween(5500, easing = LinearEasing))
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
        Text(
            "遗留参照保留当前配方、渐变、旧折射参考。柔光玻璃是 H0 Balanced、H1 Clear、H2 Frosted。横滑选择。仅预览，不改变阅读页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ComparisonChoice.entries.forEach { choice ->
                Column(
                    Modifier
                        .width(96.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .semantics { this.selected = selected == choice }
                        .clickable(role = Role.Tab) { selected = choice }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        choice.label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (selected == choice) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(
                                if (selected == choice) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                schemeCaption(selected, dark, density),
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
            GlassBackdropSource(backdrop, Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().horizontalScroll(backdropScroll)) {
                    repeat(3) { index ->
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
                if (selected == ComparisonChoice.Gradient) {
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
                } else {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("‹ 返回", "第 12 页 · 书名").forEach { label ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .plate(backdrop, selected, SoftGlassRole.Control, RoundedCornerShape(50), dark),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .plate(backdrop, selected, SoftGlassRole.Panel, RoundedCornerShape(24.dp), dark),
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
        Text(
            parameterCaption(selected, dark, density),
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
        } else if (Build.VERSION.SDK_INT < 33 && selected != ComparisonChoice.Current) {
            Text(
                "当前系统可预览模糊与色彩调节。渐变、折射和边缘透镜需要 Android 13 及以上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class ComparisonChoice(val label: String) {
    Current("当前配方"),
    Gradient("渐变"),
    Refraction("旧折射参考"),
    H0("H0"),
    H1("H1"),
    H2("H2"),
    ;

    fun legacyPreset(): GlassPreviewPreset? = when (this) {
        Current -> GlassPreviewPreset.CurrentThick
        Gradient -> GlassPreviewPreset.ProgressiveLegacy
        Refraction -> GlassPreviewPreset.CapsuleReference
        H0, H1, H2 -> null
    }

    fun softCandidate(): SoftGlassCandidate? = when (this) {
        H0 -> SoftGlassCandidate.H0
        H1 -> SoftGlassCandidate.H1
        H2 -> SoftGlassCandidate.H2
        Current, Gradient, Refraction -> null
    }
}

private val VisualChecks = listOf(
    "背景文字不可辨认",
    "背景颜色仍进入玻璃",
    "没有白色塑料感",
    "边缘不是明显描边",
    "前景始终清晰",
)

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

private fun schemeCaption(choice: ComparisonChoice, dark: Boolean, density: Float): String {
    val candidate = choice.softCandidate()
    if (candidate != null) return SoftGlassComparison.schemeLine(candidate, dark, density)
    return legacyCaption(choice.legacyPreset()!!, dark, density)
}

private fun parameterCaption(choice: ComparisonChoice, dark: Boolean, density: Float): String {
    val candidate = choice.softCandidate()
    if (candidate != null) return SoftGlassComparison.parameterSummary(candidate, dark, density)
    val preset = checkNotNull(choice.legacyPreset())
    val spec = legacySpec(preset, dark)
    val px = formatPx(SoftGlassComparison.rendererBlurPx(spec.blur, density))
    return legacyCaption(preset, dark, density) + "\n" +
        "logicalBlur ${formatDp(spec.blur.value)}dp / rendererBlur ${px}px（px = dp × density，线性）\n" +
        "sat ${spec.saturation} · bright ${spec.brightness} · contrast ${spec.contrast} · " +
        "highlight ${formatDp(spec.highlightWidth.value)}dp / ${spec.highlightColor.alpha} · " +
        "refraction ${formatDp(spec.lensHeight.value)}/${formatDp(spec.lensAmount.value)}dp · " +
        "foreground ${spec.foreground.contentAlpha}"
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
    choice: ComparisonChoice,
    role: SoftGlassRole,
    shape: Shape,
    dark: Boolean,
): Modifier {
    val candidate = choice.softCandidate()
    if (candidate != null) {
        return softGlassPreview(
            backdrop,
            shape,
            SoftGlassComparison.resolve(candidate, role, dark = dark),
        )
    }
    return legacyPlate(backdrop, checkNotNull(choice.legacyPreset()), shape)
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
