package com.m1racle.reader.ui.pdf

import com.m1racle.reader.ui.material.GlassMaterialEngine
import com.m1racle.reader.ui.material.GlassMaterialRole
import com.m1racle.reader.ui.material.GlassRenderState
import com.m1racle.reader.testing.ProductionSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression gates for the single Glass Material Engine authority. */
class ReaderChromeTokensContractTest {

    @Test
    fun `menu text has no independent geometric animation`() {
        val root = listOf(File("src/main/java/com/m1racle/reader/ui/pdf"),
            File("app/src/main/java/com/m1racle/reader/ui/pdf")).first { it.isDirectory }
        val main = File(root, "ReaderMoreHost.kt").readText()
        assertFalse(main.contains("contentScale"))
        assertTrue(main.contains("wrapContentWidth(Alignment.End, unbounded = true)"))
        val nested = File(root, "AnchoredNestedPopover.kt").readText()
        assertFalse(nested.contains("bodySlidePx"))
        assertFalse(nested.contains("translationY ="))
    }

    @Test
    fun `every product role has a complete bounded recipe`() {
        assertEquals(9, GlassMaterialRole.entries.size)
        assertFalse(GlassMaterialRole.entries.any { it.name.startsWith("Preview") })
        GlassMaterialRole.entries.forEach { role ->
            listOf(false, true).forEach { dark ->
                val spec = GlassMaterialEngine.resolve(role, dark)
                assertTrue(spec.surfaceColor.alpha in 0f..1f)
                assertEquals(1f, spec.foreground.contentAlpha, 0f)
                assertEquals(spec.contentMutedAlpha, spec.foreground.mutedAlpha, 0f)
                val expectedBlur = when (role) {
                    GlassMaterialRole.Dock -> 4f
                    GlassMaterialRole.SelectionLens -> 0f
                    GlassMaterialRole.ExpandedMenu -> 24f
                    GlassMaterialRole.Dialog -> 18f
                    GlassMaterialRole.ProgressiveChrome -> 16f
                    else -> 4f
                }
                assertEquals("$role blur", expectedBlur, spec.blur.value, 0f)
                val expectedShadow = when (role) {
                    GlassMaterialRole.Dock -> 8f
                    GlassMaterialRole.SelectionLens, GlassMaterialRole.ProgressiveChrome -> 0f
                    GlassMaterialRole.Dialog -> 18f
                    else -> 8f
                }
                assertEquals("$role shadow", expectedShadow, spec.shadow.value, 0f)
                val expectedHighlightW = when (role) {
                    GlassMaterialRole.Dialog -> 0.5f
                    GlassMaterialRole.ProgressiveChrome -> 0f
                    else -> 0.75f
                }
                assertEquals(expectedHighlightW, spec.highlightWidth.value, 0.001f)
                val expectedHighlightA = when (role) {
                    // User requested the current PDF control recipe on 2026-10-11.
                    GlassMaterialRole.Dock -> 0.60f
                    GlassMaterialRole.SelectionLens -> 0f
                    GlassMaterialRole.Dialog -> if (dark) 0.38f else 0.30f

                    GlassMaterialRole.ProgressiveChrome -> 0f
                    else -> 0.60f
                }
                assertEquals("$role highlight (dark=$dark)", expectedHighlightA, spec.highlightColor.alpha, 0.004f)
                assertTrue(spec.contentDividerAlpha in 0.04f..0.08f)
                assertTrue(spec.contentPressedOverlayAlpha in 0.04f..0.05f)
                assertEquals(1f, spec.scale, 0.001f)
            }
        }
    }

    @Test
    fun `dialog plate isolates content while retaining translucency`() {
        listOf(false, true).forEach { dark ->
            val dialog = GlassMaterialEngine.resolve(GlassMaterialRole.Dialog, dark)
            val settings = GlassMaterialEngine.resolve(GlassMaterialRole.SettingsCard, dark)
            assertEquals(0.97f, dialog.surfaceColor.alpha, 0.002f)
            assertTrue(dialog.surfaceColor.alpha < 1f)
            val expectedHierarchyGap = if (dark) 0.33f else 0.37f
            assertEquals(expectedHierarchyGap, dialog.surfaceColor.alpha - settings.surfaceColor.alpha, 0.004f)
        }
    }

    @Test
    fun `pressed state is resolved without changing component geometry`() {
        val rest = GlassMaterialEngine.resolve(GlassMaterialRole.Control, dark = false)
        val pressed = GlassMaterialEngine.resolve(
            GlassMaterialRole.Control,
            dark = false,
            pressed = true,
        )
        assertEquals(0.08f, pressed.surfaceColor.alpha - rest.surfaceColor.alpha, 0.01f)
        assertEquals(0.70f, pressed.highlightColor.alpha, 0.004f)
        assertEquals(rest.blur, pressed.blur)
        assertEquals(rest.shadow, pressed.shadow)
        assertEquals(1.085f, pressed.scale, 0.001f)
        assertEquals(GlassRenderState.IDLE, rest.renderState)
        assertEquals(GlassRenderState.INTERACTION, pressed.renderState)
    }

