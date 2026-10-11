package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
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
    /** Selected lens over dock icons and segmented labels. */
    SelectionLens,
    Menu,
    ExpandedMenu,
    SettingsCard,
    Dialog,
    /** Markdown top chrome. Legacy 16dp + 0.62 plate; optical rewrite deferred. */
    ProgressiveChrome,
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
    /** QA 档 3: run vibrancy before colorControls so compression is the last color step. */
    val compressAfterVibrancy: Boolean = false,
)

@Immutable
private data class GlassRecipe(
    val family: GlassOpticalFamily,
    val lightTint: Color = Color(0xFFFAFAFA),
    val lightShadowAlpha: Float = 0.10f,
    val darkShadowAlpha: Float = 0.46f,
    // Optical night plate. Neutral near-black.
    val darkTint: Color = Color(0xFF121212),
    val lightBrightness: Float = 0f,
    val darkBrightness: Float = 0f,
    val lightContrast: Float = 1f,
    val darkContrast: Float = 1f,
    val compressAfterVibrancy: Boolean = false,
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
    val pressedDarkTint: Color? = null,
    val pressedHighlightAlpha: Float? = null,
    /** Press boosts; production defaults. QA variant 2 overrides them. */
    val pressedSurfaceAlphaBoost: Float = 0.04f,
    val pressedHighlightAlphaBoost: Float = 0.10f,
    val lensEnabled: Boolean = false,
    val backdropLayer: GlassBackdropLayer = GlassBackdropLayer.Root,
    val exportsForSettingsCard: Boolean = false,
)

/**
 * The sole recipe registry for M1racleReader glass.
 *
 * Floating surfaces share the production liquid recipe. Settings cards sample
 * above their parent menu. Optical families share physical direction; roles
 * retain their own density and geometry-independent endpoints.
 * Dialogs use the same family but render as a soft plate because they live in
 * a separate platform window and cannot safely sample the app backdrop.
 */
object GlassMaterialEngine {
    // Press boosts (production +0.04 surface / +0.10 rim) live on GlassRecipe.
    private const val LightPressedContentOverlayAlpha = 0.04f
    private const val DarkPressedContentOverlayAlpha = 0.05f

