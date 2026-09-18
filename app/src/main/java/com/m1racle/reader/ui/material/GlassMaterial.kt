package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/**
 * Semantic material roles. Components own geometry; the engine owns every
 * visual material parameter (tint, alpha, blur, lens, highlight and shadow).
 */
enum class GlassMaterialRole {
    Control,
    Dock,
    Capsule,
    Menu,
    ExpandedMenu,
    SettingsCard,
    Dialog,
    /** Markdown top chrome. Legacy 16dp + 0.62 plate; optical rewrite deferred. */
    ProgressiveChrome,
    /** Settings comparison only (HyperChanger capsule reference). */
    PreviewCapsule,
}

internal enum class GlassBackdropLayer {
    Root,
    RootAndMenu,
}

/** Rendering activity used to keep idle material resources stable. */
internal enum class GlassRenderState {
    IDLE,
    INTERACTION,
}

/** Fully resolved, theme-aware recipe consumed by both Backdrop and plate paths. */
@Immutable
class GlassMaterialSpec internal constructor(
    val family: GlassOpticalFamily,
    val surfaceColor: Color,
    val blur: Dp,
    val lensEnabled: Boolean,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val shadow: Dp,
    val shadowColor: Color,
    val highlightWidth: Dp,
    val highlightColor: Color,
    val contentDividerAlpha: Float,
    val contentMutedAlpha: Float,
    val foreground: GlassForegroundPolicy,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val depthEffect: Boolean,
    val scale: Float,
    val contentPressedOverlayAlpha: Float,
    val vibrancy: Boolean,
    val chromaticAberration: Boolean,
    val innerShadow: Dp,
    val environment: GlassEnvironment,
    val capability: GlassCapability,
    val interaction: GlassInteractionState,
    internal val renderState: GlassRenderState,
    internal val backdropLayer: GlassBackdropLayer,
    internal val exportsForSettingsCard: Boolean,
)

@Immutable
private data class GlassRecipe(
    val family: GlassOpticalFamily,
    val lightTint: Color = Color(0xFFFAFAFA),
    val lightShadowAlpha: Float = 0.10f,
    val darkShadowAlpha: Float = 0.46f,
    // Kyant LiquidBottomTabs / RiseDiary night plate. Neutral near-black.
    val darkTint: Color = Color(0xFF121212),
    val lightBrightness: Float = 0f,
    val darkBrightness: Float = 0f,
    val saturation: Float = 1f,
    val lightSurfaceAlpha: Float,
    val darkSurfaceAlpha: Float,
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val shadow: Dp,
    val highlightWidth: Dp,
    val lightHighlightAlpha: Float,
    val darkHighlightAlpha: Float,
    val contentDividerAlpha: Float = 0.06f,
    val lightContentMutedAlpha: Float = 0.58f,
    val darkContentMutedAlpha: Float = 0.75f,
    val depthEffect: Boolean,
    val pressedScale: Float = 0.965f,
    val vibrancy: Boolean = false,
    val chromaticAberration: Boolean = false,
    val innerShadow: Dp = 0.dp,
    val pressedLensHeight: Dp? = null,
    val pressedLensAmount: Dp? = null,
    val pressedInnerShadow: Dp? = null,
    val pressedSurfaceAlpha: Float? = null,
    val lensEnabled: Boolean = false,
    val backdropLayer: GlassBackdropLayer = GlassBackdropLayer.Root,
    val exportsForSettingsCard: Boolean = false,
)

/**
 * The sole recipe registry for M1racleReader glass.
 *
 * Floating surfaces share the approved thick frost. Settings cards sample
 * above their parent menu. Optical families share physical direction; roles
 * retain their own density and geometry-independent endpoints.
 * Dialogs use the same family but render as a soft plate because they live in
 * a separate platform window and cannot safely sample the app backdrop.
 */
object GlassMaterialEngine {
    private const val PressedSurfaceAlphaBoost = 0.04f
    private const val PressedHighlightAlphaBoost = 0.10f
    private const val LightPressedContentOverlayAlpha = 0.04f
    private const val DarkPressedContentOverlayAlpha = 0.05f