    @Test
    fun `expanded material roles resolve as interaction while compact roles idle`() {
        assertEquals(
            GlassRenderState.IDLE,
            GlassMaterialEngine.resolve(GlassMaterialRole.Capsule, dark = false).renderState,
        )
        assertEquals(
            GlassRenderState.INTERACTION,
            GlassMaterialEngine.resolve(
                GlassMaterialRole.Capsule,
                dark = false,
                interaction = true,
            ).renderState,
        )
        assertEquals(
            GlassRenderState.IDLE,
            GlassMaterialEngine.resolve(GlassMaterialRole.Menu, dark = false).renderState,
        )
        assertEquals(
            GlassRenderState.INTERACTION,
            GlassMaterialEngine.resolve(
                GlassMaterialRole.Menu,
                dark = false,
                interaction = true,
            ).renderState,
        )
    }

    @Test
    fun `single renderer applies tint highlight shadow blur and lens`() {
        val engine = materialSource()
        assertTrue(engine.contains("lerp(idleSpec.surfaceColor, pressedSpec.surfaceColor, progress)"))
        assertTrue(engine.contains("pressedSpec.highlightColor.alpha - idleSpec.highlightColor.alpha"))
        assertTrue(engine.contains("colorControls("))
        assertTrue(engine.contains("blur(blurPx)"))
        assertTrue(engine.contains("lens("))
        assertTrue(engine.contains("Highlight.Default.copy("))
        assertTrue(engine.contains("dropShadow("))
        assertTrue(engine.contains("shadow = null"))
        assertTrue(engine.contains("else -> GlassMaterialEngine.PressSpring"))
        assertTrue(engine.contains("layerBlock = if (pressLayerActive || layerBlock != null)"))
        assertTrue(engine.contains("layerBlock?.invoke(this)"))
        assertTrue(engine.contains("scaleX *= scale"))
        assertTrue(engine.contains("scaleY *= scale"))
        assertTrue(engine.contains("val resourceModifier = remember("))
        assertTrue(engine.contains("return modifier.then(resourceModifier)"))
        assertFalse(engine.contains("ReaderChromeTokens"))
    }

    @Test
    fun `all app dialogs use unified dialog component`() {
        val files = listOf(
            appSource("com/m1racle/reader/MainActivity.kt"),
            sourceFile("OutlineEditDialog.kt"),
            sourceFile("ChapterExportDialogs.kt"),
            sourceFile("EmbeddedOutlineImportDialog.kt"),
        )
        files.forEach { file ->
            val source = file.readText()
            assertTrue("${file.name} must use GlassAlertDialog", source.contains("GlassAlertDialog("))
            assertFalse(source.contains("containerColor = scheme.dialogContainer"))
            assertFalse(source.contains("containerColor = MaterialTheme.colorScheme.dialogContainer"))
        }
        val dialog = appSource(
            "com/m1racle/reader/ui/material/GlassAlertDialog.kt",
        ).readText()
        assertTrue(dialog.contains("GlassMaterialRole.Dialog"))
        assertTrue(dialog.contains("BasicAlertDialog("))
        assertTrue(dialog.contains("sampleBackdrop = false"))
    }

    @Test
    fun `old material authorities are removed`() {
        assertFalse(ProductionSource.resolve("com/m1racle/reader/ui/pdf/ReaderChromeGlass.kt").exists())
        assertFalse(ProductionSource.resolve("com/m1racle/reader/ui/pdf/ReaderChromeMaterial.kt").exists())
        assertFalse(ProductionSource.resolve("com/m1racle/reader/ui/common/M1racleReaderLiquidSurface.kt").exists())
    }

    @Test
    fun `page mapping geometry and input behavior remain unchanged`() {
        val card = sourceFile("ReaderAdvancedCard.kt").readText()
        // 44dp save/confirm height is pinned by value (now via LayoutSpec token).
        assertTrue(card.contains("ButtonHeightDp.dp)"))
        assertFalse(card.contains("height(44.dp)"))
        val spec = sourceFile("AdvancedChildLayoutSpec.kt").readText()
        assertTrue(spec.contains("ButtonHeightDp = 44f"))
        assertTrue(card.contains("ImeAction.Done"))
        assertFalse(card.contains("requestFocus()"))
        assertFalse(card.contains("showSoftInput"))
        assertTrue(card.contains("TextbookPageAdvancedContent"))
        assertTrue(card.contains("verticalScroll(rememberScrollState())"))
        assertTrue(card.contains("AdvancedChildLayoutSpec.InputCornerDp"))
        assertTrue(card.contains("AdvancedChildLayoutSpec.ActionCornerDp"))
    }

    @Test
    fun `reader transition state wiring remains unchanged`() {
        val host = sourceFile("ReaderMoreHost.kt").readText()
        val screen = sourceFile("PdfViewerScreen.kt").readText()
        assertTrue(host.contains("readingModeProgress.snapTo(0f)"))
        assertTrue(host.contains("topBarProgress.snapTo(0f)"))
        assertTrue(host.contains("canonicalMainBounds") || host.contains("nestedActive"))
        assertTrue(screen.contains("ReaderMoreTransitions.onSectionToggle"))
        assertTrue(screen.contains("pendingInlineAfterChild"))
        assertTrue(screen.contains("ReaderMoreTransitions.onBack"))
        assertTrue(screen.contains("ReaderMoreTransitions.afterChildCollapsed"))
    }

    private fun materialSource(): String = appSource(
        "com/m1racle/reader/ui/material/GlassMaterial.kt",
    ).readText()

    private fun sourceFile(name: String): File = ProductionSource.file(
        "com/m1racle/reader/ui/pdf/$name",
    )

    private fun appSource(relative: String): File = ProductionSource.file(relative)
}
