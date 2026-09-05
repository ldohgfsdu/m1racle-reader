package com.m1racle.reader.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home content-first tokens.
 *
 * Alignment: ONE horizontal content rail — 24dp left/right safe inset shared
 * by the brand block, section title, list cards and popup menu. Cards =
 * solid/tonal. Only floating controls (FAB / ··· menu) consume the shared
 * unified glass material engine — never the cards themselves.
 */
object HomeDesignTokens {
    /** Unified page rail: brand text, section title, cards all at 24dp. */
    val PageHorizontal = 24.dp
    val CardGap = 8.dp

    /** Card internal padding — compact: horizontal 16, top/bottom ~2dp tighter. */
    val CardPaddingH = 16.dp
    val CardPaddingTop = 11.dp
    val CardPaddingBottom = 9.dp

    /** "最近文件" → first card: section reads continuous with the list. */
    val HeaderToFirstCardGap = 2.dp

    /** Brand header lives inside the single LazyColumn and scrolls away. */
    val HeaderTopPad = 8.dp
    val AppNameToSubtitle = 8.dp
    val SubtitleToSectionGap = 16.dp

    /** Card internal rhythm: title → type → meta → progress. */
    val TitleToTypeGap = 5.dp
    val TypeToMetaGap = 4.dp
    val MetaToProgressGap = 7.dp

    /** Bottom pad beyond nav so last card clears the 56dp FAB + 20dp gap. */
    val FabClearance = 96.dp

    /** Cards: solid tonal surface, radius 18–20dp. */
    val RadiusCard = 20.dp
    val CardShape = RoundedCornerShape(RadiusCard)

    /**
     * Bare ··· overflow icon: 44–48dp invisible touch target, ~20–22dp visible
     * glyph (touch − 2×pad). No surface, no shadow, no border.
     */
    val OverflowTouch = 48.dp
    val OverflowIconPad = 13.dp

    /**
     * FAB — rounded square using the unified Control material:
     * light surface, soft shadow, edge highlight, pressed boost.
     */
    val FabSize = 56.dp
    val FabCorner = 20.dp
    val FabShape = RoundedCornerShape(FabCorner)
    val FabIcon = 26.dp

    /** Scaffold FAB slot already insets 16dp; extras bring it to 20dp. */
    val FabEdgePaddingExtra = 4.dp

    /**
     * Lightweight utility delete menu — unified Menu material, compact single
     * row, right-edge aligned with the ··· anchor and
     * expanding leftward. Corner = PDF chrome menu-row corner (10dp).
     */
    val MenuMinWidth = 140.dp
    val MenuMaxWidth = 160.dp
    val MenuCorner = 10.dp
    val MenuShape = RoundedCornerShape(MenuCorner)
    val MenuRowHeight = 48.dp
    val MenuRowPadH = 16.dp
    val MenuItemIcon = 22.dp
    val MenuIconGap = 12.dp

    /** Popup x-offset so menu right edge = anchor right edge (menu min width). */
    val MenuAnchorOverhang = MenuMinWidth - OverflowTouch

    val AppNameSize = 22.sp
    val AppNameLine = 28.sp
    val AppSubtitleSize = 13.sp
    val SectionTitleSize = 15.sp
    val FileTitleSize = 18.sp
    val FileTitleLine = 21.sp
    val FileTypeSize = 12.sp
    val FileTypeLine = 14.sp
    val MetaSize = 12.sp
    val MetaLine = 14.sp

    val ProgressHeight = 3.dp

    // ── H2.2 Reading Hub ────────────────────────────────────────────────────

    /**
     * 继续阅读 hero 卡（GlassMaterialRole.Menu 玻璃）：视觉强度高于实色文档卡。
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
     * 底部悬浮 Glass Dock（GlassMaterialRole.Dock，独立通透配方）：
     * 悬浮胶囊，四入口，内容以底部 inset 避让——永不遮挡内容。
     */
    val DockHeight = 64.dp
    val DockBottomGap = 12.dp
    val DockHorizontalMargin = 20.dp
    val DockCorner = 32.dp
    val DockShape = RoundedCornerShape(DockCorner)
    val DockItemIcon = 24.dp
    val DockLabelSize = 11.sp
    val DockItemWeight: Float = 1f
    val DockMaxWidth = 440.dp
    val DockContentPadding = 4.dp
}