    /** Shared physical response for material and material-content presses. */
    internal val PressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.001f,
    )

    // Shared thick frost. Batch 1 locked Content blur at 26dp (18/22/26 all
    // still leaked glyphs at 0.86 tint). Tint is the next single variable.
    private val regularFrost = GlassRecipe(
        family = GlassOpticalFamily.CONTENT,
        lightTint = Color(0xFFF5F5F5),
        lightShadowAlpha = 0.14f,
        darkShadowAlpha = 0.28f,
        darkTint = Color.Black,
        lightSurfaceAlpha = 0.62f,
        darkSurfaceAlpha = 0.58f,
        blur = 26.dp,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        shadow = 6.dp,
        highlightWidth = 0.dp,
        lightHighlightAlpha = 0f,
        darkHighlightAlpha = 0f,
        depthEffect = false,
        vibrancy = true,
        lensEnabled = false,
    )

    // Controls over document pages must keep their own labels readable.
    private val readerOverlayFrost = regularFrost.copy(
        lightSurfaceAlpha = 0.45f,
        darkSurfaceAlpha = 0.80f,
    )

    // Large reader panels stay denser than compact overlay chrome.
    private val panelFrost = regularFrost.copy(
        family = GlassOpticalFamily.PANEL,
        lightSurfaceAlpha = 0.90f,
        darkSurfaceAlpha = 0.86f,
        blur = 26.dp,
    )

    // Production Dock is the hub L3 plate (opaque paper/ink + 2.dp elevation).
    private val appOverlayPlate = GlassRecipe(
        family = GlassOpticalFamily.FALLBACK,
        lightTint = Color(0xFFFFFFFF),
        darkTint = Color(0xFF171717),
        lightShadowAlpha = 0.10f,
        darkShadowAlpha = 0.20f,
        lightSurfaceAlpha = 0.98f,
        darkSurfaceAlpha = 0.98f,
        blur = 0.dp,
        lensHeight = 0.dp,
        lensAmount = 0.dp,
        shadow = 2.dp,
        highlightWidth = 0.dp,
        lightHighlightAlpha = 0f,
        darkHighlightAlpha = 0f,
        depthEffect = false,
        pressedScale = 1f,
        vibrancy = false,
        lensEnabled = false,
    )

    // Markdown top chrome legacy equivalent. R36.x OPTICAL_REFINEMENT_PENDING.
    private val progressiveLegacy = regularFrost.copy(
        lightSurfaceAlpha = 0.62f,
        darkSurfaceAlpha = 0.62f,
        blur = 16.dp,
        shadow = 0.dp,
        vibrancy = false,
    )

    private val previewCapsule = GlassRecipe(
        family = GlassOpticalFamily.INTERACTIVE,
        lightTint = Color(0xFFF5F5F5),
        darkTint = Color.Black,
        lightSurfaceAlpha = 0.42f,
        darkSurfaceAlpha = 0.30f,
        blur = 4.32.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        shadow = 6.dp,
        highlightWidth = 0.5.dp,
        lightHighlightAlpha = 0.96f,
        darkHighlightAlpha = 0.78f,
        depthEffect = true,
        vibrancy = true,
        lensEnabled = true,
        chromaticAberration = false,
    )

    private val fallbackPlate = GlassRecipe(
        family = GlassOpticalFamily.FALLBACK,
        // Dialogs live in a separate platform window, so their soft plate
        // cannot sample the reader backdrop. Keep a small amount of
        // translucency for the glass edge while making body copy beneath
        // the dialog visually unreadable.
        lightSurfaceAlpha = 0.97f,
        darkSurfaceAlpha = 0.97f,
        blur = 18.dp,
        lensHeight = regularFrost.lensHeight,
        lensAmount = regularFrost.lensAmount,
        shadow = 18.dp,
        highlightWidth = 0.5.dp,
        lightHighlightAlpha = 0.30f,
        darkHighlightAlpha = 0.38f,
        depthEffect = false,
        lensEnabled = false,
    )

    private val recipes = mapOf(
        GlassMaterialRole.Dock to appOverlayPlate,
        GlassMaterialRole.Control to readerOverlayFrost.copy(
            family = GlassOpticalFamily.INTERACTIVE,
        ),
        GlassMaterialRole.Capsule to readerOverlayFrost,
        GlassMaterialRole.Menu to readerOverlayFrost.copy(
            exportsForSettingsCard = true,
        ),
        GlassMaterialRole.ExpandedMenu to panelFrost.copy(
            family = GlassOpticalFamily.CONTENT,
            exportsForSettingsCard = true,
        ),
        GlassMaterialRole.SettingsCard to panelFrost.copy(
            backdropLayer = GlassBackdropLayer.RootAndMenu,
        ),
        GlassMaterialRole.Dialog to fallbackPlate.copy(family = GlassOpticalFamily.PANEL),
        GlassMaterialRole.ProgressiveChrome to progressiveLegacy,
        GlassMaterialRole.PreviewCapsule to previewCapsule,
    )

    fun rolesWithConsumers(): Set<GlassMaterialRole> = recipes.keys

    fun previewRole(preset: GlassPreviewPreset): GlassMaterialRole = when (preset) {
        GlassPreviewPreset.CurrentThick -> GlassMaterialRole.Control
        GlassPreviewPreset.ProgressiveLegacy -> GlassMaterialRole.ProgressiveChrome
        GlassPreviewPreset.CapsuleReference -> GlassMaterialRole.PreviewCapsule
        else -> GlassMaterialRole.Capsule
    }

    fun resolve(
        role: GlassMaterialRole,
        dark: Boolean,
        pressed: Boolean = false,
        interaction: Boolean = false,
    ): GlassMaterialSpec = resolve(
        role = role,
        environment = GlassEnvironment.app(dark),
        capability = GlassCapability.FULL_OPTICAL,
        interaction = when {
            pressed -> GlassInteractionState.PRESSED
            interaction -> GlassInteractionState.EXPANDED
            else -> GlassInteractionState.IDLE
        },
    )

    fun resolve(
        role: GlassMaterialRole,
        environment: GlassEnvironment,
        capability: GlassCapability = GlassCapability.FULL_OPTICAL,
        interaction: GlassInteractionState = GlassInteractionState.IDLE,
        previewPreset: GlassPreviewPreset? = null,
    ): GlassMaterialSpec {
        val baseRecipe = checkNotNull(recipes[role]) { "Missing glass recipe for $role" }
        // Explicit settings-only candidates; production callers leave this null.
        val tint = when (previewPreset) {
            GlassPreviewPreset.Tint86 -> .86f
            GlassPreviewPreset.Tint80 -> .80f
            GlassPreviewPreset.Tint70 -> .70f
            GlassPreviewPreset.Tint60 -> .60f
            GlassPreviewPreset.Tint50 -> .50f
            GlassPreviewPreset.Tint45, GlassPreviewPreset.Blur32, GlassPreviewPreset.Blur40 -> .45f
            GlassPreviewPreset.Tint35 -> .35f
            else -> null
        }
        val roleRecipe = if (tint == null) baseRecipe else baseRecipe.copy(
            blur = when (previewPreset) {
                GlassPreviewPreset.Blur32 -> 32.dp
                GlassPreviewPreset.Blur40 -> 40.dp
                else -> 26.dp
            },
            lightSurfaceAlpha = tint,
            darkSurfaceAlpha = tint,
        )
        val recipe = if (capability == GlassCapability.SOLID_FALLBACK &&
            roleRecipe.family != GlassOpticalFamily.FALLBACK
        ) {
            val plate = fallbackPlate
            roleRecipe.copy(
                family = GlassOpticalFamily.FALLBACK,
                lightTint = plate.lightTint,
                darkTint = plate.darkTint,
                lightSurfaceAlpha = plate.lightSurfaceAlpha,
                darkSurfaceAlpha = plate.darkSurfaceAlpha,
                highlightWidth = plate.highlightWidth,
                lightHighlightAlpha = plate.lightHighlightAlpha,
                darkHighlightAlpha = plate.darkHighlightAlpha,
            )
        } else {
            roleRecipe
        }
        val dark = environment.isDark
        val pressed = interaction == GlassInteractionState.PRESSED ||
            interaction == GlassInteractionState.DRAGGING
        val idleSurfaceAlpha = if (dark) recipe.darkSurfaceAlpha else recipe.lightSurfaceAlpha
        val surfaceAlpha = if (pressed) {
            recipe.pressedSurfaceAlpha ?: (idleSurfaceAlpha + PressedSurfaceAlphaBoost)
        } else {
            idleSurfaceAlpha
        }
        val highlightBoost = if (pressed && recipe.highlightWidth > 0.dp) PressedHighlightAlphaBoost else 0f
        val highlightAlpha = (
            if (dark) recipe.darkHighlightAlpha else recipe.lightHighlightAlpha
            ) + highlightBoost
        val brightness = if (dark) recipe.darkBrightness else recipe.lightBrightness
        val muted = if (dark) recipe.darkContentMutedAlpha else recipe.lightContentMutedAlpha
        val lensOn = recipe.lensEnabled && capability == GlassCapability.FULL_OPTICAL
        val blur = if (capability == GlassCapability.SOLID_FALLBACK) 0.dp else recipe.blur
        return GlassMaterialSpec(
            family = recipe.family,
            surfaceColor = (if (dark) recipe.darkTint else recipe.lightTint).copy(
                alpha = surfaceAlpha.coerceIn(0f, 0.98f),
            ),
            blur = blur,
            lensEnabled = lensOn,
            lensHeight = if (lensOn) {
                if (pressed) recipe.pressedLensHeight ?: recipe.lensHeight else recipe.lensHeight
            } else {
                0.dp
            },
            lensAmount = if (lensOn) {
                if (pressed) recipe.pressedLensAmount ?: recipe.lensAmount else recipe.lensAmount
            } else {
                0.dp
            },
            shadow = recipe.shadow,
            shadowColor = Color.Black.copy(
                alpha = if (dark) recipe.darkShadowAlpha else recipe.lightShadowAlpha,
            ),
            highlightWidth = recipe.highlightWidth,
            highlightColor = Color.White.copy(alpha = highlightAlpha.coerceIn(0f, 1f)),
            contentDividerAlpha = recipe.contentDividerAlpha,
            contentMutedAlpha = muted,
            foreground = GlassForegroundPolicy(
                contentAlpha = 1f,
                mutedAlpha = muted,
            ),
            brightness = brightness,
            contrast = 1f,
            saturation = recipe.saturation,
            depthEffect = recipe.depthEffect && lensOn,
            scale = if (pressed) recipe.pressedScale else 1f,
            contentPressedOverlayAlpha = if (dark) {
                DarkPressedContentOverlayAlpha
            } else {
                LightPressedContentOverlayAlpha
            },
            vibrancy = recipe.vibrancy && capability != GlassCapability.SOLID_FALLBACK,
            chromaticAberration = recipe.chromaticAberration && lensOn,
            innerShadow = if (pressed) {
                recipe.pressedInnerShadow ?: recipe.innerShadow
            } else {
                recipe.innerShadow
            },
            environment = environment,
            capability = capability,
            interaction = interaction,
            renderState = if (interaction == GlassInteractionState.IDLE ||
                interaction == GlassInteractionState.HOVER
            ) {
                GlassRenderState.IDLE
            } else {
                GlassRenderState.INTERACTION
            },
            backdropLayer = recipe.backdropLayer,
            exportsForSettingsCard = recipe.exportsForSettingsCard,
        )
    }
}

