package com.m1racle.reader.ui.hub.glasslab

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.m1racle.reader.R
import com.m1racle.reader.ui.material.GlassBackdropSource
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
internal fun GlassLabPreview(
    parameters: GlassLabParameters,
    background: Int,
    compare: Boolean,
    dark: Boolean,
    animate: Boolean,
    resetToken: Int,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var animatedAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, reduceMotion, lifecycle) {
        if (animate && !reduceMotion) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var start = 0L
            while (isActive) withFrameNanos { now ->
                if (start == 0L) start = now
                animatedAngle = ((now - start) / 1_000_000_000f * 30f) % 360f
            }
        } else animatedAngle = 0f
    }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resetToken) { dragX = 0f; dragY = 0f }
    val description = stringResource(R.string.glass_lab_drag)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val geometry = glassLabGeometry(parameters, maxWidth.value, maxHeight.value)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val glassWidthPx = with(density) { geometry.width.dp.toPx() }
        val glassHeightPx = with(density) { geometry.height.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val limitX = ((widthPx - glassWidthPx) / 2 - margin).coerceAtLeast(0f)
        val limitY = ((heightPx - glassHeightPx) / 2 - margin).coerceAtLeast(0f)
        val dx = dragX.coerceIn(-limitX, limitX)
        val dy = dragY.coerceIn(-limitY, limitY)
        val splitLocal = glassWidthPx / 2 - dx
        val effectScale = parameters.scale * geometry.fit
        // The captured plain background and the glass are siblings. Never capture the specimen.
        GlassBackdropSource(backdrop, Modifier.matchParentSize()) {
            GlassLabBackground(background, dark, Modifier.fillMaxSize())
        }
        if (compare) Canvas(Modifier.matchParentSize()) {
            drawRect(Color(0xFF808080), Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
        }
        Box(
            Modifier.align(Alignment.Center)
                .offset { androidx.compose.ui.unit.IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .size(geometry.width.dp, geometry.height.dp)
                .semantics { contentDescription = description }
                .pointerInput(limitX, limitY) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        dragX = (dragX.coerceIn(-limitX, limitX) + amount.x).coerceIn(-limitX, limitX)
                        dragY = (dragY.coerceIn(-limitY, limitY) + amount.y).coerceIn(-limitY, limitY)
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(geometry.radius.dp) },
                    effects = {
                        vibrancy()
                        if (parameters.blur > 0f) blur((parameters.blur * effectScale).dp.toPx())
                        if (Build.VERSION.SDK_INT >= 33) {
                            val effectWidth = size.width
                            val effectHeight = size.height
                            val effectOffset = -padding
                            val r = geometry.radius.dp.toPx()
                            val d = (parameters.depth * effectScale).dp.toPx().coerceAtMost(size.minDimension / 2f)
                            val refractionAmount = (parameters.refraction * effectScale).dp.toPx()
                            runtimeShaderEffect("GlassLabLens", GlassLabLensShader, "content") {
                                setFloatUniform("size", effectWidth, effectHeight)
                                setFloatUniform("offset", effectOffset, effectOffset)
                                setFloatUniform("radius", r)
                                setFloatUniform("depth", d)
                                setFloatUniform("curvature", parameters.curvature)
                                setFloatUniform("refraction", refractionAmount)
                                setFloatUniform("splay", parameters.splay)
                                setFloatUniform("chroma", parameters.chroma)
                                setFloatUniform("diagnostic", if (compare) 1f else 0f)
                                setFloatUniform("splitX", splitLocal)
                            }
                        }
                    },
                    highlight = { Highlight(width = 1.dp, blurRadius = .5.dp,
                        alpha = parameters.highlight,
                        style = HighlightStyle.Default(angle = parameters.lightAngle + animatedAngle)) },
                    shadow = { Shadow(radius = 22.dp, offset = DpOffset(0.dp, 8.dp),
                        color = Color.Black.copy(alpha = parameters.shadow)) },
                    onDrawSurface = {
                        val tint = if (dark) Color.Black else Color.White
                        clipRect(right = if (compare) splitLocal.coerceIn(0f, size.width) else size.width) {
                            drawRect(tint.copy(alpha = parameters.opacity))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!compare) Text("Liquid Glass", fontSize = 19.sp,
                color = if (dark) Color.White else Color(0xFF43355D))
        }
        if (compare) {
            Canvas(Modifier.matchParentSize()) {
                drawLine(Color.White.copy(alpha = .8f), Offset(size.width/2,0f),Offset(size.width/2,size.height),1.dp.toPx())
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                Text(stringResource(R.string.glass_lab_appearance), modifier = Modifier.weight(1f),
                    color = if (dark) Color.White else Color(0xFF655879), fontSize = 11.sp)
                Text(stringResource(R.string.glass_lab_normals), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GlassLabBackground(mode: Int, dark: Boolean, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val specimenText = stringResource(R.string.glass_lab_background_text)
    val foreground = if(dark) Color(0xFFEAE1FA) else Color(0xFF594775)
    val text = remember(measurer, specimenText, foreground) {
        measurer.measure(AnnotatedString(specimenText), TextStyle(color = foreground, fontSize = 27.sp))
    }
    Canvas(modifier) {
        drawRect(if(dark) Color(0xFF1D182B) else Color(0xFFF2ECFF))
        when(mode) {
            0 -> {
                val step = 36.dp.toPx()
                val color = if(dark) Color(0xFF756196) else Color(0xFFD2C3ED)
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx()))
                var x = step/2
                while(x<size.width){drawLine(color,Offset(x,0f),Offset(x,size.height),1.dp.toPx(),pathEffect=dash);x+=step}
                var y = step/2
                while(y<size.height){drawLine(color,Offset(0f,y),Offset(size.width,y),1.dp.toPx(),pathEffect=dash);y+=step}
                drawRect(Color(0xFFAE95EB).copy(alpha=.6f), Offset(size.width*.68f,0f), Size(10.dp.toPx(),size.height))
                drawRect(Color(0xFFEFADC5).copy(alpha=.7f), Offset(0f,size.height*.66f), Size(size.width,10.dp.toPx()))
            }
            1 -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFFCEDCFC),Color(0xFFECBDF8),Color(0xFFFFD6CE)),Offset.Zero,Offset(size.width,size.height)))
                drawRect(Brush.radialGradient(listOf(Color(0xFF918CF0),Color.Transparent),Offset(size.width*.68f,size.height*.68f),size.width*.62f))
                drawRect(Brush.radialGradient(listOf(Color(0xFFFFE6CB),Color.Transparent),Offset(size.width*.15f,size.height*.55f),size.width*.46f))
                drawRect(Brush.radialGradient(listOf(Color(0xFFD08FEB),Color.Transparent),Offset(size.width*.07f,size.height*.95f),size.width*.55f))
                if(dark)drawRect(Color(0xFF171329).copy(alpha=.62f))
            }
            else -> {
                val rowHeight = text.size.height + 20.dp.toPx()
                var y = -rowHeight/2
                var row = 0
                while(y < size.height) {
                    drawText(text, topLeft=Offset(if(row%2==0) -24.dp.toPx() else 16.dp.toPx(),y))
                    y+=rowHeight;row++
                }
                drawRect(Color(0xFFAD93F0).copy(alpha=.45f), Offset(0f,size.height*.72f),Size(size.width,24.dp.toPx()))
            }
        }
    }
}
