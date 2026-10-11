package com.m1racle.reader.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.atan2

/** The open bottom sector clamps to the nearest endpoint, without wrapping 100% to 0%. */
internal fun wheelFraction(x: Float, y: Float): Float {
    val degrees = ((Math.toDegrees(atan2(y, x).toDouble()).toFloat() - 135f) % 360f + 360f) % 360f
    return if (degrees <= 270f) degrees / 270f else if (degrees < 315f) 1f else 0f
}

@Composable
fun ReaderProgressWheel(value: Float, label: String, onValueChange: (Float) -> Unit,
                        onCommit: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val currentChange by rememberUpdatedState(onValueChange)
    val currentCommit by rememberUpdatedState(onCommit)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val semantics = Modifier.semantics {
        contentDescription = "阅读进度"
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
        if (enabled) setProgress { target -> currentChange(target.coerceIn(0f, 1f)); currentCommit(); true }
        else disabled()
    }
    val gesture = if (!enabled) Modifier else Modifier
        .pointerInput(Unit) {
            var point = Offset.Zero
            fun update() { currentChange(wheelFraction(point.x - size.width / 2f, point.y - size.height / 2f)) }
            detectDragGestures(onDragStart = { point = it; update() }, onDragEnd = { currentCommit() }) { change, delta ->
                change.consume(); point += delta; update()
            }
        }
        .pointerInput(Unit) { detectTapGestures { currentChange(wheelFraction(it.x - size.width / 2f, it.y - size.height / 2f)); currentCommit() } }
    Box(modifier.then(semantics).size(164.dp).then(gesture), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val inset = 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * value.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(value.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