/** Theme-aware secondary content alpha from the selected material recipe. */
@Composable
fun glassMutedContentAlpha(role: GlassMaterialRole): Float = GlassMaterialEngine.resolve(
    role = role,
    environment = LocalGlassEnvironment.current,
).contentMutedAlpha

/** Theme-aware restrained overlay used by rows inside a glass surface. */
@Composable
fun glassPressedContentOverlayAlpha(role: GlassMaterialRole): Float =
    GlassMaterialEngine.resolve(
        role = role,
        environment = LocalGlassEnvironment.current,
    ).contentPressedOverlayAlpha

/** Root content capture plus the exported menu material used by stacked cards. */
@Immutable
data class GlassBackdropHost(
    val root: LayerBackdrop,
    val menuExport: LayerBackdrop,
)

val LocalGlassBackdropHost = staticCompositionLocalOf<GlassBackdropHost?> { null }

/**
 * The only rendering entry for glass surfaces.
 *
 * [sampleBackdrop] must be false in Popup/Dialog windows and for surfaces
 * inside their own captured layer; those cases use the same resolved recipe
 * as a translucent plate with the same highlight and shadow.
 */
@Composable
fun Modifier.glassMaterial(
    role: GlassMaterialRole,
    shape: Shape,
    sampleBackdrop: Boolean = true,
    pressed: Boolean = false,
    interaction: Boolean = false,
    sourceBackdrop: Backdrop? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    surfaceExport: LayerBackdrop? = null,
    previewPreset: GlassPreviewPreset? = null,
): Modifier {
    val environment = LocalGlassEnvironment.current
    val host = LocalGlassBackdropHost.current
    val capability = GlassCapabilityProbe.forSurface(
        device = LocalGlassCapability.current,
        sampleBackdrop = sampleBackdrop,
        hasBackdrop = host != null,
        supportsLens = supportsGlassLens(shape),
    )
    val interactionState = if (interaction) {
        GlassInteractionState.EXPANDED
    } else {
        GlassInteractionState.IDLE
    }
    // Stable endpoint specs prevent unrelated parent recompositions from
    // replacing the renderer's effect lambdas and invalidating its draw cache.
    val idleSpec = remember(role, environment, capability, interactionState, previewPreset) {
        GlassMaterialEngine.resolve(
            role = role,
            environment = environment,
            capability = capability,
            interaction = interactionState,
            previewPreset = previewPreset,
        )
    }
    val pressedSpec = remember(role, environment, capability, previewPreset) {
        GlassMaterialEngine.resolve(
            role = role,
            environment = environment,
            capability = capability,
            interaction = GlassInteractionState.PRESSED,
            previewPreset = previewPreset,
        )
    }
    val pressProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = GlassMaterialEngine.PressSpring,
        label = "glassMaterialPress",
    )
    // Reading the progress here keeps the temporary scale layer alive through
    // the release spring. It does not replace the remembered effect modifier.
    val pressLayerActive = pressed || pressProgress.value > 0.001f
    val canSample = sampleBackdrop &&
        host != null &&
        capability != GlassCapability.SOLID_FALLBACK
    if (!canSample) {
        return then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier).then(
            softGlassPlate(
                shape = shape,
                idleSpec = idleSpec,
                pressedSpec = pressedSpec,
                pressProgress = pressProgress,
                pressLayerActive = pressLayerActive,
            ),
        )
    }
    return drawGlassMaterial(
        modifier = this,
        host = host,
        shape = shape,
        idleSpec = idleSpec,
        pressedSpec = pressedSpec,
        pressProgress = pressProgress,
        pressLayerActive = pressLayerActive,
        sourceBackdrop = sourceBackdrop,
        layerBlock = layerBlock,
        surfaceExport = surfaceExport,
    )
}

