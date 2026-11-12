package com.m1racle.reader.ui.pdf

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entry restore wiring guard.
 *
 * The document row's persisted progress owns the page the reader opens on. A
 * viewport session may only refine the anchor when it agrees; it must never
 * supply the entry page by itself. This guards the regression where a stale
 * session page (e.g. 7) overrode newer progress (e.g. 57) on open.
 */
class PdfEntryRestoreWiringTest {
    private val viewModel = ProductionSource.text(
        "com/m1racle/reader/ui/pdf/PdfViewerViewModel.kt",
    )

    @Test
    fun `entry page is resolved from progress and the session only refines it`() {
        assertTrue(
            "PdfViewerViewModel must resolve entry restore through resolvePdfEntryRestore",
            viewModel.contains("resolvePdfEntryRestore("),
        )
        assertTrue(
            "entry page must come from the resolved restore",
            viewModel.contains("val entryPage = entryRestore.pageIndex"),
        )
    }

    @Test
    fun `a stale viewport session page never becomes the entry page`() {
        assertFalse(
            "entry page must not fall back to the viewport session page",
            viewModel.contains("persistedViewportSession?.pageIndex ?: restored"),
        )
        assertFalse(
            "restore target must not use the viewport session page directly",
            viewModel.contains("pageIndex = persistedViewportSession?.pageIndex ?: persistedPosition.pageIndex"),
        )
    }

    @Test
    fun `seeded viewport session is the resolved one`() {
        assertTrue(
            "the tracker must be seeded with the resolved session",
            viewModel.contains("viewportSessionTracker.seed(entryRestore.session)"),
        )
        assertTrue(
            "published state must carry the resolved session",
            viewModel.contains("viewportSessionState = entryRestore.session"),
        )
    }
}
