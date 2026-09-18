package com.m1racle.reader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * R36 surfaces named for the screens that will consume them.
 *
 * L0 [background] = Hub page and reader canvas.
 * L1 [list] = recent / library / bookmark rows (not cards).
 * L2 [hero] = Continue Reading only.
 * L3 [overlay] = toolbar, dock, menu plates.
 *
 * PDF unified toolbar consumes [overlay] (L3). Home hero consumes [hero] (L2);
 * list rows sit on [background] without a card plate.
 */
@Immutable
data class M1racleSurface(
    val background: Color,
    val list: Color,
    val hero: Color,
    val overlay: Color,
)

object M1racleCornerRadius {
    val control = 14.dp
    val card = 24.dp
    val container = 32.dp
}

/** Overlay elevation is ~30% of the current 6.dp frost shadow. */
object M1racleElevation {
    val none = 0.dp
    val overlay = 2.dp
}

/**
 * The only places glass may appear. Lists, file rows, hero, PDF pages,
 * and the add button are not in this set.
 */
enum class M1racleGlassUsage {
    Toolbar,
    Dock,
    Menu,
}

object M1racleGlassPolicy {
    val allowed = setOf(
        M1racleGlassUsage.Toolbar,
        M1racleGlassUsage.Dock,
        M1racleGlassUsage.Menu,
    )

    fun allows(usage: M1racleGlassUsage): Boolean = usage in allowed
}

fun m1racleSurface(dark: Boolean): M1racleSurface =
    if (dark) DarkM1racleSurface else LightM1racleSurface

private val LightM1racleSurface = M1racleSurface(
    background = PaperBackground,
    list = PaperBackground,
    hero = PaperSurfaceContainerLow,
    overlay = PaperSurface,
)

private val DarkM1racleSurface = M1racleSurface(
    background = InkBackground,
    list = InkBackground,
    hero = InkSurfaceContainerLow,
    overlay = InkSurface,
)

val LocalM1racleSurface = staticCompositionLocalOf { LightM1racleSurface }
