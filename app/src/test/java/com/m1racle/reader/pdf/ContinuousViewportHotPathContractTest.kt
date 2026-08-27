package com.m1racle.reader.pdf

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification: STRUCTURAL_GUARD_ONLY. Source shape only; never runtime evidence. */
class ContinuousViewportHotPathContractTest {

    private fun source(path: String): String =
        File("app/src/main/java/com/m1racle/reader/$path").takeIf { it.isFile }
            ?.readText()
            ?: File("src/main/java/com/m1racle/reader/$path").readText()

    private fun viewport() = source("ui/pdf/ContinuousDocumentViewport.kt")
    private fun viewModel() = source("ui/pdf/PdfViewerViewModel.kt")
    private fun screen() = source("ui/pdf/PdfViewerScreen.kt")

    @Test
    fun `drive effect is a single snapshotFlow collector not per-frame relaunch`() {
        val vp = viewport()
        assertFalse(vp.contains("LaunchedEffect(documentState.scrollX"))
        assertTrue(vp.contains("snapshotFlow {"))
        assertTrue(vp.contains("ContinuousViewportDriveInputs("))
    }

    @Test
    fun `collector reads per-composition values through state holders`() {
        val vp = viewport()
        assertTrue(vp.contains("val currentVisiblePages by rememberUpdatedState(visiblePages)"))
        assertTrue(vp.contains("val currentPrimaryAnchorPage by rememberUpdatedState(primaryPage)"))
        assertTrue(vp.contains("currentOnViewportChanged("))
    }

    @Test
    fun `gesture payload no longer double-copies page sets`() {
        val vp = viewport()
        assertFalse(vp.contains("composedPages.toSet(),"))
        assertFalse(vp.contains("visiblePages.toSet(),"))
    }

    @Test
    fun `measure path reads prebuilt tile index instead of filtering per pass`() {
        val vp = viewport()
        assertTrue(vp.contains("buildContinuousTileIndex("))
        assertTrue(vp.contains("tiles = tileIndex.forPage(page),"))
        assertFalse(vp.contains("PdfTileDisplay.forPage(drawableTiles, page)"))
        assertTrue(vp.contains("subcompose(page)"))
    }

    @Test
    fun `planner coverage uses zero-copy key view`() {
        val vm = viewModel()
        assertFalse(vm.contains("tileCache.snapshot().keys"))
        assertTrue(vm.contains("tileCache.keySet()"))
    }

    @Test
    fun `horizontal strip groups tiles once per publication`() {
        val sc = screen()
        assertFalse(sc.contains("horizontalOverlaySnapshot.entries.values.filter"))
        assertTrue(sc.contains("horizontalTilesByPage"))
    }
}
