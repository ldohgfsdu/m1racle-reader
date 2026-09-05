package com.m1racle.reader.ui.material

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Semantic material roles. Components own geometry; the engine owns every
 * visual material parameter (tint, alpha, blur, lens, highlight and shadow).
 */
enum class GlassMaterialRole {
    Control,
    Capsule,
    Menu,
    SettingsCard,
    Dialog,
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
    val surfaceColor: Color,
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val shadow: Dp,
    val shadowColor: Color,
    val highlightWidth: Dp,
    val highlightColor: Color,
    val contentDividerAlpha: Float,
    val contentMutedAlpha: Float,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val depthEffect: Boolean,
    val scale: Float,
    val contentPressedOverlayAlpha: Float,
    internal val renderState: GlassRenderState,
    internal val backdropLayer: GlassBackdropLayer,
    internal val exportsForSettingsCard: Boolean,
)

@Immutable
private data class GlassRecipe(
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
    val backdropLayer: GlassBackdropLayer = GlassBackdropLayer.Root,
    val exportsForSettingsCard: Boolean = false,
)

/**
 * The sole recipe registry for M1racleReader glass.
 *
 * Values preserve the shipped Regular-frost hierarchy: compact controls are
 * quiet, menus are denser, and settings cards stack above their parent menu.
 * Dialogs use the same family but render as a soft plate because they live in
 * a separate platform window and cannot safely sample the app backdrop.
 */
object GlassMaterialEngine {
    private val LightSurfaceTint = Color(0xFFFAFAFA)
    private val DarkSurfaceTint = Color(0xFF111820)

    // Bound document-driven light/dark modulation without switching material
    // during scroll/pinch. Blur alone cannot suppress large moving ink regions.
    private const val DocumentBackdropContrast = 0.65f

    private const val LightBrightness = 0.20f
    private const val DarkBrightness = -0.18f
    private const val LightSaturation = 1.25f
    private const val DarkSaturation = 1.25f
    private const val LightShadowAlpha = 0.10f
    private const val DarkShadowAlpha = 0.46f
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

    private val recipes = mapOf(
        GlassMaterialRole.Control to GlassRecipe(
            lightSurfaceAlpha = 0.50f,
            darkSurfaceAlpha = 0.68f,
            blur = 12.dp,
            lensHeight = 10.dp,
            lensAmount = 20.dp,
            shadow = 8.dp,
            highlightWidth = 0.5.dp,
            lightHighlightAlpha = 0.30f,
            darkHighlightAlpha = 0.34f,
            depthEffect = false,
        ),
        GlassMaterialRole.Capsule to GlassRecipe(
            lightSurfaceAlpha = 0.50f,
            darkSurfaceAlpha = 0.68f,
            blur = 12.dp,
            lensHeight = 10.dp,
            lensAmount = 20.dp,
            shadow = 8.dp,
            highlightWidth = 0.5.dp,
            lightHighlightAlpha = 0.30f,
            darkHighlightAlpha = 0.34f,
            depthEffect = false,
        ),
        GlassMaterialRole.Menu to GlassRecipe(
            lightSurfaceAlpha = 0.60f,
            darkSurfaceAlpha = 0.74f,
            blur = 16.dp,
            lensHeight = 16.dp,
            lensAmount = 32.dp,
            shadow = 16.dp,
            highlightWidth = 0.5.dp,
            lightHighlightAlpha = 0.30f,
            darkHighlightAlpha = 0.30f,
            depthEffect = false,
            exportsForSettingsCard = true,
        ),
        GlassMaterialRole.SettingsCard to GlassRecipe(
            lightSurfaceAlpha = 0.66f,
            darkSurfaceAlpha = 0.78f,
            blur = 16.dp,
            lensHeight = 16.dp,
            lensAmount = 32.dp,
            shadow = 18.dp,
            highlightWidth = 0.5.dp,
            lightHighlightAlpha = 0.30f,
            darkHighlightAlpha = 0.32f,
            depthEffect = false,
            backdropLayer = GlassBackdropLayer.RootAndMenu,
        ),
        GlassMaterialRole.Dialog to GlassRecipe(
            // Dialogs live in a separate platform window, so their soft plate
            // cannot sample the reader backdrop. Keep a small amount of
            // translucency for the glass edge while making body copy beneath
            // the dialog visually unreadable.
            lightSurfaceAlpha = 0.97f,
            darkSurfaceAlpha = 0.97f,
            blur = 18.dp,
            lensHeight = 16.dp,
            lensAmount = 32.dp,
            shadow = 18.dp,
            highlightWidth = 0.5.dp,
            lightHighlightAlpha = 0.30f,
            darkHighlightAlpha = 0.38f,
            depthEffect = false,
        ),
    )

