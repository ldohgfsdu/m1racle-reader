package com.m1racle.reader.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.m1racle.reader.ui.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// The recorded row is source-only. Its sibling lens cannot enter this capture.
internal val LocalDockSampling = staticCompositionLocalOf { false }
internal val LocalDockVisualTranslation = staticCompositionLocalOf<() -> Float> { { 0f } }
internal val LocalDockSampleScale = staticCompositionLocalOf<() -> Float> { { 1f } }

internal fun dockSlotAt(x: Float, width: Float, rtl: Boolean): Int {
    if (width <= 0f) return 0
    val physical = (x / (width / 5f)).toInt().coerceIn(0, 4)
    return if (rtl) 4 - physical else physical
}

internal fun dockLensStretch(velocity: Float): Float =
    (velocity / 40f).coerceIn(-1f, 1f)

// Adapted from Kyant LiquidBottomTabs / DampedDragAnimation (Apache-2.0).
// The lens exceeds the rail; the rail itself grows by 16dp and follows by up to 4dp.
internal fun dockLensPickupX(progress: Float): Float = 1f + (78f / 56f - 1f) * progress.coerceIn(-.3f, 1.3f)
internal fun dockLensPickupY(progress: Float): Float = dockLensPickupX(progress)
internal fun dockLensScaleX(strain: Float): Float = 1f / (1f - (strain * .75f).coerceIn(-.2f, .2f))
internal fun dockLensScaleY(strain: Float): Float = 1f - (strain * .25f).coerceIn(-.2f, .2f)
internal fun dockPanelOffset(drag: Float, width: Float, limit: Float): Float =
    if (width <= 0f) 0f else limit * drag.sign * EaseOut.transform((abs(drag) / width).coerceIn(0f, 1f))

