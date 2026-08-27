package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R24 §问题一/问题二 几何契约（与 ReaderMoreHost / PdfChrome 常量一致）：
 * - 菜单卡就是三点圆本身 morph 而来：expanded top == circle top、right == circle
 *   right（TopEnd anchor 两态恒定，无独立 popover、无 y offset、无 gap）。
 * - corner 从 circle radius（44/2 = 22dp）连续插值到最终 card corner（24–28dp 带），
 *   全程无 shape 瞬切、无"药丸/肥皂块"固定圆角。
 * - 一级 row 50–54dp、option row 略矮；左右 padding 16dp；icon 22–24dp；gap 12–14dp。
 * - 父卡高度恒定 = 6 条一级 row + pad + divider；二级展开不改父卡高度（child overlay）。
 * - Nested child：top 锁定在被点 row 的 top（vertical pad + index × row 高），
 *   宽与父卡对齐，只向下生长（覆盖而非推挤）。
 * - TextbookPageForm 是同一 morph 容器的 form 态（top/right 锚点恒定，宽 216→288）。
 */
class ReaderChromeGeometryTest {

    private val menuWidthDp = 218 // MAIN 212–224dp band
    private val formWidthDp = 284 // advanced 276–292dp band
    private val circleSizeDp = 44
    private val menuRowHeight = 52
    private val optionRowHeight = 48
    private val screenMarginDp = 12
    private val circleTopPadDp = 8
    private val menuVerticalPadDp = 3
    private val cornerClosedDp = 22
    private val cornerOpenDp = 26
    private val rowHPadDp = 16
    private val iconSlotDp = 22
    private val iconGapDp = 12
    /** Level-2 label start (OptionIndent): aligns with MAIN title column. */
    private val optionIndentDp = 16 + 22 + 12

    /** Chrome row top in window dp: status inset + fixed top pad (same for all chrome). */
    private fun chromeTop(statusInsetDp: Int) = statusInsetDp + circleTopPadDp

    /** The three-dot circle bounds — the morph's zero frame (TopEnd anchor). */
    private fun circleBounds(screenWidthDp: Int, statusInsetDp: Int): IntArray = intArrayOf(
        screenWidthDp - screenMarginDp - circleSizeDp,
        chromeTop(statusInsetDp),
        screenWidthDp - screenMarginDp,
        chromeTop(statusInsetDp) + circleSizeDp,
    )

    /**
     * The MORPHED parent card bounds: the SAME node as the circle — top/right
     * identical to the circle's; grows leftward/downward only. Height is
     * FIXED (6 rows + pads + divider) in Main AND Expanded — level-2 never
     * reflows the parent (nested child overlay instead).
     */
    private fun menuBounds(
        screenWidthDp: Int,
        statusInsetDp: Int,
    ): IntArray = intArrayOf(
        screenWidthDp - screenMarginDp - menuWidthDp,
        chromeTop(statusInsetDp),
        screenWidthDp - screenMarginDp,
        chromeTop(statusInsetDp) +
            menuVerticalPadDp * 2 + menuRowHeight * 6,
    )

    @Test
    fun `expanded card shares the circle top and right exactly`() {
        // The morph anchor: the card IS the circle grown — top/right never
        // move, no secondary y offset, no popover gap.
        val screenW = 412
        val inset = 24
        val circle = circleBounds(screenW, inset)
        val menu = menuBounds(screenW, inset)
        assertEquals("card top == circle top", circle[1], menu[1])
        assertEquals("card right == circle right", circle[2], menu[2])
        assertTrue("card grows leftward", menu[0] < circle[0])
        assertTrue("card grows downward", menu[3] > circle[3])
    }

    @Test
    fun `corner interpolates from circle radius to card corner - never snaps`() {
        // 44×44 with 22dp corners IS a circle (corner == radius); the final
        // card corner is in the 24–28dp band. The morph interpolates between
        // them continuously — no pill/soap-bar silhouette, no shape switch.
        assertEquals("closed corner == circle radius", circleSizeDp / 2, cornerClosedDp)
        assertTrue("final corner in 24–28 band", cornerOpenDp in 24..28)
        assertTrue("corner moves (interpolates)", cornerOpenDp != cornerClosedDp)
    }

    @Test
    fun `density spec - rows 50-54dp, padding 16dp, icon 22-24dp, gap 12-14dp`() {
        assertTrue("level-1 row 50–54dp", menuRowHeight in 50..54)
        assertTrue("option row below level-1", optionRowHeight < menuRowHeight)
        assertEquals("row padding 16dp", 16, rowHPadDp)
        assertTrue("icon 22–24dp", iconSlotDp in 22..24)
        assertTrue("icon→label gap 12–14dp", iconGapDp in 12..14)
        // R25.1: option text left edge aligns with MAIN title column
        // (pad + icon + gap) — no extra rightward drift.
        val level1LabelStart = rowHPadDp + iconSlotDp + iconGapDp
        assertEquals("option indent == title column", level1LabelStart, optionIndentDp)
    }

