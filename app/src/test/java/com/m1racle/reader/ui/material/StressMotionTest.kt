package com.m1racle.reader.ui.material

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StressMotionTest {
    @Test
    fun `capture size and blur stay constant while translation moves in subpixel steps`() {
        val blur = 96f
        val frames = StressMotion.frames(
            steps = 180,
            stepPx = 0.25f,
            viewportWidthPx = 1080,
            viewportHeightPx = 900,
            blurPx = blur,
            sourceId = 7,
            effectId = 7,
        )
        assertEquals(180, frames.size)
        assertEquals(1, frames.map { it.captureWidth }.distinct().size)
        assertEquals(1, frames.map { it.captureHeight }.distinct().size)
        assertEquals(1, frames.map { it.sourceWidth }.distinct().size)
        assertEquals(1, frames.map { it.sourceHeight }.distinct().size)
        assertEquals(1, frames.map { it.blurPx }.distinct().size)
        assertEquals(1, frames.map { it.sourceId }.distinct().size)
        assertEquals(1, frames.map { it.effectId }.distinct().size)
        assertEquals(frames.map { it.sourceId }, frames.map { it.effectId })
        frames.zipWithNext().forEach { (previous, next) ->
            assertEquals(-0.25f, next.translationX - previous.translationX, 0f)
        }
        assertTrue(frames.any { it.translationX != StressMotion.quantizedScrollPx(-it.translationX).toFloat() * -1f })
    }

    @Test
    fun `integer scroll stalls then jumps on quarter-pixel steps`() {
        val quantized = (0..8).map { StressMotion.quantizedScrollPx(it * 0.25f) }
        assertTrue(quantized.zipWithNext().any { (left, right) -> left == right })
        assertTrue(quantized.zipWithNext().any { (left, right) -> right - left >= 1 })
    }

    @Test
    fun `preview moves the stress strip with a graphics layer and one backdrop`() {
        val preview = source("ui/material/BlurComparisonPreview.kt")
        assertTrue(preview.contains("translate(left = StressMotion.translationX(travel.value))"))
        assertFalse(preview.contains("graphicsLayer"))
        assertFalse(preview.contains("clipToBounds()"))
        assertFalse(preview.contains("placeRelativeWithLayer"))
        assertFalse(preview.contains("backdropScroll"))
        assertEquals(1, Regex("rememberLayerBackdrop\\(\\)").findAll(preview).count())
        assertTrue(preview.contains("GlassStressStage.RawText"))
        assertTrue(preview.contains("GlassStressStage.GlassNoBlur"))
        assertTrue(preview.contains("GlassStressStage.BlurOnly"))
        assertTrue(preview.contains("GlassStressTarget.ControlOnly"))
        assertTrue(preview.contains("GlassStressTarget.PanelOnly"))
        val renderer = source("ui/material/SoftGlassPreview.kt")
        assertTrue(renderer.contains("refractionEnabled = false"))
        assertFalse(PreviewTemporalIsolation.refractionEnabled)
    }

    private fun source(relative: String): String = File(readerRoot(), relative).readText()

    private fun readerRoot(): File = listOf(
        File("src/main/java/com/m1racle/reader"),
        File("app/src/main/java/com/m1racle/reader"),
    ).first { it.isDirectory }
}
