package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.m1racle.reader.ui.theme.LocalReaderDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.viewer.readerGradient
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

/** Local comparison only: source and samples are siblings; no preference changes or root sampling. */
@Composable
fun BlurComparisonPreview() {
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val backdrop = rememberLayerBackdrop()
    var dark by remember { mutableStateOf(false) }
    var textBackground by remember { mutableStateOf(true) }
    val presets = GlassPreviewPreset.entries
    val labels = listOf("当前配方", "渐变", "折射参考", "26 / .86", "26 / .80", "26 / .75", "26 / .70",
        "26 / .65", "26 / .60", "26 / .55", "26 / .50", "26 / .45", "32 / .45", "40 / .45", "26 / .35")
    val preset = presets[selected]
    val spec = GlassMaterialEngine.resolve(
        GlassMaterialEngine.previewRole(preset), GlassEnvironment.reader(dark),
        LocalGlassCapability.current, previewPreset = preset,
    )
    val caption = "${spec.blur.value}dp 模糊 · tint ${"%.2f".format(java.util.Locale.ROOT, spec.surfaceColor.alpha)} · " +
        "折射 ${spec.lensHeight.value}/${spec.lensAmount.value}dp"
    LaunchedEffect(playing) {
        if (playing) while (isActive) {
            val end = if (scroll.value < scroll.maxValue/2) scroll.maxValue else 0
            if (scroll.maxValue == 0) { withFrameNanos { }; continue }
            try { scroll.animateScrollTo(end, tween(5500,easing=LinearEasing)) }
            catch (e: CancellationException) {
                coroutineContext.ensureActive()
                playing=false
                break
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("玻璃配方对照",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={playing=!playing}) { Text(if(playing) "暂停" else "播放") }
        }
        Row {
            TextButton(onClick = { dark = !dark }) { Text(if (dark) "暗色 · 点此切浅色" else "浅色 · 点此切暗色") }
            TextButton(onClick = { textBackground = !textBackground }) { Text(if (textBackground) "正文 · 点此切图案" else "图案 · 点此切正文") }
        }
        Text("方案标签：模糊 dp / tint。横滑选择；.75/.65/.55 为 light-tint 中间候选，32/40dp 与 .35 为远端参考。", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index,label ->
                Column(Modifier.width(104.dp).clip(RoundedCornerShape(10.dp))
                    .semantics { this.selected = selected == index }
                    .clickable(role=Role.Tab) { selected=index }
                    .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(label,style=MaterialTheme.typography.labelLarge,
                        color=if(selected==index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(18.dp).height(2.dp).background(if(selected==index) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp))) {
            GlassBackdropSource(backdrop,Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    repeat(3) { index ->
                        Box(Modifier.width(320.dp).fillMaxHeight().background(if(dark) Color(0xFF182027) else Color(0xFFF4EEE3))) {
                            if (!textBackground) Canvas(Modifier.fillMaxSize()) {
                                val colors=listOf(Color(0xFFECA35F),Color(0xFF4F9C91),Color(0xFF7D90C7))
                                drawCircle(colors[index],90.dp.toPx(),Offset(size.width*.65f,size.height*.40f))
                                for (line in 0..18) {
                                    val x=line*22.dp.toPx()
                                    drawLine(Color(0xFF38464F).copy(alpha=.25f),Offset(x,0f),Offset(x-size.height*.5f,size.height),2.dp.toPx())
                                }
                                drawCircle(Color(0xFFD46E65),44.dp.toPx(),Offset(size.width*.15f,size.height*.72f))
                            }
                            if (textBackground) Column(Modifier.padding(16.dp)) {
                                repeat(14) { line ->
                                    Text("${line + 1} 光落在书页上，阅读让细节渐渐清晰。山川与城市之间，留下文字与记忆。",
                                        color = if (dark) Color.White else Color.Black,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            } else Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
                                Text("纸上风景  0${index+1}",color=if(dark) Color.White else Color(0xFF253B3C),
                                    style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                                Text("文字、线条与色彩\n慢慢经过一层薄雾",color=if(dark) Color.White else Color(0xFF253B3C),style=MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(42.dp))
                                Text("Aa  文字  012345\n光落在书页上，风翻过下一章。",color=if(dark) Color.White else Color(0xFF253B3C),style=MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            CompositionLocalProvider(
                LocalGlassBackdropHost provides remember(backdrop) { GlassBackdropHost(backdrop,backdrop) },
                LocalGlassEnvironment provides GlassEnvironment.reader(dark),
                LocalReaderDarkTheme provides dark,
            ) {
            if(selected==1) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(152.dp)
                    .previewBlur(backdrop,selected,RectangleShape)) {
                    Text("渐变模糊",Modifier.padding(20.dp),color=if(dark) Color.White else Color.Black,
                        style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf("‹ 返回","第 12 页 · 书名").forEach { label ->
                            Box(Modifier.weight(1f).height(52.dp).previewBlur(backdrop,selected,RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                                Text(label,color=if(dark) Color.White else Color.Black,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(112.dp).previewBlur(backdrop,selected,RoundedCornerShape(24.dp)),contentAlignment=Alignment.Center) {
                        Text("目录     阅读设置\n\n书签     搜索正文",color=if(dark) Color.White else Color.Black,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            }
        }
        Text(caption,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("左右滑动背景，或点播放；比较正文是否仍能辨认、明暗是否保留、标题是否清楚。仅预览，不改变阅读页。暗色候选尚未验收。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(Build.VERSION.SDK_INT < 33 && selected in 1..2) {
            Text("当前系统可预览模糊底色；渐变与折射效果需要 Android 13 及以上。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Preview uses the same engine and progressive renderer as document chrome. */
@Composable
private fun Modifier.previewBlur(backdrop: LayerBackdrop, variant: Int, shape: Shape): Modifier {
    val preset = GlassPreviewPreset.entries[variant]
    val role = GlassMaterialEngine.previewRole(preset)
    if (preset != GlassPreviewPreset.ProgressiveLegacy) {
        return glassMaterial(role, shape, sourceBackdrop = backdrop, previewPreset = preset)
    }
    return readerGradient(backdrop)
}