    /** Shared physical response for material and material-content presses. */
    internal val PressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = 0.001f,
    )

    // Base for the non-floating gradient and cross-window plate.
    // Floating consumers derive their material from liquidGlassStyle below.
    private val regularFrost = GlassRecipe(
        family = GlassOpticalFamily.CONTENT,
        lightTint = Color(0xFFF5F5F5),
        lightShadowAlpha = 0.14f,
        darkShadowAlpha = 0.28f,
        darkTint = Color.Black,
        lightSurfaceAlpha = 0.62f,
        darkSurfaceAlpha = 0.58f,
        blur = 18.dp,
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

    private fun liquidRecipe(role: GlassMaterialRole): GlassRecipe {
        val light = liquidGlassStyle(role, false)
        val dark = liquidGlassStyle(role, true)
        return regularFrost.copy(
            lightTint = light.tint.copy(alpha = 1f),
            darkTint = dark.tint.copy(alpha = 1f),
            lightSurfaceAlpha = light.tint.alpha,
            darkSurfaceAlpha = dark.tint.alpha,
            blur = light.blur,
            lensHeight = light.lensHeight,
            lensAmount = light.lensAmount,
            lensEnabled = light.lensAmount > 0.dp,
            shadow = light.shadow,
            lightShadowAlpha = light.shadowAlpha,
            darkShadowAlpha = dark.shadowAlpha,
            highlightWidth = light.highlightWidth,
            lightHighlightAlpha = light.highlightAlpha,
            darkHighlightAlpha = dark.highlightAlpha,
        )
    }

    // Readable Markdown chrome; the renderer matches its tint to the page.
    private val progressiveChrome = regularFrost.copy(
        lightSurfaceAlpha = 0.90f,
        darkSurfaceAlpha = 0.90f,
        blur = 16.dp,
        shadow = 0.dp,
        vibrancy = false,
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
        GlassMaterialRole.Dock to liquidRecipe(GlassMaterialRole.Dock).copy(pressedScale = 1f),
        GlassMaterialRole.Control to liquidRecipe(GlassMaterialRole.Control).copy(
            family = GlassOpticalFamily.INTERACTIVE,
            pressedScale = 1.085f,
            pressedSurfaceAlpha = 0.40f,
            pressedDarkTint = Color(0xFFAAAAAA),
        ),
        GlassMaterialRole.Capsule to liquidRecipe(GlassMaterialRole.Capsule).copy(
            pressedScale = 1.075f,
            pressedSurfaceAlpha = 0.40f,
            pressedDarkTint = Color(0xFFAAAAAA),
        ),
        GlassMaterialRole.SelectionLens to liquidRecipe(GlassMaterialRole.SelectionLens).copy(
            lensEnabled = true,
            pressedScale = 1f, // Dock owns the pickup transform, including drag preview.
            pressedLensHeight = 10.dp,
            pressedLensAmount = 14.dp,
            pressedSurfaceAlpha = 0f,
            pressedHighlightAlpha = 1f,
            pressedInnerShadow = 8.dp,
            chromaticAberration = true,
        ),
        GlassMaterialRole.Menu to liquidRecipe(GlassMaterialRole.Menu).copy(
            exportsForSettingsCard = true,
        ),
        GlassMaterialRole.ExpandedMenu to liquidRecipe(GlassMaterialRole.ExpandedMenu).copy(
            family = GlassOpticalFamily.CONTENT,
            exportsForSettingsCard = true,
        ),
        GlassMaterialRole.SettingsCard to liquidRecipe(GlassMaterialRole.SettingsCard).copy(
            backdropLayer = GlassBackdropLayer.RootAndMenu,
        ),
        GlassMaterialRole.Dialog to fallbackPlate.copy(family = GlassOpticalFamily.PANEL),
        GlassMaterialRole.ProgressiveChrome to progressiveChrome,
    )

    fun rolesWithConsumers(): Set<GlassMaterialRole> = recipes.keys

    fun resolve(
        role: GlassMaterialRole,
        dark: Boolean,
        pressed: Boolean = false,
        interaction: Boolean = false,
        qa: QaGlassSelection = QaGlass.selection,
    ): GlassMaterialSpec = resolve(
        role = role,
        environment = GlassEnvironment.app(dark),
        capability = GlassCapability.FULL_OPTICAL,
        interaction = when {
            pressed -> GlassInteractionState.PRESSED
            interaction -> GlassInteractionState.EXPANDED
            else -> GlassInteractionState.IDLE
        },
        qa = qa,
    )

    fun resolve(
        role: GlassMaterialRole,
        environment: GlassEnvironment,
        capability: GlassCapability = GlassCapability.FULL_OPTICAL,
        interaction: GlassInteractionState = GlassInteractionState.IDLE,
        qa: QaGlassSelection = QaGlass.selection,
    ): GlassMaterialSpec {
        // QA-only experiment; BASELINE (and every non-qa build) returns null here.
        val roleRecipe = checkNotNull(recipes[role]) { "Missing glass recipe for $role" }
            .withQaOverride(qaGlassOverride(role, qa))
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
                pressedSurfaceAlpha = null,
                pressedDarkTint = null,
            )
        } else {
            roleRecipe
        }
        val dark = environment.isDark
        val pressed = interaction == GlassInteractionState.PRESSED ||
            interaction == GlassInteractionState.DRAGGING
        val idleSurfaceAlpha = if (dark) recipe.darkSurfaceAlpha else recipe.lightSurfaceAlpha
        val surfaceAlpha = if (pressed) {
            recipe.pressedSurfaceAlpha ?: (idleSurfaceAlpha + recipe.pressedSurfaceAlphaBoost)
        } else {
            idleSurfaceAlpha
        }
        val highlightBoost = if (pressed && recipe.highlightWidth > 0.dp) recipe.pressedHighlightAlphaBoost else 0f
        val highlightAlpha = if (pressed && recipe.pressedHighlightAlpha != null) recipe.pressedHighlightAlpha else (
            if (dark) recipe.darkHighlightAlpha else recipe.lightHighlightAlpha
            ) + highlightBoost
        val brightness = if (dark) recipe.darkBrightness else recipe.lightBrightness
        val muted = if (dark) recipe.darkContentMutedAlpha else recipe.lightContentMutedAlpha
        val lensOn = recipe.lensEnabled && capability == GlassCapability.FULL_OPTICAL
        val blur = if (capability == GlassCapability.SOLID_FALLBACK) 0.dp else recipe.blur
        return GlassMaterialSpec(
            family = recipe.family,
            surfaceColor = (if (dark) {
                if (pressed) recipe.pressedDarkTint ?: recipe.darkTint else recipe.darkTint
            } else recipe.lightTint).copy(
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
            contrast = if (dark) recipe.darkContrast else recipe.lightContrast,
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
            compressAfterVibrancy = recipe.compressAfterVibrancy,
        )
    }
}

