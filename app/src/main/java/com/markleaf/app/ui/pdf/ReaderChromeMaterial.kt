package com.markleaf.app.ui.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.markleaf.app.ui.common.LiquidBackdropHost
import com.markleaf.app.ui.common.LiquidSurfaceLevel
import com.markleaf.app.ui.common.LocalLiquidBackdropHost
import com.markleaf.app.ui.common.markLeafLiquidSurface
import com.markleaf.app.ui.common.shapeSupportsLiquidLens

/**
 * Reader chrome surface levels map onto the global Liquid family.
 * Frosted / dual-material paths were removed (r26.1) — Liquid is the only UI surface language.
 */
enum class ReaderChromeSurfaceRole {
    Chrome,
    Parent,
    Child,
}

/** @deprecated use [LocalLiquidBackdropHost]; kept as alias for Reader wiring. */
typealias ReaderChromeBackdropHost = LiquidBackdropHost

val LocalReaderChromeBackdropHost = LocalLiquidBackdropHost

/**
 * Reader chrome entry — always Liquid via [markLeafLiquidSurface].
 * Components must not branch on material style (none remains).
 */
@Composable
fun Modifier.readerChromeSurface(
    level: ReaderGlassElevation,
    shape: Shape,
    role: ReaderChromeSurfaceRole = roleForElevation(level),
): Modifier {
    val liquidLevel = when (role) {
        ReaderChromeSurfaceRole.Chrome -> LiquidSurfaceLevel.Control
        ReaderChromeSurfaceRole.Parent -> LiquidSurfaceLevel.Parent
        ReaderChromeSurfaceRole.Child -> LiquidSurfaceLevel.Foreground
    }
    return markLeafLiquidSurface(
        shape = shape,
        level = liquidLevel,
        exportForChild = role == ReaderChromeSurfaceRole.Parent,
    )
}

internal fun roleForElevation(level: ReaderGlassElevation): ReaderChromeSurfaceRole = when (level) {
    ReaderGlassElevation.Chrome -> ReaderChromeSurfaceRole.Chrome
    ReaderGlassElevation.Main -> ReaderChromeSurfaceRole.Parent
    ReaderGlassElevation.Child -> ReaderChromeSurfaceRole.Child
}

/** Geometry freeze bands (layout must not change with material). */
object ReaderChromeMaterialLogic {
    fun geometryInvariantBands(): GeometryBands = GeometryBands(
        parentWidthDp = 220f,
        cornerDp = 28f,
        rowHeightDp = 52f,
        openMorphMs = 200,
        closeMorphMs = 180,
    )

    fun shouldApplyLiquidLens(shape: Shape): Boolean = shapeSupportsLiquidLens(shape)

    data class GeometryBands(
        val parentWidthDp: Float,
        val cornerDp: Float,
        val rowHeightDp: Float,
        val openMorphMs: Int,
        val closeMorphMs: Int,
    )
}
