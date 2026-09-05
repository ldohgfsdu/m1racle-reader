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
    fun `home dock is a sibling after captured content`() {
        val home = appSource("ui/home/HomeScreen.kt").readText()
        val sourceCall = trailingLambda(home, "GlassBackdropSource(")

        assertFalse(sourceCall.body.contains("GlassDock("))
        assertTrue(home.indexOf("GlassDock(", sourceCall.closeBrace) > sourceCall.closeBrace)
        assertTrue(sourceCall.body.contains("RecentDocumentList("))
    }

    @Test
    fun `shared hub shell owns dock outside captured page content`() {
        val hub = appSource("ui/hub/GlassDock.kt").readText()
        val sourceCall = trailingLambda(hub, "GlassBackdropSource(")

        assertEquals("content()", sourceCall.body.trim())
        assertFalse(sourceCall.body.contains("GlassDock("))
        assertTrue(hub.indexOf("GlassDock(", sourceCall.closeBrace) > sourceCall.closeBrace)

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
    }

    @Test
    fun `pdf capture includes opaque surround and a stable document material context`() {
        val reader = appSource("ui/pdf/PdfViewerScreen.kt").readText()
        val normalized = reader.replace(Regex("\\s+"), " ")
        assertTrue(normalized.contains("remember(backdropBackground) { { drawRect(backdropBackground) drawContent() } }"))
        assertTrue(normalized.contains("rememberLayerBackdrop( onDraw = drawPdfBackdrop,"))
        assertTrue(normalized.contains("menuExport = parentExportBackdrop, overDocument = true,"))
        val material = appSource("ui/material/GlassMaterial.kt").readText()
        assertTrue(material.contains("contrast = idleSpec.contrast"))
        assertTrue(material.contains("overDocument = overDocument"))
    }

    @Test
    fun `dialog and menu retain isolated soft plate behavior`() {
        val dialog = appSource("ui/material/GlassAlertDialog.kt").readText()
        val home = appSource("ui/home/HomeScreen.kt").readText()

        assertTrue(dialog.contains("sampleBackdrop = false"))
        assertTrue(home.contains("sampleBackdrop = false"))
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
