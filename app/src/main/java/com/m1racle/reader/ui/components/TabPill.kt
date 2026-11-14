package com.m1racle.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

/**
 * Tab pill whose highlight is dragged by two springs, one per edge.
 *
 * Ported from the Designbookmark "Tab Pill" motion study: the edge facing the
 * direction of travel is stiff (k 620, c 40) and the trailing edge is soft
 * (k 170, c 19), so the pill stretches toward the new tab and then pulls its tail
 * in. Spring constants are taken verbatim from that study's tokens; the integrator
 * matches its engine (`a = -k·(x-t) - c·v`, four sub-steps per frame).
 *
 * Deliberately not using Compose's [androidx.compose.animation.core.spring]: its
 * `stiffness` is a different unit, so the same numbers would not move the same way.
 */
@Composable
fun TabPill(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(labels.isNotEmpty())
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()

    val horizontalPadding = 14.dp
    val barPadding = 4.dp
    // Label widths drive both the layout and the two spring targets.
    val tabWidths = labels.map { label ->
        with(density) {
            measurer.measure(label, style = style, softWrap = false).size.width.toDp() + horizontalPadding * 2
        }
    }
    val edges = remember(tabWidths) {
        buildList {
            var cursor = 0f
            tabWidths.forEach { w ->
                add(cursor)
                cursor += w.value
            }
            add(cursor)
        }
    }

    var leftEdge by remember { mutableFloatStateOf(edges.getOrElse(selectedIndex) { 0f }) }
    var rightEdge by remember { mutableFloatStateOf(edges.getOrElse(selectedIndex + 1) { 0f }) }
    var renderedIndex by remember { mutableIntStateOf(selectedIndex) }
    val left = remember { SpringEdge(leftEdge) }
    val right = remember { SpringEdge(rightEdge) }

    // A new selection retunes the edges by direction, exactly like the source:
    // travelling right makes the right edge stiff, travelling left makes the left one stiff.
    LaunchedEffect(selectedIndex, edges) {
        val from = edges.getOrElse(renderedIndex) { 0f }
        val toLeft = edges.getOrElse(selectedIndex) { from }
        val toRight = edges.getOrElse(selectedIndex + 1) { from }
        val forward = toLeft >= from
        if (forward) {
            right.tune(LEAD_K, LEAD_C)
            left.tune(TRAIL_K, TRAIL_C)
        } else {
            left.tune(LEAD_K, LEAD_C)
            right.tune(TRAIL_K, TRAIL_C)
        }
        left.target = toLeft
        right.target = toRight
        renderedIndex = selectedIndex

        var lastFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 0f else ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(1f / 30f)
            lastFrame = frame
            repeat(SUB_STEPS) {
                left.step(dt / SUB_STEPS)
                right.step(dt / SUB_STEPS)
            }
            leftEdge = left.value
            rightEdge = right.value
            val settled = abs(left.value - left.target) < 0.01f && abs(left.velocity) < 0.01f &&
                abs(right.value - right.target) < 0.01f && abs(right.velocity) < 0.01f
            if (settled) {
                leftEdge = left.target
                rightEdge = right.target
                break
            }
        }
    }

    val pillLeft = with(density) { leftEdge.toDp() }
    val pillWidth = with(density) { max(0f, rightEdge - leftEdge).toDp() }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(barPadding)
            .height(44.dp),
    ) {
        // The highlight sits under the labels; labels only change colour.
        Box(
            Modifier
                .offset(x = pillLeft)
                .width(pillWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(scheme.inverseSurface),
        )
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .width(tabWidths[index])
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { if (!selected) onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = style,
                        color = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One edge of the pill. Units follow the source study: k is stiffness, c is damping. */
private class SpringEdge(start: Float) {
    var value: Float = start
    var velocity: Float = 0f
    var target: Float = start
    private var k: Float = LEAD_K
    private var c: Float = LEAD_C

    fun tune(stiffness: Float, damping: Float) {
        k = stiffness
        c = damping
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        val acceleration = -k * (value - target) - c * velocity
        velocity += acceleration * dt
        value += velocity * dt
    }
}

private const val LEAD_K = 620f
private const val LEAD_C = 40f
private const val TRAIL_K = 170f
private const val TRAIL_C = 19f
private const val SUB_STEPS = 4