/** Both sampled glass and popup plates use a direct shadow, not elevation lighting.
 * Draw before the plate's clip so the complete rounded silhouette can cast outward.
 */
internal fun Modifier.frostShadow(shape: Shape, spec: GlassMaterialSpec): Modifier =
    dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = spec.shadow,
            color = spec.shadowColor,
            offset = DpOffset(0.dp, spec.shadow / 3f),
        ),
    )

@Composable
private fun softGlassPlate(
    shape: Shape,
    idleSpec: GlassMaterialSpec,
    pressedSpec: GlassMaterialSpec,
    pressProgress: State<Float>,
    pressLayerActive: Boolean,
): Modifier = remember(
    shape,
    idleSpec,
    pressedSpec,
    pressProgress,
    pressLayerActive,
) {
    val scaleModifier = if (pressLayerActive) {
        Modifier.graphicsLayer {
            val progress = pressProgress.value.coerceIn(0f, 1f)
            val scale = idleSpec.scale + (pressedSpec.scale - idleSpec.scale) * progress
            scaleX = scale
            scaleY = scale
        }
    } else {
        Modifier
    }
    scaleModifier
        .frostShadow(shape, idleSpec)
        .clip(shape)
        .drawWithContent {
            val progress = pressProgress.value.coerceIn(0f, 1f)
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = lerp(idleSpec.surfaceColor, pressedSpec.surfaceColor, progress),
            )
            drawContent()
            // A zero-width Stroke means a device hairline, not an absent border.
            if (idleSpec.highlightWidth > 0.dp) {
                drawOutline(
                    outline = outline,
                    color = lerp(idleSpec.highlightColor, pressedSpec.highlightColor, progress),
                    style = Stroke(width = idleSpec.highlightWidth.toPx()),
                )
            }
        }
}

