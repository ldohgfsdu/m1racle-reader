package com.m1racle.reader.ui.home

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home: solid tonal cards + bare ··· overflow + ONE unified 24dp content rail.
 * Floating controls (FAB / ··· menu) reuse the SHARED Reader Liquid Glass
 * surface (Control level) — cards themselves never liquid-coat.
 */
class HomeLayoutContractTest {

    @Test
    fun `home tokens in product bands`() {
        // ONE unified rail: page margin 24 for brand text, section title, cards.
        assertEquals(24f, HomeDesignTokens.PageHorizontal.value)
        // Card: light tonal, radius 18–20, gap 8–10,
        // internal h 16 / top 10–12 / bottom 8–10.
        assertTrue(HomeDesignTokens.RadiusCard.value in 18f..20f)
        assertTrue(HomeDesignTokens.CardGap.value in 8f..10f)
        assertEquals(16f, HomeDesignTokens.CardPaddingH.value)
        assertTrue(HomeDesignTokens.CardPaddingTop.value in 10f..12f)
        assertTrue(HomeDesignTokens.CardPaddingBottom.value in 8f..10f)
        // 最近文件 → first card tightened 6–10dp from the uniform 8dp gap.
        assertTrue(HomeDesignTokens.HeaderToFirstCardGap.value in 0f..4f)
        assertTrue(8f - HomeDesignTokens.HeaderToFirstCardGap.value in 6f..10f)
        // Card rhythm: title→type 5, type→meta 4, meta→progress 7.
        assertEquals(5f, HomeDesignTokens.TitleToTypeGap.value)
        assertEquals(4f, HomeDesignTokens.TypeToMetaGap.value)
        assertEquals(7f, HomeDesignTokens.MetaToProgressGap.value)
        assertTrue(HomeDesignTokens.ProgressHeight.value in 3f..4f)
        // Title ~18sp Semibold / lineHeight ~21sp.
        assertTrue(HomeDesignTokens.FileTitleSize.value in 17f..19f)
        assertTrue(HomeDesignTokens.FileTitleLine.value in 20f..23f)
        // Bare overflow: 44–48dp invisible touch, 20–22dp visible glyph.
        assertTrue(HomeDesignTokens.OverflowTouch.value in 44f..48f)
        val visibleIcon = HomeDesignTokens.OverflowTouch.value -
            2f * HomeDesignTokens.OverflowIconPad.value
        assertTrue(visibleIcon in 20f..22f)
        // FAB: 56–60dp rounded square, radius 18–20, plus icon 24–28.
        assertTrue(HomeDesignTokens.FabSize.value in 56f..60f)
        assertTrue(HomeDesignTokens.FabCorner.value in 18f..20f)
        assertTrue(HomeDesignTokens.FabIcon.value in 24f..28f)
        // Utility menu: width 140–160, corner = PDF chrome menu-row corner,
        // row 48, row pad 16, icon 20–22, icon gap 12–14.
        assertTrue(HomeDesignTokens.MenuMinWidth.value in 140f..160f)
        assertTrue(HomeDesignTokens.MenuMaxWidth.value in 140f..160f)
        assertEquals(10f, HomeDesignTokens.MenuCorner.value)
        assertEquals(48f, HomeDesignTokens.MenuRowHeight.value)
        assertEquals(16f, HomeDesignTokens.MenuRowPadH.value)
        assertTrue(HomeDesignTokens.MenuItemIcon.value in 20f..22f)
        assertTrue(HomeDesignTokens.MenuIconGap.value in 12f..14f)
        // Menu right-edge aligns with the ··· anchor (min width − touch).
        assertEquals(
            HomeDesignTokens.MenuMinWidth.value - HomeDesignTokens.OverflowTouch.value,
            HomeDesignTokens.MenuAnchorOverhang.value,
            0.001f,
        )
    }

    @Test
    fun `liquid only on floating controls - cards stay solid`() {
        val home = homeSource()
        // Floating controls consume semantic roles from the shared engine.
        assertTrue(home.contains("glassMaterial"))
        assertTrue(home.contains("GlassMaterialRole.Control"))
        assertTrue(home.contains("GlassMaterialRole.Menu"))
        assertTrue(home.contains("rememberLayerBackdrop()"))
        assertTrue(home.contains("GlassBackdropSource("))
        assertTrue(home.contains("backdrop = rootBackdrop"))
        assertTrue(home.contains("LocalGlassBackdropHost"))
        // FAB = liquid rounded square (no Material FloatingActionButton).
        assertTrue(home.contains("GlassHomeFab("))
        assertFalse(home.contains("FloatingActionButton("))
        // ··· menu = Popup with the shared liquid surface (soft plate — no
        // cross-window sampling), not Material DropdownMenu.
        assertTrue(home.contains("Popup("))
        assertTrue(home.contains("sampleBackdrop = false"))
        assertFalse(home.contains("DropdownMenu("))
        // Cards remain solid tonal — liquid never coats the card shell.
        assertTrue(home.contains("surfaceContainerLow"))
        assertTrue(home.contains("HomeDesignTokens.CardShape"))
        assertFalse(
            Regex("""Surface\([\s\S]{0,200}glassMaterial""").containsMatchIn(home),
        )
    }

