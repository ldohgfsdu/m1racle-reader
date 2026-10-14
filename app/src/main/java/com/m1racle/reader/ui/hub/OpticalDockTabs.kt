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
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.m1racle.reader.ui.theme.DockSelectedLight
import com.m1racle.reader.ui.theme.DockSelectedDark
import com.m1racle.reader.ui.material.*
import com.m1racle.reader.ui.home.HomeDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val LocalDockVisualTranslation = staticCompositionLocalOf<() -> Float> { { 0f } }

internal fun dockSlotAt(x: Float, width: Float, rtl: Boolean): Int {
    if (width <= 0f) return 0
    val physical = (x / (width / 5f)).toInt().coerceIn(0, 4)
    return if (rtl) 4 - physical else physical
}

internal fun dockLensStretch(velocity: Float): Float =
    (velocity / 20f).coerceIn(-1f, 1f)

// Adapted from Kyant LiquidBottomTabs / DampedDragAnimation (Apache-2.0).
// The lens exceeds the rail; the rail itself grows by 16dp and follows by up to 4dp.
internal fun dockLensPickupX(progress: Float): Float = 1f + .5f * progress.coerceIn(-.3f, 1.3f)
internal fun dockLensPickupY(progress: Float): Float = 1f + (68f / 48f - 1f) * progress.coerceIn(-.3f, 1.3f)
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
    Column(
        Modifier.weight(1f).fillMaxHeight().selectable(
                selected = selected, role = Role.Tab, interactionSource = null,
                indication = null, onClick = onClick,
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
    content: @Composable RowScope.(tabBackdrops: List<LayerBackdrop>, importBackdrop: LayerBackdrop) -> Unit,
) {
    val owner by rememberUpdatedState(selectedSlot)
    val commit by rememberUpdatedState(onTabSelected)
    val position = remember { Animatable(selectedSlot.toFloat()) }
    var settleFrom by remember { mutableStateOf<Float?>(null) }
    var held by remember { mutableStateOf(false) }
    var preview by remember { mutableFloatStateOf(selectedSlot.toFloat()) }
    var pickupOrigin by remember { mutableFloatStateOf(selectedSlot.toFloat()) }
    var strain by remember { mutableFloatStateOf(0f) }
    var lastMovement by remember { mutableLongStateOf(0L) }
    val press = animateFloatAsState(if (held) 1f else 0f,
        spring(1f, 1000f, visibilityThreshold = .001f), label = "dock-lens-press")
    // A tap picks up the current lens and carries it to the pressed tab.
    // Once pickup finishes, dragging follows the finger without extra lag.
    val logicalPosition = remember { derivedStateOf {
        if (held) pickupOrigin + (preview - pickupOrigin) * press.value.coerceIn(0f, 1f)
        else settleFrom ?: position.value
    } }
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
    val deformation = animateFloatAsState(strain,
        spring(.5f, 600f, visibilityThreshold = .001f), label = "dock-lens-velocity")
    LaunchedEffect(lastMovement, held) {
        if (held) { delay(80); strain = 0f }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dockBackdrop = rememberLayerBackdrop()
    // Record each visible tab once. Tint those same pixels in the lens's
    // optical input, so a partially covered icon has two colors. The import
    // action is not included and retains its own blue fill and white plus.
    val tabBackdrops = listOf(rememberLayerBackdrop(), rememberLayerBackdrop(),
        rememberLayerBackdrop(), rememberLayerBackdrop())
    val importBackdrop = rememberLayerBackdrop()
    val accent = if (LocalGlassEnvironment.current.isDark) DockSelectedDark else DockSelectedLight
    val tintPaint = remember(accent) { Paint().apply { colorFilter = ColorFilter.tint(accent) } }
    val root = LocalGlassBackdropHost.current?.root
    // Capture the visible dock once. The lens is a sibling and can never be
    // recorded into its own source; there is no substitute rail or foreground.
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
                val inset = HomeDesignTokens.DockContentPadding.toPx()
                val width = (size.width - 2f * inset).coerceAtLeast(1f)
                val startSlot = dockSlotAt(down.position.x - inset, width, rtl)
                if (startSlot != 2 && !down.isConsumed) {
                    down.consume()
                    pickupOrigin = logicalPosition.value
                    held = true
                    preview = startSlot.toFloat()
                    scope.launch { panelReturn.snapTo(0f) }
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
                    // Continue from the rendered position even after a short
                    // tap, rather than jumping to an unfinished pickup target.
                    settleFrom = logicalPosition.value
                    held = false
                    strain = 0f
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
        val dockWidth = maxWidth
        val railHeightPx = with(density) { containerHeight.roundToPx().toFloat() }
        val lensScale = remember(pickupX, pickupY, deformation) { derivedStateOf {
            Offset(dockLensPickupX(pickupX.value) * dockLensScaleX(deformation.value),
                dockLensPickupY(pickupY.value) * dockLensScaleY(deformation.value))
        } }
        CompositionLocalProvider(LocalDockVisualTranslation provides translation) {
        // Resolve under the page host, before the capture masks it. The visible
        // dock samples only the page, never the layer that records the dock.
        val dockMaterial = Modifier.fillMaxSize().glassMaterial(
            GlassMaterialRole.Dock, capsule,
            pressed = held, interactionProgress = press, sourceBackdrop = root,
            layerBlock = remember(panelOffset, growth, press) { {
                translationX = panelOffset.value
                val scale = 1f + growth / size.width * press.value
                scaleX = scale
                scaleY = scale
            } },
        )
        // Include the actual rail's small outward pickup and shadow in the
        // recorded bounds. The visible dock and its hitboxes keep their size.
        GlassBackdropSource(dockBackdrop,
            Modifier.requiredSize(dockWidth + 32.dp, containerHeight + 32.dp)) {
            Box(Modifier.align(Alignment.Center).size(dockWidth, containerHeight)) {
                Box(dockMaterial.testTag("dock-material").clip(capsule))
            }
        }
        // Record only the visible material here. Foreground pixels have their
        // own captures, so correcting the rail's optical size cannot produce
        // smaller duplicate icons underneath the tinted foreground.
        Row(Modifier.fillMaxSize().padding(horizontal = HomeDesignTokens.DockContentPadding)) {
            content(tabBackdrops, importBackdrop)
        }
        val slotWidth = (maxWidth - HomeDesignTokens.DockContentPadding * 2) / 5
        val slotWidthPx = with(density) { slotWidth.toPx() }
        val lensOffset = remember(slotWidthPx, density) { derivedStateOf {
            val logical = logicalPosition.value
            with(density) { HomeDesignTokens.DockContentPadding.toPx() } + logical * slotWidthPx
        } }
        // Cancel the rail's pickup expansion only in the lens's optical image.
        // This is a transform of the actual captured material, not another rail.
        // The pivot is the rail center expressed in the inverse-transformed
        // lens coordinates; it works at either end and throughout a drag.
        val sampleRail: DrawScope.(DrawScope.() -> Unit) -> Unit =
            remember(widthPx, railHeightPx, slotWidthPx, growth, press, lensOffset, lensScale, rtl) { { drawSource ->
                val physicalLeft = if (rtl) widthPx - lensOffset.value - size.width else lensOffset.value
                val pivot = Offset(center.x + (widthPx / 2f - physicalLeft - center.x) / lensScale.value.x,
                    center.y)
                val undoPickup = 1f / (1f + growth / widthPx * press.value)
                // Preserve the full pill's proportions. Anchor its projection
                // at the corresponding rail position, including either cap;
                // shrinking around the lens center pushes end caps too far in.
                val fraction = ((lensOffset.value - HomeDesignTokens.DockContentPadding.toPx()) /
                    (4f * slotWidthPx)).coerceIn(0f, 1f)
                val anchorX = widthPx * if (rtl) 1f - fraction else fraction
                val projectionPivot = Offset(center.x +
                    (anchorX - physicalLeft - center.x) / lensScale.value.x, center.y)
                // Stronger edge refraction needs a distinct inner contour.
                // Fit both axes uniformly; do not squash the captured cap.
                val opticalFit = 1f + (size.height / railHeightPx - 1f) * press.value
                withTransform({
                    scale(undoPickup, undoPickup, pivot)
                    // Minify the actual pill uniformly around its projection.
                    // A Y-only fit squashes its circular ends into ellipses.
                    scale(opticalFit, opticalFit, projectionPivot)
                }) { drawSource() }
            } }
        val sampledRail = rememberBackdrop(dockBackdrop, sampleRail)
        val tintedTabs = tabBackdrops.mapIndexed { index, source ->
            val slot = dockSlotForDestination(index)
            val tintPixels: DrawScope.(DrawScope.() -> Unit) -> Unit =
                remember(tintPaint, slot, slotWidthPx, widthPx, press, lensOffset, lensScale, rtl) { { drawSource ->
                    val physicalLeft = if (rtl) widthPx - lensOffset.value - size.width else lensOffset.value
                    val logicalCenter = with(density) { HomeDesignTokens.DockContentPadding.toPx() } +
                        (slot + .5f) * slotWidthPx
                    val tabCenter = if (rtl) widthPx - logicalCenter else logicalCenter
                    val pivot = Offset(center.x + (tabCenter - physicalLeft - center.x) / lensScale.value.x,
                        center.y)
                    // Enlarge the same captured foreground around its own
                    // center. The optical lens clips the enlarged, tinted
                    // pixels while the actual row keeps its layout and size.
                    val scale = 1f + .2f * press.value
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), tintPaint)
                    try {
                        withTransform({ scale(scale, scale, pivot) }) { drawSource() }
                    } finally { drawContext.canvas.restore() }
                } }
            rememberBackdrop(source, tintPixels)
        }
        val opticalSource = rememberCombinedBackdrop(*
            (listOfNotNull(root, sampledRail, importBackdrop) + tintedTabs).toTypedArray())
        Box(Modifier.align(Alignment.CenterStart)
            .offset {
                // offset is relative to TopStart; Compose mirrors it in RTL.
                IntOffset(lensOffset.value.roundToInt(), 0)
            }
            // Coolapk's measured 56dp rail and 48-to-68dp pickup, rather than
            // RiseDiary's larger 64dp rail and 56-to-78dp lens. Keep the real
            // captured contour visible through the smaller refracting rim.
            .size(slotWidth, containerHeight - 8.dp)
            .glassMaterial(GlassMaterialRole.DockLens, capsule,
                pressed = held, sourceBackdrop = opticalSource, interactionProgress = press,
                layerBlock = remember(lensScale, panelOffset, lensOffset, rtl, railHeightPx) {
                    {
                        // Layout offsets use whole pixels. Preserve the remainder
                        // in the sampling-aware layer for continuous finger tracking.
                        val x = lensOffset.value
                        val remainder = (x - x.roundToInt()) * if (rtl) -1f else 1f
                        translationX = panelOffset.value + remainder
                        // Center alignment rounds to an integer pixel. Restore
                        // its half-pixel remainder before optical sampling.
                        val verticalInset = (railHeightPx - size.height) / 2f
                        translationY = verticalInset - verticalInset.roundToInt()
                        scaleX = lensScale.value.x
                        scaleY = lensScale.value.y
                    }
                })
            .testTag("dock-selection").clearAndSetSemantics {})
        }
    }
}


