package com.m1racle.reader.ui.material

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassAuthorityContractTest {

    @Test
    fun `every role resolves for environment and capability`() {
        for (role in GlassMaterialRole.entries) {
            for (env in GlassEnvironment.entries) {
                for (cap in GlassCapability.entries) {
                    val spec = GlassMaterialEngine.resolve(role, env, cap)
                    assertEquals(env, spec.environment)
                    assertEquals(cap, spec.capability)
                    assertEquals(1f, spec.foreground.contentAlpha, 0f)
                    assertEquals(spec.foreground.mutedAlpha, spec.contentMutedAlpha, 0f)
                    assertTrue(spec.surfaceColor.alpha in 0f..1f)
                    val expectLens = role == GlassMaterialRole.PreviewCapsule &&
                        cap == GlassCapability.FULL_OPTICAL
                    assertEquals(expectLens, spec.lensEnabled)
                }
            }
        }
    }

    @Test
    fun `surface alpha does not drive foreground alpha`() {
        val a = GlassMaterialEngine.resolve(GlassMaterialRole.Control, dark = false)
        val b = GlassMaterialEngine.resolve(GlassMaterialRole.Dialog, dark = false)
        assertTrue(a.surfaceColor.alpha < b.surfaceColor.alpha)
        assertEquals(a.foreground.contentAlpha, b.foreground.contentAlpha, 0f)
        assertEquals(1f, a.foreground.contentAlpha, 0f)
    }

    @Test
    fun `menu idle is not classified as interaction`() {
        val idle = GlassMaterialEngine.resolve(GlassMaterialRole.Menu, dark = false)
        val open = GlassMaterialEngine.resolve(
            GlassMaterialRole.Menu,
            dark = false,
            interaction = true,
        )
        assertEquals(GlassRenderState.IDLE, idle.renderState)
        assertEquals(GlassInteractionState.IDLE, idle.interaction)
        assertEquals(GlassRenderState.INTERACTION, open.renderState)
        assertEquals(GlassInteractionState.EXPANDED, open.interaction)
    }

    @Test
    fun `no orphan dock selection recipe remains`() {
        assertFalse(GlassMaterialRole.entries.any { it.name == "DockSelection" })
        assertEquals(GlassMaterialRole.entries.toSet(), GlassMaterialEngine.rolesWithConsumers())
        assertFalse(appSource("ui/material/GlassMaterial.kt").contains("DockSelection"))
    }

    @Test
    fun `markdown chrome and settings preview do not own blur tint literals`() {
        val gradient = appSource("ui/viewer/ReaderGradient.kt")
        assertTrue(gradient.contains("GlassMaterialRole.ProgressiveChrome"))
        assertFalse(gradient.contains("0.62f"))
        assertFalse(gradient.contains("16.dp"))
        val preview = appSource("ui/material/BlurComparisonPreview.kt")
        assertTrue(preview.contains("GlassPreviewPreset"))
        assertFalse(preview.contains("4.32.dp"))
        assertFalse(preview.contains("drawBackdrop(backdrop"))
    }

    @Test
    fun `cross-window surfaces resolve through the engine`() {
        val dialog = appSource("ui/material/GlassAlertDialog.kt")
        assertTrue(dialog.contains("GlassMaterialRole.Dialog"))
        assertTrue(dialog.contains("sampleBackdrop = false"))
        val row = appSource("ui/home/DocumentListRow.kt")
        assertTrue(row.contains("GlassCapability.SOLID_FALLBACK"))
        val viewer = appSource("ui/viewer/ViewerScreen.kt")
        assertTrue(viewer.contains("sampleBackdrop = false"))
        assertTrue(viewer.contains("GlassMaterialRole.ExpandedMenu"))
    }

    @Test
    fun `document backdrop helper exists and chrome stays outside source`() {
        val topo = appSource("ui/material/GlassBackdropTopology.kt")
        assertTrue(topo.contains("fun DocumentBackdropSource("))
        val pdf = appSource("ui/pdf/PdfViewerScreen.kt")
        val start = pdf.indexOf("GlassBackdropSource(")
        val open = pdf.indexOf("{", pdf.indexOf(")", start))
        var depth = 1
        var i = open + 1
        while (i < pdf.length && depth > 0) {
            when (pdf[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        val body = pdf.substring(open + 1, i - 1)
        assertEquals("content()", body.trim())
    }

    private fun appSource(relative: String): String = File(
        appSourceRoot(),
        "com/m1racle/reader/$relative",
    ).also { check(it.isFile) { "Missing source: $it" } }.readText()

    private fun appSourceRoot(): File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).first { it.isDirectory }
}