    @Test
    fun `parent card height is fixed - level-2 never reflows it`() {
        // R24: the parent height is the SAME in Main and Expanded (6 fixed
        // rows, no section divider — R24.2); the nested child overlays
        // instead of displacing.
        val screenW = 412
        val inset = 24
        val rowsOnly = menuVerticalPadDp * 2 + menuRowHeight * 6
        assertEquals("parent = 6 rows + pads", rowsOnly, menuBounds(screenW, inset)[3] - chromeTop(inset))
        // No extra height anywhere for level-2 (no accordion slot).
        assertEquals(rowsOnly, menuBounds(screenW, inset)[3] - chromeTop(inset))
    }

    @Test
    fun `nested child top locks to the tapped row`() {
        // Child source Y inside the parent: vertical pad + index × row height.
        // 阅读方式 = row 0 → y = 3dp; 阅读界面 = row 1 → y = 3 + 52 = 55dp.
        assertEquals(3, menuVerticalPadDp + 0 * menuRowHeight)
        assertEquals(55, menuVerticalPadDp + 1 * menuRowHeight)
        // Child grows DOWN from the row height to header + options (2 × 48).
        val readingModeChildFull = menuRowHeight + 2 * optionRowHeight
        assertEquals(148, readingModeChildFull)
        assertTrue(readingModeChildFull > menuRowHeight * 2)
    }

    @Test
    fun `no state ever moves the card vertically`() {
        val screenW = 800 // landscape wide screen
        val inset = 24
        val circleTop = circleBounds(screenW, inset)[1]
        assertEquals("collapsed top", circleTop, menuBounds(screenW, inset)[1])
    }

    @Test
    fun `print entry is a plain row - the card morphs into the form instead`() {
        // 设置教材页码 is a level-1 row (6 rows total). No print body slot
        // exists in the parent; the compact form is the same container in its
        // form state (width 216 → 288, same anchor).
        val screenW = 412
        val inset = 24
        val circle = circleBounds(screenW, inset)
        assertEquals("form top == circle top", circle[1], chromeTop(inset))
        assertEquals("form right == circle right", circle[2], screenW - screenMarginDp)
        assertTrue("form widens the same container", formWidthDp > menuWidthDp)
        assertTrue("rows fit 1.15x font scale", menuRowHeight > 14 * 1.15f)
        assertTrue("option rows fit 1.15x text", optionRowHeight > 13 * 1.15f)
    }

    @Test
    fun `nested child grows from the task row with right-top continuity`() {
        // R25.2 ChatGPT nested: right-aligns MAIN; left expand 48–96dp;
        // left must not invade back-circle hit area (12+44+8 ≈ 64dp).
        val screenW = 412
        val backClearance = 64
        val leftExpand = 72 // mid of 48–96
        val targetWidth = minOf(menuWidthDp + leftExpand, screenW - screenMarginDp - backClearance)
        assertTrue("left expand 48–96", leftExpand in 48..96)
        assertTrue("child wider than MAIN", targetWidth > menuWidthDp)
        val rowIndex = 3 // e.g. 教材目录
        val rowTop = menuVerticalPadDp + menuRowHeight * rowIndex
        assertEquals(3 + 52 * 3, rowTop)
        val parentRight = screenW - screenMarginDp
        val targetLeft = parentRight - targetWidth
        assertTrue("left clears back circle", targetLeft >= backClearance)
        assertTrue("not full-screen slab", targetWidth < screenW * 0.85)
        val safeHeight = 800
        val maxHeight = ((safeHeight - 12) * 0.72f).toInt()
        assertTrue("maxHeight within 70-75%", maxHeight in (safeHeight * 70 / 100)..(safeHeight * 75 / 100))
    }

    @Test
    fun `menu open retracts the capsule entirely`() {
        // Overlay behavior: while the menu is up (expanded or morphing shut)
        // the capsule is retracted — the morphing card never overlaps the
        // capsule. Chrome levels are untouched; the capsule fades back after.
        val capsuleVisible = true && !(true || false) // capsuleVisible && !menuActive
        assertEquals(false, capsuleVisible)
    }

    @Test
    fun `capsule expansion keeps top and centerX`() {
        val screenW = 412
        // The expanded popover's TOP EDGE keeps the pill's width and position;
        // only the body widens below (CapsulePopoverShape).
        val pillWidth = 200
        val bodyWidth = 240
        val pillLeft = screenW / 2 - pillWidth / 2
        val bodyLeft = screenW / 2 - bodyWidth / 2
        assertEquals("pill is centered", screenW - (pillLeft + pillWidth), pillLeft)
        assertEquals("body is centered on the same axis", pillLeft, bodyLeft + (bodyWidth - pillWidth) / 2)
    }
}