    @Test
    fun `one content rail - brand text cards and empty state share 24dp inset`() {
        val home = homeSource()
        // List state: LazyColumn contentPadding uses the same PageHorizontal.
        assertTrue(home.contains("start = HomeDesignTokens.PageHorizontal"))
        assertTrue(home.contains("end = HomeDesignTokens.PageHorizontal"))
        // Empty state: header gets the same rail (plain Column has no padding).
        assertTrue(
            Regex(
                """modifier\s*=\s*Modifier\.padding\(\s*horizontal\s*=\s*HomeDesignTokens\.PageHorizontal,?\s*\)""",
            ).containsMatchIn(home),
        )
    }

    @Test
    fun `card height compressed 5-8 percent from 93dp baseline`() {
        // Single-line-title card height = paddings + title lh + gaps + rows + progress.
        val height = HomeDesignTokens.CardPaddingTop.value +
            HomeDesignTokens.FileTitleLine.value +
            HomeDesignTokens.TitleToTypeGap.value +
            HomeDesignTokens.FileTypeLine.value +
            HomeDesignTokens.TypeToMetaGap.value +
            HomeDesignTokens.MetaLine.value +
            HomeDesignTokens.MetaToProgressGap.value +
            HomeDesignTokens.ProgressHeight.value +
            HomeDesignTokens.CardPaddingBottom.value
        // 93dp (previous round) → ~88dp = -5.4%.
        val percent = (93f - height) / 93f
        assertTrue("expected 5-8% compression, got $percent", percent in 0.05f..0.08f)
        // Title font size unchanged (18sp) — compression must not come from
        // drastically shrinking the title.
        assertEquals(18f, HomeDesignTokens.FileTitleSize.value)
    }

    @Test
    fun `overflow icon is bare and does not inflate card height`() {
        val home = homeSource()
        assertTrue(home.contains("OverflowMenuButton("))
        assertTrue(home.contains("HomeDesignTokens.OverflowTouch"))
        // Overlay layout: text block end-padded by touch width + TopEnd ···,
        // so the invisible 48dp target never forces a taller row.
        assertTrue(home.contains("align(Alignment.TopEnd)"))
        assertTrue(home.contains("padding(end = HomeDesignTokens.OverflowTouch)"))
        // No glass/surface wrapping for the ···.
        assertFalse(Regex("""OverflowMenuButton[\s\S]{0,400}Surface\(""").containsMatchIn(home))
    }

    @Test
    fun `delete menu is utility sized and right-edge aligned to anchor`() {
        val home = homeSource()
        assertTrue(home.contains("MenuMinWidth"))
        assertTrue(home.contains("MenuMaxWidth"))
        assertTrue(home.contains("MenuAnchorOverhang"))
        // Popup positioned in px relative to the ··· anchor, expanding leftward.
        assertTrue(home.contains("IntOffset(x = offsetX, y = offsetY)"))
        assertTrue(home.contains("HomeDesignTokens.MenuItemIcon"))
        assertTrue(home.contains("GlassMaterialRole.Menu"))
    }

    @Test
    fun `single lazy column - brand header scrolls with list, no clip boundary`() {
        val home = homeSource()
        // Header is the first item of the SAME LazyColumn as the cards.
        assertTrue(home.contains("item(key = \"home_brand_header\")"))
        assertTrue(home.contains("LazyColumn("))
        // Per-item spacing: tight header→card gap, uniform card gap.
        assertTrue(home.contains("HeaderToFirstCardGap"))
        assertFalse(home.contains("spacedBy"))
        // No fixed header sibling above the list, no sticky layer, no nested list.
        assertFalse(home.contains("HomeHeader(statusTop"))
        assertFalse(home.contains("stickyHeader"))
        assertFalse(home.contains("nestedScroll"))
    }

    @Test
    fun `list uses stable document id keys and no animateItem placeholder`() {
        val home = homeSource()
        val vm = homeSourceVm()
        assertTrue(home.contains("key = { doc -> doc.id }"))
        assertFalse(home.contains("animateItem"))
        assertFalse(home.contains("AnimatedVisibility"))
        assertTrue(vm.contains("pendingDeleteIds"))
        assertTrue(vm.contains("HomeListUpdate.visibleDocuments"))
    }

    @Test
    fun `viewModel delete is optimistic`() {
        val vm = homeSourceVm()
        assertTrue(vm.contains("pendingDeleteIds"))
        assertTrue(vm.contains("HomeListUpdate.visibleDocuments"))
        assertTrue(vm.contains("deleteById"))
    }

    private fun homeSource(): String = listOf(
        File("app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt"),
        File("src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt"),
        File("/root/code/m1racle-reader/app/src/main/java/com/m1racle/reader/ui/home/HomeScreen.kt"),
    ).first { it.isFile }.readText()

    private fun homeSourceVm(): String = listOf(
        File("app/src/main/java/com/m1racle/reader/ui/home/HomeViewModel.kt"),
        File("src/main/java/com/m1racle/reader/ui/home/HomeViewModel.kt"),
        File("/root/code/m1racle-reader/app/src/main/java/com/m1racle/reader/ui/home/HomeViewModel.kt"),
    ).first { it.isFile }.readText()
}