    fun resolve(
        role: GlassMaterialRole,
        dark: Boolean,
        pressed: Boolean = false,
        interaction: Boolean = false,
        overDocument: Boolean = false,
    ): GlassMaterialSpec {
        val recipe = checkNotNull(recipes[role]) { "Missing glass recipe for $role" }
        val surfaceBoost = if (pressed) PressedSurfaceAlphaBoost else 0f
        val highlightBoost = if (pressed) PressedHighlightAlphaBoost else 0f
        val surfaceAlpha = (
            if (dark) recipe.darkSurfaceAlpha else recipe.lightSurfaceAlpha
            ) + surfaceBoost
        val highlightAlpha = (
            if (dark) recipe.darkHighlightAlpha else recipe.lightHighlightAlpha
            ) + highlightBoost
        val contrast = if (overDocument) DocumentBackdropContrast else 1f
        val baseBrightness = if (dark) DarkBrightness else LightBrightness
        // colorControls pivots contrast around mid-gray. Re-anchor it to white
        // in light mode and black in dark mode, preserving the paper/ink field.
        val brightness = baseBrightness * contrast +
            (if (dark) -0.5f else 0.5f) * (1f - contrast)
        return GlassMaterialSpec(
            surfaceColor = (if (dark) DarkSurfaceTint else LightSurfaceTint).copy(
                alpha = surfaceAlpha.coerceIn(0f, 0.98f),
            ),
            blur = recipe.blur,
            lensHeight = recipe.lensHeight,
            lensAmount = recipe.lensAmount,
            shadow = recipe.shadow,
            shadowColor = Color.Black.copy(
                alpha = if (dark) DarkShadowAlpha else LightShadowAlpha,
            ),
            highlightWidth = recipe.highlightWidth,
            highlightColor = Color.White.copy(alpha = highlightAlpha.coerceIn(0f, 0.70f)),
            contentDividerAlpha = recipe.contentDividerAlpha,
            contentMutedAlpha = if (dark) {
                recipe.darkContentMutedAlpha
            } else {
                recipe.lightContentMutedAlpha
            },
            brightness = brightness,
            contrast = contrast,
            saturation = if (dark) DarkSaturation else LightSaturation,
            depthEffect = recipe.depthEffect,
            scale = if (pressed) recipe.pressedScale else 1f,
            contentPressedOverlayAlpha = if (dark) {
                DarkPressedContentOverlayAlpha
            } else {
                LightPressedContentOverlayAlpha
            },
            renderState = if (
                pressed || interaction ||
                role == GlassMaterialRole.Menu ||
                role == GlassMaterialRole.SettingsCard ||
                role == GlassMaterialRole.Dialog
            ) {
                GlassRenderState.INTERACTION
            } else {
                GlassRenderState.IDLE
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
    dark = isSystemInDarkTheme(),
).contentMutedAlpha

/** Theme-aware restrained overlay used by rows inside a glass surface. */
@Composable
fun glassPressedContentOverlayAlpha(role: GlassMaterialRole): Float =
    GlassMaterialEngine.resolve(
        role = role,
        dark = isSystemInDarkTheme(),
    ).contentPressedOverlayAlpha

/** Root content capture plus the exported menu material used by stacked cards. */
@Immutable
data class GlassBackdropHost(
    val root: LayerBackdrop,
    val menuExport: LayerBackdrop,
    val overDocument: Boolean = false,
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
): Modifier {
    val dark = isSystemInDarkTheme()
    val host = LocalGlassBackdropHost.current
    val overDocument = host?.overDocument == true
    // Stable endpoint specs prevent unrelated parent recompositions from
    // replacing the renderer's effect lambdas and invalidating its draw cache.
    val idleSpec = remember(role, dark, interaction, overDocument) {
        GlassMaterialEngine.resolve(
            role = role,
            dark = dark,
            interaction = interaction,
            overDocument = overDocument,
        )
    }
    val pressedSpec = remember(role, dark, interaction, overDocument) {
        GlassMaterialEngine.resolve(
            role = role,
            dark = dark,
            pressed = true,
            interaction = interaction,
            overDocument = overDocument,
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
    if (!sampleBackdrop || host == null) {
        return then(
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
    )
}

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
        .shadow(
            elevation = idleSpec.shadow,
            shape = shape,
            ambientColor = idleSpec.shadowColor,
            spotColor = idleSpec.shadowColor,
        )
        .clip(shape)
        .drawWithContent {
            val progress = pressProgress.value.coerceIn(0f, 1f)
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = lerp(idleSpec.surfaceColor, pressedSpec.surfaceColor, progress),
            )
            drawContent()
            drawOutline(
                outline = outline,
                color = lerp(idleSpec.highlightColor, pressedSpec.highlightColor, progress),
                style = Stroke(width = idleSpec.highlightWidth.toPx()),
            )
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
): Modifier {
    val density = LocalDensity.current
    val backdrop: Backdrop = when (idleSpec.backdropLayer) {
        GlassBackdropLayer.Root -> host.root
        GlassBackdropLayer.RootAndMenu -> rememberCombinedBackdrop(host.root, host.menuExport)
    }
    val exportedBackdrop = if (idleSpec.exportsForSettingsCard) host.menuExport else null
    val blurPx = with(density) { idleSpec.blur.toPx() }
    val lensHeightPx = with(density) { idleSpec.lensHeight.toPx() }
    val lensAmountPx = with(density) { idleSpec.lensAmount.toPx() }
    val lensEnabled = supportsGlassLens(shape) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
        lensEnabled,
    ) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = idleSpec.brightness,
                    contrast = idleSpec.contrast,
                    saturation = idleSpec.saturation,
                )
                blur(blurPx)
                if (lensEnabled) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = idleSpec.depthEffect,
                    )
                }
            },
            layerBlock = if (pressLayerActive) {
                {
                    val progress = pressProgress.value.coerceIn(0f, 1f)
                    val scale = idleSpec.scale +
                        (pressedSpec.scale - idleSpec.scale) * progress
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                null
            },
            highlight = {
                val progress = pressProgress.value.coerceIn(0f, 1f)
                Highlight.Plain.copy(
                    width = idleSpec.highlightWidth,
                    alpha = idleSpec.highlightColor.alpha +
                        (pressedSpec.highlightColor.alpha - idleSpec.highlightColor.alpha) * progress,
                )
            },
            shadow = {
                Shadow(
                    radius = idleSpec.shadow,
                    color = idleSpec.shadowColor,
                )
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
