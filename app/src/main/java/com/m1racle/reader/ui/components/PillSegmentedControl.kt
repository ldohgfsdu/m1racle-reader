package com.m1racle.reader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.GlassBackdropSource
import com.m1racle.reader.ui.material.GlassBackdropHost
import com.m1racle.reader.ui.material.LocalGlassBackdropHost
import com.m1racle.reader.ui.material.LocalGlassCapability
import com.m1racle.reader.ui.material.GlassCapability
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.glassMaterial
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Compact visual track, with a separate 48dp touch area and a spring press lens. */
@Composable
fun PillSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(labels.isNotEmpty())
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textSizes = labels.map { measurer.measure(it, style = style, softWrap = false).size }
    val naturalSegmentWidth = with(density) { textSizes.maxOf { it.width }.toDp() + 8.dp }
        .coerceAtLeast(56.dp)
    val interactions = remember(labels.size) { List(labels.size) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val pressedIndex = pressed.indexOfFirst { it }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val previewPosition = dragPosition ?: (if (pressedIndex >= 0) pressedIndex else selectedIndex).toFloat()
    val previewIndex = previewPosition.roundToInt()
    val position = animateFloatAsState(
        targetValue = previewPosition,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
        label = "segmentPosition",
    )
    val press = animateFloatAsState(
        targetValue = if (pressedIndex >= 0 || dragPosition != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "segmentPress",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier) {
        // Ordinary phone widths show the whole group, including its last option
        // and both rounded ends. At accessibility sizes keep full text scrollable.
        val segmentWidth = if (density.fontScale <= 1.3f && maxWidth / labels.size >= 56.dp) {
            naturalSegmentWidth.coerceAtMost(maxWidth / labels.size)
        } else naturalSegmentWidth
        val labelWidth = with(density) { (segmentWidth - 8.dp).roundToPx() }
        val labelHeight = labels.maxOf {
            measurer.measure(it, style = style, constraints = Constraints(maxWidth = labelWidth)).size.height
        }
        val trackHeight = with(density) { labelHeight.toDp() + 10.dp }.coerceAtLeast(38.dp)
        val touchHeight = trackHeight.coerceAtLeast(48.dp)
        val slotWidthPx = with(density) { segmentWidth.toPx() }
        val overflowing = segmentWidth * labels.size > maxWidth
        val dragModifier = Modifier.pointerInput(slotWidthPx, rtl, labels.size, overflowing) {
            fun preview(x: Float) {
                val physical = x / slotWidthPx - 0.5f
                dragPosition = (if (rtl) labels.lastIndex - physical else physical)
                    .coerceIn(0f, labels.lastIndex.toFloat())
            }
            val finish: () -> Unit = {
                val target = dragPosition?.roundToInt()
                dragPosition = null
                if (target != null) currentOnSelect(target)
            }
            // A normal swipe scrolls an overflowing track. Holding first picks up the lens.
            if (overflowing) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { preview(it.x) },
                    onDrag = { change, _ -> change.consume(); preview(change.position.x) },
                    onDragEnd = finish,
                    onDragCancel = { dragPosition = null },
                )
            } else {
                detectHorizontalDragGestures(
                    onDragStart = { preview(it.x) },
                    onHorizontalDrag = { change, _ -> change.consume(); preview(change.position.x) },
                    onDragEnd = finish,
                    onDragCancel = { dragPosition = null },
                )
            }
        }
        val backdrop = rememberLayerBackdrop()
        val localHost = remember(backdrop) { GlassBackdropHost(backdrop, backdrop) }
        val optical = LocalGlassCapability.current == GlassCapability.FULL_OPTICAL
        val trackModifier = Modifier.drawBehind {
            val height = trackHeight.toPx()
            drawRoundRect(scheme.surfaceContainerHigh, Offset(0f, (size.height - height) / 2f),
                Size(size.width, height), CornerRadius(height / 2f))
        }
        val labelRow: @Composable (Boolean, Modifier) -> Unit = { sampled, rowModifier ->
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.width(segmentWidth).height(touchHeight)
                            .then(if (sampled) Modifier else Modifier.selectable(
                                selected = selectedIndex == index,
                                role = Role.RadioButton,
                                interactionSource = interactions[index],
                                indication = null,
                                onClick = { onSelect(index) },
                            )).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = style, textAlign = TextAlign.Center,
                            softWrap = true,
                            modifier = Modifier.graphicsLayer {
                                val scale = if (sampled) 1f + 0.12f * press.value else 1f
                                scaleX = scale
                                scaleY = scale
                            },
                            color = if (selectedIndex == index) scheme.onSurface else scheme.onSurfaceVariant)
                    }
                }
            }
        }
        CompositionLocalProvider(LocalGlassBackdropHost provides localHost) {
            Box(Modifier.width(segmentWidth * labels.size).height(touchHeight)
                .then(trackModifier)
                .clip(RoundedCornerShape(50))
                .testTag("segment-track")
                .horizontalScroll(rememberScrollState())) {
                Box(Modifier.width(segmentWidth * labels.size).height(touchHeight)) {
                    // Only the plain track and labels are captured. The sibling lens never enters its source.
                    GlassBackdropSource(backdrop, Modifier.alpha(0f).clearAndSetSemantics {}) {
                        labelRow(true, trackModifier)
                    }
                    if (optical) labelRow(false, Modifier.selectableGroup().then(dragModifier)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (optical && previewIndex in labels.indices) {
                                val progress = press.value.coerceIn(0f, 1f)
                                val width = (segmentWidth - 6.dp).toPx() * (1f + 0.06f * progress)
                                val height = (trackHeight - 6.dp).toPx() * (1f + 0.12f * progress)
                                val index = if (rtl) labels.lastIndex - position.value else position.value
                                drawRoundRect(Color.Transparent,
                                    Offset(index * slotWidthPx + (slotWidthPx - width) / 2f,
                                        (size.height - height) / 2f),
                                    Size(width, height), CornerRadius(height / 2f),
                                    blendMode = BlendMode.Clear)
                            }
                        })
                    if (previewIndex in labels.indices) {
                        Box(Modifier.align(AbsoluteAlignment.CenterLeft)
                            .width(segmentWidth - 6.dp).height(trackHeight - 6.dp)
                            .graphicsLayer {
                                val index = if (rtl) labels.lastIndex - position.value else position.value
                                translationX = index * slotWidthPx + with(density) { 3.dp.toPx() }
                            }
                            .glassMaterial(
                                GlassMaterialRole.SelectionLens, RoundedCornerShape(50),
                                pressed = pressedIndex >= 0 || dragPosition != null,
                                sourceBackdrop = backdrop,
                                layerBlock = {
                                    val progress = press.value.coerceIn(0f, 1f)
                                    scaleX = 1f + 0.06f * progress
                                    scaleY = 1f + 0.12f * progress
                                },
                            )
                            .background(scheme.onSurface.copy(alpha = 0.06f *
                                (1f - press.value.coerceIn(0f, 1f))), RoundedCornerShape(50))
                            .testTag("segment-glass-lens")
                            .clearAndSetSemantics {})
                    }
                    if (!optical) labelRow(false, Modifier.selectableGroup().then(dragModifier))
                }
            }
        }
    }
}
