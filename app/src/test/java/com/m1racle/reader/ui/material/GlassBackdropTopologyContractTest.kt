package com.m1racle.reader.ui.material

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R32.3 regression contract for acyclic Backdrop ownership. */
class GlassBackdropTopologyContractTest {

    @Test
    fun `capture source masks its host before attaching layer backdrop`() {
        val source = appSource("ui/material/GlassBackdropTopology.kt").readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        assertTrue(normalized.contains("LocalGlassBackdropHost provides null"))
        assertTrue(normalized.contains("modifier = if (enabled) modifier.layerBackdrop(backdrop) else modifier"))
        assertTrue(
            normalized.indexOf("LocalGlassBackdropHost provides null") <
                normalized.indexOf("modifier.layerBackdrop(backdrop)"),
        )
    }

    @Test
    fun `all production capture nodes use the guarded ownership boundary`() {
        val javaRoot = ProductionSource.javaRoot()
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
    fun `dock samples page and selection samples exported material plus isolated icons`() {
        val dock = appSource("ui/hub/GlassDock.kt").readText()
        assertTrue(dock.contains("sourceBackdrop = selectionBackdrop"))
        assertTrue(dock.contains("surfaceExport = barExport"))
        assertTrue(dock.contains("rememberCombinedBackdrop(barExport, iconBackdrop)"))
        assertTrue(dock.contains("GlassMaterialRole.SelectionLens"))
        val capture = dock.substringAfter("GlassBackdropSource(iconBackdrop,").substringBefore("val lensWidth")
        assertTrue(capture.contains("DockTabVisual("))
        assertFalse(capture.contains("glassMaterial("))
        assertTrue(dock.contains(".alpha(0f).clearAndSetSemantics {}"))
        assertTrue(dock.contains("blendMode = BlendMode.Clear"))
        assertFalse(dock.contains("sampleBackdrop = false"))
        assertFalse(dock.contains(".background(MaterialTheme.colorScheme.background"))
        assertTrue(dock.contains("layerBlock = lensTransform"))
        assertTrue(dock.contains("selectableGroup()"))
    }

    @Test
    fun `hub capture includes opaque page background before content across all routes`() {
        val nav = appSource("ui/navigation/NavGraph.kt").readText()
        val compact = nav.replace(Regex("\\s+"), " ")
        assertTrue(compact.contains("val hubBackground = MaterialTheme.colorScheme.background"))
        assertTrue(compact.contains("remember(hubBackground) { { drawRect(hubBackground) drawContent() } }"))
        assertTrue(compact.contains("rememberLayerBackdrop(onDraw = drawHubBackdrop)"))
        assertFalse(nav.contains("val hubBackdrop = rememberLayerBackdrop()"))
    }

    @Test
    fun `shared hub shell owns dock outside captured page content`() {
        val hub = appSource("ui/hub/GlassDock.kt").readText()
        val shell = hub.substringAfter("fun HubGlassShell(").substringBefore("private data class DockEntry")
        assertFalse(shell.contains("GlassBackdropSource("))
        assertFalse(shell.contains("rememberLayerBackdrop("))
        assertFalse(shell.contains("LocalGlassBackdropHost"))
        val nav = appSource("ui/navigation/NavGraph.kt").readText()
        val capture = trailingLambda(nav, "GlassBackdropSource(")
        assertTrue(capture.body.contains("NavHost("))
        assertFalse(capture.body.contains("GlassDock("))
        assertTrue(nav.indexOf("GlassDock(", capture.closeBrace) > capture.closeBrace)
        assertTrue(nav.contains("HubDestination.isTopLevelRoute(currentEntry?.destination?.route)"))
        listOf("LibraryScreen.kt", "BookmarksScreen.kt", "HubSettingsScreen.kt").forEach { name ->
            val screen = appSource("ui/hub/$name").readText()
            assertTrue(screen.contains("HubGlassShell("))
            assertFalse(screen.contains("GlassDock("))
        }
    }

    @Test
    fun `hub scrolling reaches gesture area with navigation clearance at content tail`() {
        for (name in listOf("LibraryScreen.kt", "BookmarksScreen.kt", "HubSettingsScreen.kt")) {
            val screen = appSource("ui/hub/$name").readText()
            assertFalse(name, screen.contains(".navigationBarsPadding()"))
            assertTrue(name, screen.contains("WindowInsets.navigationBars.asPaddingValues()"))
            assertTrue(name, screen.contains("bottom = HomeDesignTokens.DockContentClearance + navBottom"))
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

    private fun appSource(relative: String): File =
        ProductionSource.file("com/m1racle/reader/$relative")
}
