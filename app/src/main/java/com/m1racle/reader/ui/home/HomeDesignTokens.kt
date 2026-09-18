package com.m1racle.reader.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home reading-entry tokens.
 * GlassPolicy: Dock / FAB / long-press Menu only. Hero and list rows are solid.
 */
object HomeDesignTokens {
    /** Unified page rail: brand text, section title, cards all at 24dp. */
    val PageHorizontal = 24.dp
    val CardGap = 8.dp

    /** "最近文件" → first card: section reads continuous with the list. */
    val HeaderToFirstCardGap = 2.dp

    val AppNameToSubtitle = 8.dp
    val SubtitleToSectionGap = 16.dp


    /** Cards: solid tonal surface, radius 18–20dp. */
    val RadiusCard = 20.dp
    val CardShape = RoundedCornerShape(RadiusCard)

    /**
     * Utility actions use the platform-positioned solid menu with the shared corner.
     */
    val MenuCorner = 10.dp
    val MenuShape = RoundedCornerShape(MenuCorner)

    val AppNameSize = 22.sp
    val AppNameLine = 28.sp
    val AppSubtitleSize = 13.sp
    val SectionTitleSize = 15.sp

    // ── H2.2 Reading Hub ────────────────────────────────────────────────────

    /**
     * Continue Reading hero uses L2 [M1racleSurface.hero], never glass.
     */
    val HeroCorner = 20.dp
    val HeroShape = RoundedCornerShape(HeroCorner)
    val HeroPaddingH = 16.dp
    val HeroPaddingV = 14.dp
    val HeroCaptionSize = 12.sp
    val HeroTitleSize = 17.sp
    val HeroTitleLine = 22.sp
    val HeroMetaSize = 12.sp

    /**
     * 底部悬浮 Glass Dock（与阅读控件共用轻折射磨砂配方）：
     * 悬浮胶囊，四入口，内容以底部 inset 避让——永不遮挡内容。
     */
    val DockHeight = 64.dp
    val DockBottomGap = 12.dp
    /** Scroll content clears the Dock; system navigation inset is applied by the page. */
    val DockContentClearance = DockHeight + DockBottomGap + 24.dp
    val DockHorizontalMargin = 20.dp
    val DockLabelSize = 12.sp
    val DockItemWeight: Float = 1f
    val DockMaxWidth = 440.dp
    val DockContentPadding = 4.dp
}
