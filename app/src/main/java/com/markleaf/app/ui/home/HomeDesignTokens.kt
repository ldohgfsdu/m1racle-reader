package com.markleaf.app.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home content-first tokens.
 *
 * Alignment: ONE horizontal content rail — 24dp left/right safe inset shared
 * by the brand block, section title, list cards and popup menu. Cards =
 * solid/tonal. Only floating controls (FAB / ··· menu) consume the shared
 * Liquid Glass surface ([com.markleaf.app.ui.common.markLeafLiquidSurface],
 * same tokens as the PDF reader chrome) — never the cards themselves.
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
     * FAB — Liquid Glass rounded square, exactly the PDF reader chrome
     * material ([LiquidSurfaceLevel.Control] via the shared liquid surface):
     * light surface, soft shadow, edge highlight, pressed boost.
     */
    val FabSize = 56.dp
    val FabCorner = 20.dp
    val FabShape = RoundedCornerShape(FabCorner)
    val FabIcon = 26.dp

    /** Scaffold FAB slot already insets 16dp; extras bring it to 20dp. */
    val FabEdgePaddingExtra = 4.dp

    /**
     * Lightweight utility delete menu — shared Liquid Glass surface (Control
     * tokens), compact single row, right-edge aligned with the ··· anchor and
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

    /** Control-level edge highlight (shared liquid tokens: 0.5dp / α0.55). */
    val MenuEdgeHighlightWidth = 0.5.dp
    val MenuEdgeHighlightAlpha = 0.55f

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
}
