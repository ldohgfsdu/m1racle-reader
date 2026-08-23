package com.markleaf.app.pdf

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification: STRUCTURAL_GUARD_ONLY. Source shape only; never runtime evidence. */
class R71FrameStabilityTest {
    @Test fun `page draws frozen displayed bitmap not Ready bitmap`() {
        val source = viewportSource()
        assertTrue(source.contains("val bitmap = displayedBitmaps[page]"))
        assertFalse(source.contains("val bitmap = state.pageBitmaps[page]"))
    }

    @Test fun `pinching and settling use stable visible pages`() {
        val source = viewportSource()
        assertTrue(source.contains("interaction.stableVisiblePages"))
        assertTrue(source.contains("ContinuousInteractionReducer.appendPages"))
    }

    @Test fun `settle debounce is transaction owned and reusable`() {
        val source = viewportSource()
        assertTrue(source.contains("SETTLE_CANCEL"))
        assertTrue(source.contains("INTERACTION_REUSE"))
        assertTrue(source.contains("ContinuousInteractionReducer.SETTLE_DEBOUNCE_MS"))
    }

    @Test fun `render completion defers Ready publish while interaction active`() {
        val source = viewModelSource()
        val pending = source.indexOf("pendingContinuousCachePublish = true")
        val publish = source.indexOf("_uiState.value = publishCache(", pending)
        assertTrue(pending >= 0)
        assertTrue(publish < 0 || pending < publish)
        assertTrue(source.contains("suspendPageSettled"))
    }

    @Test fun `preview jobs survive pinch while costly jobs cancel`() {
        val source = viewModelSource()
        val cancel = source.substringAfter("private fun cancelNonPreviewJobs")
            .substringBefore("private fun cancelHighJobsExcept")
        assertTrue(cancel.contains("key.tier == QualityTier.MEDIUM"))
        assertTrue(cancel.contains("key.tier == QualityTier.HIGH"))
    }

    @Test fun `document page draws base and tiles in one canvas without crossfade`() {
        val page = viewportSource().substringAfter("private fun DocumentPage")
        assertTrue(page.contains("drawImage("))
        assertTrue(page.contains("FilterQuality.High"))
        assertFalse(page.contains("Crossfade"))
        assertFalse(page.contains("AnimatedContent"))
        assertFalse(page.contains("androidx.compose.foundation.Image"))
    }

    @Test fun `subcompose identity excludes transform and bitmap fields`() {
        val source = viewportSource()
        assertTrue(source.contains("subcompose(\"\$documentId|\$page\")"))
        assertFalse(source.contains("subcompose(\"\$documentId|\$page|"))
    }

    @Test fun `pointer loop has no render or viewport publish`() {
        val pointer = viewportSource().substringAfter(".pointerInput(documentId)")
            .substringBefore(") { constraints ->")
        assertFalse(pointer.contains("onViewportChanged"))
        assertFalse(pointer.contains("onEnsureAround"))
        assertFalse(pointer.contains("publishCache"))
    }

    @Test fun `aspect metadata is retained across cache map eviction`() {
        assertTrue(viewportSource().contains("displayedAspects = displayedAspects + acceptedAspects"))
    }

    @Test fun `vertical high scheduling uses dedicated hysteresis`() {
        assertTrue(viewModelSource().contains("ContinuousQualityHysteresis.resolve"))
    }

    private fun viewportSource(): String = source("ContinuousDocumentViewport.kt")
    private fun viewModelSource(): String = source("PdfViewerViewModel.kt")

    private fun source(name: String): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("src/main/java/com/markleaf/app/ui/pdf/$name"),
        File("../src/main/java/com/markleaf/app/ui/pdf/$name"),
    ).first { it.isFile }.readText()
}