@Composable
private fun drawGlassMaterial(
    modifier: Modifier,
    host: GlassBackdropHost,
    shape: Shape,
    idleSpec: GlassMaterialSpec,
    pressedSpec: GlassMaterialSpec,
    pressProgress: State<Float>,
    pressLayerActive: Boolean,
    sourceBackdrop: Backdrop? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    surfaceExport: LayerBackdrop? = null,
): Modifier {
    val density = LocalDensity.current
    // An explicit processed source replaces the page source. Adding raw page
    // pixels underneath it reintroduces sharp detail through translucent glass.
    val backdrop = sourceBackdrop ?: when (idleSpec.backdropLayer) {
        GlassBackdropLayer.Root -> host.root
        GlassBackdropLayer.RootAndMenu -> rememberCombinedBackdrop(host.root, host.menuExport)
    }
    val exportedBackdrop = surfaceExport ?: if (idleSpec.exportsForSettingsCard) host.menuExport else null
    val blurPx = with(density) { idleSpec.blur.toPx() }
    val lensHeightPx = with(density) { idleSpec.lensHeight.toPx() }
    val lensAmountPx = with(density) { idleSpec.lensAmount.toPx() }
    val pressedLensHeightPx = with(density) { pressedSpec.lensHeight.toPx() }
    val pressedLensAmountPx = with(density) { pressedSpec.lensAmount.toPx() }
    val lensEnabled = idleSpec.lensEnabled &&
        supportsGlassLens(shape) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val innerShadowEnabled = idleSpec.innerShadow > 0.dp || pressedSpec.innerShadow > 0.dp

    // drawBackdrop owns one GraphicsLayer per modifier node and releases it on
    // detach. Remembering the complete modifier keeps that node and its
    // RenderEffect alive across idle recompositions. A real material/state or
    // geometry change updates the node once; detach releases its owned layers.
    val resourceModifier = remember(
        backdrop,
        exportedBackdrop,
        shape,
        idleSpec,
        pressedSpec,
        pressProgress,
        pressLayerActive,
        blurPx,
        lensHeightPx,
        lensAmountPx,
        pressedLensHeightPx,
        pressedLensAmountPx,
        lensEnabled,
        innerShadowEnabled,
        layerBlock,
    ) {
        Modifier.frostShadow(shape, idleSpec).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // Kyant LiquidBottomTabs does not lift, crush, or oversaturate
                // the sampled backdrop. Skip identity colorControls.
                if (idleSpec.brightness != 0f ||
                    idleSpec.contrast != 1f ||
                    idleSpec.saturation != 1f
                ) {
                    colorControls(
                        brightness = idleSpec.brightness,
                        contrast = idleSpec.contrast,
                        saturation = idleSpec.saturation,
                    )
                }
                if (idleSpec.vibrancy) {
                    vibrancy()
                }
                blur(blurPx)
                if (lensEnabled) {
                    val progress = pressProgress.value.coerceIn(0f, 1f)
                    val refractionHeight = lerpFloat(lensHeightPx, pressedLensHeightPx, progress)
                    val refractionAmount = lerpFloat(lensAmountPx, pressedLensAmountPx, progress)
                    if (refractionHeight > 0f || refractionAmount > 0f) {
                        lens(
                            refractionHeight = refractionHeight,
                            refractionAmount = refractionAmount,
                            depthEffect = idleSpec.depthEffect,
                            chromaticAberration = idleSpec.chromaticAberration,
                        )
                    }
                }
            },
            // Backdrop applies the inverse of this transform while sampling.
            // Scaling an outer graphicsLayer cannot provide that compensation.
            layerBlock = if (pressLayerActive || layerBlock != null) {
                {
                    layerBlock?.invoke(this)
                    val progress = pressProgress.value.coerceIn(0f, 1f)
                    val scale = idleSpec.scale +
                        (pressedSpec.scale - idleSpec.scale) * progress
                    scaleX *= scale
                    scaleY *= scale
                }
            } else {
                null
            },
            highlight = if (idleSpec.highlightWidth > 0.dp) ({
                val progress = pressProgress.value.coerceIn(0f, 1f)
                Highlight.Plain.copy(
                    width = idleSpec.highlightWidth,
                    alpha = idleSpec.highlightColor.alpha +
                        (pressedSpec.highlightColor.alpha - idleSpec.highlightColor.alpha) * progress,
                )
            }) else null,
            // Use the explicit Compose drop shadow; avoid the library's offscreen
            // BlurMaskFilter + Clear shadow mask on the hardware canvas.
            shadow = null,
            innerShadow = if (innerShadowEnabled) {
                {
                    val progress = pressProgress.value.coerceIn(0f, 1f)
                    InnerShadow(
                        radius = androidx.compose.ui.unit.lerp(
                            idleSpec.innerShadow,
                            pressedSpec.innerShadow,
                            progress,
                        ),
                        alpha = progress,
                    )
                }
            } else {
                null
            },
            exportedBackdrop = exportedBackdrop,
            onDrawSurface = {
                val progress = pressProgress.value.coerceIn(0f, 1f)
                drawRect(lerp(idleSpec.surfaceColor, pressedSpec.surfaceColor, progress))
            },
        )
    }
    return modifier.then(resourceModifier)
}

/** Lens supports Compose corner shapes and Kyant's rounded rectangle only. */
fun supportsGlassLens(shape: Shape): Boolean {
    if (shape is CornerBasedShape) return true
    return shape.javaClass.name == "com.kyant.shapes.RoundedRectangularShape"
}
