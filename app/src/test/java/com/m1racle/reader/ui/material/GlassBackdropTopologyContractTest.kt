package com.m1racle.reader.ui.material

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R32.3 regression contract for acyclic Backdrop ownership. */
class GlassBackdropTopologyContractTest {

    @Test
    fun `capture source masks its host before attaching layer backdrop`() {
        val source = appSource("ui/material/GlassBackdropTopology.kt").readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(normalized.contains("LocalGlassBackdropHost provides null"))
        assertTrue(normalized.contains("modifier = modifier.layerBackdrop(backdrop)"))
        assertTrue(
            normalized.indexOf("LocalGlassBackdropHost provides null") <
                normalized.indexOf("modifier.layerBackdrop(backdrop)"),
        )
    }

    @Test
    fun `all production capture nodes use the guarded ownership boundary`() {
        val javaRoot = appSourceRoot()
        val directCaptureOwners = javaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(Regex("""\.layerBackdrop\s*\(""")) }
            .map { it.relativeTo(javaRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(
            listOf("com/m1racle/reader/ui/material/GlassBackdropTopology.kt"),
            directCaptureOwners,
        )
    }

    @Test
    fun `home has no unused document backdrop capture`() {
        val home = appSource("ui/home/HomeScreen.kt").readText()
        assertFalse(home.contains("GlassBackdropSource("))
        assertFalse(home.contains("rememberLayerBackdrop("))
        assertFalse(home.contains("GlassDock("))
        assertTrue(home.contains("HomeContent("))
        assertTrue(home.contains("RecentDocumentList("))
    }

    @Test
    fun `dock is an L3 tab bar without a moving glass capsule`() {
        val dock = appSource("ui/hub/GlassDock.kt").readText()
        assertTrue(dock.contains("GlassMaterialRole.Dock"))
        assertTrue(dock.contains("dockSpec.surfaceColor"))
        assertTrue(dock.contains("M1racleGlassUsage.Dock"))
        assertTrue(dock.contains("selectableGroup()"))
        assertFalse(dock.contains("GlassMaterialRole.DockSelection"))
        assertFalse(dock.contains("glassMaterial("))
        assertFalse(dock.contains("DockCapsulePressedScale"))
        assertFalse(dock.contains("dockCapsuleScales("))
        assertFalse(dock.contains("sourceBackdrop = selectionBackdrop"))
        assertTrue(dock.contains("DockIndicatorWidth"))
        assertTrue(dock.contains("alpha = contentAlpha"))
        val engine = appSource("ui/material/GlassMaterial.kt").readText()
        assertTrue(engine.contains("sourceBackdrop: Backdrop? = null"))
        assertFalse("processed glass must not mix raw page detail back in", engine.contains("extraBackdrop"))
    }

    @Test
    fun `shared hub shell owns dock outside captured page content`() {
        val hub = appSource("ui/hub/GlassDock.kt").readText()
        assertFalse(hub.contains("rememberLayerBackdrop("))
        assertFalse(hub.contains("GlassBackdropSource("))
        val nav = appSource("ui/navigation/NavGraph.kt").readText()
        assertTrue(nav.contains("GlassDock("))
        assertTrue(nav.contains("HubDestination.isTopLevelRoute(currentEntry?.destination?.route)"))

        listOf("LibraryScreen.kt", "BookmarksScreen.kt", "HubSettingsScreen.kt").forEach { name ->
            val screen = appSource("ui/hub/$name").readText()
            assertTrue("$name delegates Dock ownership", screen.contains("HubGlassShell("))
            assertFalse("$name must not place its own Dock in captured content", screen.contains("GlassDock("))
        }
    }

    @Test
    fun `reader capture remains content-only with chrome as sibling overlays`() {
        val reader = appSource("ui/pdf/PdfViewerScreen.kt").readText()
        val sourceCall = trailingLambda(reader, "GlassBackdropSource(")

        assertEquals("content()", sourceCall.body.trim())
        assertFalse(sourceCall.body.contains("glassMaterial("))
        assertTrue(reader.indexOf("glassMaterial(", sourceCall.closeBrace) > sourceCall.closeBrace)
        assertTrue(reader.indexOf("ReaderMoreHost(", sourceCall.closeBrace) > sourceCall.closeBrace)
        val host = appSource("ui/pdf/ReaderMoreHost.kt").readText()
        assertTrue(host.contains("glassMaterial("))
        assertTrue(host.contains("GlassMaterialRole.Menu"))
    }

    @Test
    fun `pdf capture includes opaque surround and shares the frost plate`() {
        val reader = appSource("ui/pdf/PdfViewerScreen.kt").readText()
        val normalized = reader.replace(Regex("\\s+"), " ")
        assertTrue(normalized.contains("remember(backdropBackground) { { drawRect(backdropBackground) drawContent() } }"))
        assertTrue(normalized.contains("rememberLayerBackdrop( onDraw = drawPdfBackdrop,"))
        assertTrue(normalized.contains("GlassBackdropHost( root = rootBackdrop, menuExport = parentExportBackdrop, )"))
        assertFalse(reader.contains("overDocument"))
        val material = appSource("ui/material/GlassMaterial.kt").readText()
        assertTrue(material.contains("contrast = idleSpec.contrast"))
        assertFalse(material.contains("overDocument"))
        assertTrue(material.contains("vibrancy = true"))
    }

    @Test
    fun `dialog and menu retain isolated soft plate behavior`() {
        val dialog = appSource("ui/material/GlassAlertDialog.kt").readText()
        val menu = appSource("ui/home/DocumentListRow.kt").readText()

        assertTrue(dialog.contains("sampleBackdrop = false"))
        assertTrue(menu.contains("DropdownMenu("))
        assertTrue(menu.contains("GlassMaterialRole.Dock"))
        assertTrue(menu.contains("GlassCapability.SOLID_FALLBACK"))
        assertFalse(menu.contains(".glassMaterial("))
    }

    private data class TrailingLambda(
        val body: String,
        val closeBrace: Int,
    )

    private fun trailingLambda(source: String, call: String): TrailingLambda {
        val callStart = source.indexOf(call)
        assertTrue("missing $call", callStart >= 0)
        val lambdaOpen = source.indexOf("{", source.indexOf(")", callStart))
        assertTrue("missing trailing lambda for $call", lambdaOpen >= 0)
        var depth = 1
        var cursor = lambdaOpen + 1
        while (cursor < source.length && depth > 0) {
            when (source[cursor]) {
                '{' -> depth++
                '}' -> depth--
            }
            cursor++
        }
        assertEquals("unbalanced trailing lambda for $call", 0, depth)
        val close = cursor - 1
        return TrailingLambda(
            body = source.substring(lambdaOpen + 1, close),
            closeBrace = close,
        )
    }

    private fun appSource(relative: String): File = File(
        appSourceRoot(),
        "com/m1racle/reader/$relative",
    ).also { check(it.isFile) { "Missing source: $it" } }

    private fun appSourceRoot(): File = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
        File("/root/code/m1racle-reader/app/src/main/java"),
    ).first { it.isDirectory }
}
