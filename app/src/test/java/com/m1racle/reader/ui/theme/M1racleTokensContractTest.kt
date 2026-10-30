package com.m1racle.reader.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared UI role contracts for the Radix visual system. */
class M1racleTokensContractTest {

    @Test
    fun `product typography keeps its roles within three UI sizes`() {
        val type = DefaultM1racleTypography
        assertEquals(18f, type.display.fontSize.value)
        assertEquals(14f, type.title.fontSize.value)
        assertEquals(14f, type.section.fontSize.value)
        assertEquals(14f, type.body.fontSize.value)
        assertEquals(12f, type.caption.fontSize.value)
        val names = M1racleTypography::class.java.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") && !it.contains("Companion") }
        assertEquals(listOf("display", "title", "section", "body", "caption"), names)
    }

    @Test
    fun `surface names match real screens`() {
        val light = m1racleSurface(dark = false)
        val dark = m1racleSurface(dark = true)
        assertEquals(PaperBackground, light.background)
        assertEquals(PaperBackground, light.list)
        assertEquals(PaperSurfaceContainerLow, light.hero)
        assertEquals(PaperSurface, light.overlay)
        assertEquals(InkBackground, dark.background)
        assertEquals(InkBackground, dark.list)
        assertEquals(InkSurfaceContainerLow, dark.hero)
        assertEquals(InkSurface, dark.overlay)
    }

    @Test
    fun `corner radii use nested 4dp increments`() {
        assertEquals(8f, M1racleCornerRadius.control.value)
        assertEquals(12f, M1racleCornerRadius.card.value)
        assertEquals(16f, M1racleCornerRadius.container.value)
    }

    @Test
    fun `overlay elevation is about 30 percent of current frost shadow`() {
        assertEquals(0f, M1racleElevation.none.value)
        assertEquals(2f, M1racleElevation.overlay.value)
        assertTrue(M1racleElevation.overlay.value <= 6f * 0.35f)
    }

    @Test
    fun `glass policy allows only toolbar dock and menu`() {
        assertEquals(
            setOf(
                M1racleGlassUsage.Toolbar,
                M1racleGlassUsage.Dock,
                M1racleGlassUsage.Menu,
            ),
            M1racleGlassPolicy.allowed,
        )
        assertEquals(3, M1racleGlassUsage.entries.size)
        assertTrue(M1racleGlassPolicy.allows(M1racleGlassUsage.Toolbar))
        assertFalse(M1racleGlassPolicy.allowed.any { it.name == "Capsule" })
        assertFalse(M1racleGlassPolicy.allowed.any { it.name == "Control" })
    }

    @Test
    fun `necessary motion shares 250ms ease out`() {
        assertEquals(250, M1racleMotion.FastMs)
        assertEquals(250, M1racleMotion.NormalMs)
        assertEquals(250, M1racleMotion.SlowMs)
        assertEquals(250, M1racleMotion.fast.durationMillis)
        assertEquals(250, M1racleMotion.normal.durationMillis)
        assertEquals(250, M1racleMotion.slow.durationMillis)
        assertEquals(M1racleMotion.easeOut, M1racleMotion.fast.easing)
        assertEquals(M1racleMotion.easeOut, M1racleMotion.normal.easing)
        assertEquals(M1racleMotion.easeOut, M1racleMotion.slow.easing)
        assertEquals(M1racleMotion.easeIn, M1racleMotion.fastIn.easing)
        assertEquals(M1racleMotion.easeIn, M1racleMotion.normalIn.easing)
        assertEquals(0f, M1racleMotion.easeOut.transform(0f), 0f)
        assertEquals(1f, M1racleMotion.easeOut.transform(1f), 0f)
        assertTrue(M1racleMotion.easeOut.transform(0.5f) > 0.5f)
        assertTrue(M1racleMotion.easeIn.transform(0.5f) > 0.5f)
    }

    @Test
    fun `material typography shares the three UI sizes`() {
        val live = M1racleReaderTypography
        assertEquals(18f, live.headlineSmall.fontSize.value)
        assertEquals(14f, live.bodyLarge.fontSize.value)
        assertEquals(12f, live.bodySmall.fontSize.value)
    }
}