@Composable
internal fun RowScope.OpticalDockTab(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sampling = LocalDockSampling.current
    Column(
        Modifier.weight(1f).fillMaxHeight().then(
            if (sampling) Modifier.clearAndSetSemantics {} else Modifier.selectable(
                selected = selected, role = Role.Tab, interactionSource = null,
                indication = null, onClick = onClick,
            )
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        content = content,
    )
}

/** Product-owned navigation interaction using the same renderer as PDF chrome.
 * Navigation owns selection. Pointer state is temporary and commits once on up.
 * The import slot is excluded from this gesture, so its own click remains native.
 */
@Composable
internal fun OpticalDockTabs(
    selectedSlot: Int,
    onTabSelected: (Int) -> Unit,
    containerHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val owner by rememberUpdatedState(selectedSlot)
    val commit by rememberUpdatedState(onTabSelected)
    val position = remember { Animatable(selectedSlot.toFloat()) }
    var settleFrom by remember { mutableStateOf<Float?>(null) }
    var held by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(selectedSlot.toFloat()) }
    var strain by remember { mutableFloatStateOf(0f) }
    var lastMovement by remember { mutableLongStateOf(0L) }
    val press = animateFloatAsState(if (held) 1f else 0f,
        spring(1f, 1000f, visibilityThreshold = .001f), label = "dock-lens-press")
    val pickupX = animateFloatAsState(if (held) 1f else 0f,
        spring(.6f, 250f, visibilityThreshold = .001f), label = "dock-pickup-x")
    val pickupY = animateFloatAsState(if (held) 1f else 0f,
        spring(.7f, 250f, visibilityThreshold = .001f), label = "dock-pickup-y")
    val panelReturn = remember { Animatable(0f) }
    LaunchedEffect(held) {
        if (!held) {
            panelReturn.animateTo(0f, spring(1f, 300f, .5f))
        }
    }
    val scope = rememberCoroutineScope()
    val shaderEnabled = LocalGlassCapability.current == GlassCapability.FULL_OPTICAL
    val highlight = remember(scope, shaderEnabled) {
        DockInteractiveHighlight(scope, enableShader = shaderEnabled)
    }
    val deformation = animateFloatAsState(strain,
        spring(.5f, 300f, visibilityThreshold = .001f), label = "dock-lens-velocity")
    LaunchedEffect(lastMovement, held) {
        if (held) { delay(80); strain = 0f }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val foreground = rememberLayerBackdrop()
    val dockSurface = rememberLayerBackdrop()
    val root = LocalGlassBackdropHost.current?.root
    val opticalSource = if (root != null) rememberCombinedBackdrop(dockSurface, foreground) else foreground
    val capsule = remember { RoundedCornerShape(50) }
    LaunchedEffect(selectedSlot, held, settleFrom) {
        if (!held) {
            settleFrom?.let { position.snapTo(it); settleFrom = null }
            position.animateTo(selectedSlot.toFloat(), spring(.8f, 450f))
        }
    }
    BoxWithConstraints(modifier.height(containerHeight).pointerInput(rtl) {
        awaitEachGesture {
            var releasedSlot: Int? = null
            try {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val width = size.width.toFloat()
                val startSlot = dockSlotAt(down.position.x, width, rtl)
                if (startSlot != 2 && !down.isConsumed) {
                    down.consume()
                    held = true
                    preview = startSlot.toFloat()
                    scope.launch { panelReturn.snapTo(0f) }
                    highlight.press(down.position)
                    var previous = down
                    var tracking = true
                    while (tracking) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1 || change.isConsumed) {
                            event.changes.forEach { it.consume() }
                            tracking = false
                        } else {
                            val direction = if (rtl) -1f else 1f
                            val delta = (change.position.x - down.position.x) / (width / 5f)
                            preview = (startSlot + delta * direction).coerceIn(0f, 4f)
                            scope.launch { panelReturn.snapTo(change.position.x - down.position.x) }
                            highlight.move(change.position)
                            val seconds = ((change.uptimeMillis - previous.uptimeMillis).coerceAtLeast(1)) / 1000f
                            strain = dockLensStretch((change.position.x - previous.position.x) / (width / 5f) / seconds)
                            lastMovement = change.uptimeMillis
                            change.consume()
                            previous = change
                            if (!change.pressed) {
                                releasedSlot = preview.roundToInt().takeUnless { it == 2 }
                                tracking = false
                            }
                        }
                    }
                }
            } finally {
                if (held) {
                    settleFrom = preview
                    held = false
                    strain = 0f
                    highlight.release()
                }
            }
            releasedSlot?.takeUnless { it == owner }?.let(commit)
        }
    }) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val maxFollow = with(density) { 4.dp.toPx() }
        val growth = with(density) { 16.dp.toPx() }
        val panelOffset = remember(widthPx, maxFollow) { derivedStateOf {
            dockPanelOffset(panelReturn.value, widthPx, maxFollow)
        } }
        val translation = remember(panelOffset) { { panelOffset.value } }
        val sampledScale = remember(press) { { 1f + .2f * press.value } }
        CompositionLocalProvider(LocalDockVisualTranslation provides translation,
            LocalDockSampleScale provides sampledScale) {
        // Export only the processed static surface, before drawing any consumers.
        // The library export excludes drawContent, and this box has no children.
        Box(Modifier.fillMaxSize().glassMaterial(
            GlassMaterialRole.Dock, capsule, exportBackdrop = dockSurface,
            pressed = held, interactionProgress = press,
            layerBlock = remember(panelOffset, growth, press) { {
                translationX = panelOffset.value
                val scale = 1f + growth / size.width * press.value
                scaleX = scale
                scaleY = scale
            } },
        ).testTag("dock-material").clip(capsule).then(highlight.modifier))
        // Only foreground is captured here; the dock and lens are sibling consumers.
        val sourceOnlyModifier = Modifier.fillMaxSize().clearAndSetSemantics {}.alpha(0f)
        GlassBackdropSource(foreground, sourceOnlyModifier) {
            CompositionLocalProvider(LocalDockSampling provides true) {
                Row(Modifier.fillMaxSize(), content = content)
            }
        }
        Row(Modifier.fillMaxSize(), content = content)
        val slotWidth = maxWidth / 5
        Box(Modifier
            .offset {
                val logical = if (held) preview else settleFrom ?: position.value
                // offset is relative to TopStart; Compose mirrors it in RTL.
                IntOffset((logical * slotWidth.toPx()).roundToInt(), 4.dp.roundToPx())
            }
            .size(slotWidth, containerHeight - 8.dp)
            .glassMaterial(GlassMaterialRole.SelectionLens, capsule,
                pressed = held, sourceBackdrop = opticalSource, interactionProgress = press,
                layerBlock = remember(pickupX, pickupY, deformation, panelOffset) {
                    {
                        translationX = panelOffset.value
                        scaleX = dockLensPickupX(pickupX.value) * dockLensScaleX(deformation.value)
                        scaleY = dockLensPickupY(pickupY.value) * dockLensScaleY(deformation.value)
                    }
                })
            .testTag("dock-selection").clearAndSetSemantics {})
        }
    }
}