private fun GlassRecipe.withQaOverride(o: QaGlassOverride?): GlassRecipe = if (o == null) this else copy(
    lightTint = o.lightTint ?: lightTint,
    lightSurfaceAlpha = o.lightAlpha ?: lightSurfaceAlpha,
    darkTint = o.darkTint ?: darkTint,
    darkSurfaceAlpha = o.darkAlpha ?: darkSurfaceAlpha,
    blur = o.blur ?: blur,
    highlightWidth = o.highlightWidth ?: highlightWidth,
    lightHighlightAlpha = o.lightHighlightAlpha ?: lightHighlightAlpha,
    darkHighlightAlpha = o.darkHighlightAlpha ?: darkHighlightAlpha,
    shadow = o.shadow ?: shadow,
    lightShadowAlpha = o.lightShadowAlpha ?: lightShadowAlpha,
    darkShadowAlpha = o.darkShadowAlpha ?: darkShadowAlpha,
    pressedSurfaceAlpha = if (o.clearPressedTint) null else pressedSurfaceAlpha,
    pressedDarkTint = if (o.clearPressedTint) null else pressedDarkTint,
    pressedScale = o.pressedScale ?: pressedScale,
    pressedSurfaceAlphaBoost = o.pressedSurfaceAlphaBoost ?: pressedSurfaceAlphaBoost,
    pressedHighlightAlphaBoost = o.pressedHighlightAlphaBoost ?: pressedHighlightAlphaBoost,
    lightBrightness = o.lightBrightness ?: lightBrightness,
    darkBrightness = o.darkBrightness ?: darkBrightness,
    lightContrast = o.lightContrast ?: lightContrast,
    darkContrast = o.darkContrast ?: darkContrast,
    lightContentMutedAlpha = o.mutedAlpha ?: lightContentMutedAlpha,
    darkContentMutedAlpha = o.mutedAlpha ?: darkContentMutedAlpha,
    compressAfterVibrancy = o.compressAfterVibrancy ?: compressAfterVibrancy,
)

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
    exportBackdrop: LayerBackdrop? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    interactionProgress: State<Float>? = null,
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
    // QA glass selection is a key so a QA switch re-resolves live; constant elsewhere.
    val qa = QaGlass.selection
    val idleSpec = remember(role, environment, capability, interactionState, qa) {
        GlassMaterialEngine.resolve(
            role = role,
            environment = environment,
            capability = capability,
            interaction = interactionState,
            qa = qa,
        )
    }
    val pressedSpec = remember(role, environment, capability, qa) {
        GlassMaterialEngine.resolve(
            role = role,
            environment = environment,
            capability = capability,
            interaction = GlassInteractionState.PRESSED,
            qa = qa,
        )
    }
    val floatingControl = role == GlassMaterialRole.Control || role == GlassMaterialRole.Capsule
    val pressProgress = interactionProgress ?: animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = when {
            // QA variant 2: 120ms decelerate in, spring 0.7 / 500 out.
            floatingControl && qa.coolapk -> if (pressed) {
                tween(QaCoolapk.PressInMillis, easing = LinearOutSlowInEasing)
            } else {
                spring(QaCoolapk.ReleaseDampingRatio, QaCoolapk.ReleaseStiffness, .001f)
            }
            // Match the dock pickup spring: refraction and rim must settle with
            // the silhouette rather than fading out before its elastic rebound.
            role == GlassMaterialRole.SelectionLens -> spring(.55f, 450f, visibilityThreshold = .001f)
            floatingControl -> spring(dampingRatio = .55f, stiffness = 450f, visibilityThreshold = .001f)
            else -> GlassMaterialEngine.PressSpring
        },
        label = "glassMaterialPress",
    )
    // Reading the progress here keeps the temporary scale layer alive through
    // the release spring. It does not replace the remembered effect modifier.
    val pressLayerActive = pressed || kotlin.math.abs(pressProgress.value) > 0.001f
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
        role = role,
        host = host,
        shape = shape,
        idleSpec = idleSpec,
        pressedSpec = pressedSpec,
        pressProgress = pressProgress,
        pressLayerActive = pressLayerActive,
        sourceBackdrop = sourceBackdrop,
        exportBackdrop = exportBackdrop,
        layerBlock = layerBlock,
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
            val progress = if (pressedSpec.scale > idleSpec.scale)
                pressProgress.value.coerceIn(-.3f, 1.3f) else pressProgress.value.coerceIn(0f, 1f)
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
    role: GlassMaterialRole,
    host: GlassBackdropHost,
    shape: Shape,
    idleSpec: GlassMaterialSpec,
    pressedSpec: GlassMaterialSpec,
    pressProgress: State<Float>,
    pressLayerActive: Boolean,
    sourceBackdrop: Backdrop? = null,
    exportBackdrop: LayerBackdrop? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val density = LocalDensity.current
    // An explicit processed source replaces the page source. Adding raw page
    // pixels underneath it reintroduces sharp detail through translucent glass.
    val backdrop = sourceBackdrop ?: when (idleSpec.backdropLayer) {
        GlassBackdropLayer.Root -> host.root
        GlassBackdropLayer.RootAndMenu -> rememberCombinedBackdrop(host.root, host.menuExport)
    }
    val exportedBackdrop = exportBackdrop ?: if (idleSpec.exportsForSettingsCard) host.menuExport else null
    require(exportedBackdrop == null || exportedBackdrop !== backdrop) {
        "A glass surface cannot export into its own sampling source"
    }
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
                val colorStep = glassColorStep(idleSpec)
                // QA 档 3 (spec §3.1): vibrancy first, compression last, so the
                // backdrop always ends inside the compressed luminance band.
                if (idleSpec.compressAfterVibrancy) {
                    if (idleSpec.vibrancy) vibrancy()
                    if (colorStep) colorControls(
                        brightness = idleSpec.brightness,
                        contrast = idleSpec.contrast,
                        saturation = idleSpec.saturation,
                    )
                } else {
                    if (colorStep) colorControls(
                        brightness = idleSpec.brightness,
                        contrast = idleSpec.contrast,
                        saturation = idleSpec.saturation,
                    )
                    if (idleSpec.vibrancy) vibrancy()
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
                    val progress = if (pressedSpec.scale > idleSpec.scale)
                        pressProgress.value.coerceIn(-.3f, 1.3f) else pressProgress.value.coerceIn(0f, 1f)
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
                Highlight.Default.copy(
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

/** Whether colorControls runs; identity brightness / contrast / saturation is skipped. */
internal fun glassColorStep(spec: GlassMaterialSpec): Boolean =
    spec.brightness != 0f || spec.contrast != 1f || spec.saturation != 1f

/** Effect order the renderer uses, for tests: "vibrancy" / "colorControls". */
internal fun glassColorEffectOrder(spec: GlassMaterialSpec): List<String> {
    val v = if (spec.vibrancy) listOf("vibrancy") else emptyList()
    val c = if (glassColorStep(spec)) listOf("colorControls") else emptyList()
    return if (spec.compressAfterVibrancy) v + c else c + v
}

/** Lens supports Compose corner shapes and Kyant's rounded rectangle only. */
fun supportsGlassLens(shape: Shape): Boolean {
    if (shape is CornerBasedShape) return true
    return shape.javaClass.name == "com.kyant.shapes.RoundedRectangularShape"
}
