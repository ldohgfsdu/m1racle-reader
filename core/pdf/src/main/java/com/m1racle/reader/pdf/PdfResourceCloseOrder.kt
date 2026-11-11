package com.m1racle.reader.pdf

/**
 * Pure close-order contract for display PDF resources.
 * Android requires [PdfRenderer] closed before its [ParcelFileDescriptor].
 * When open fails after the FD is allocated but before the renderer is stored,
 * the FD must still be closed (failed-open path).
 */
object PdfResourceCloseOrder {

    enum class Step {
        CLOSE_RENDERER,
        CLOSE_PFD,
    }

    /**
     * Ideal successful-session order: renderer then PFD.
     * Failed-open (renderer never installed): PFD only.
     */
    fun stepsForSession(rendererOpened: Boolean, pfdOpened: Boolean): List<Step> {
        val steps = mutableListOf<Step>()
        if (rendererOpened) steps += Step.CLOSE_RENDERER
        if (pfdOpened) steps += Step.CLOSE_PFD
        return steps
    }

    /**
     * Document-switch release checklist (logical order).
     * Caches must clear before re-open so old bitmaps cannot pin the previous document.
     */
    enum class DocumentSwitchStep {
        CLOSE_MAIN_RENDERER,
        CLOSE_THUMB_RENDERER,
        CLEAR_PAGE_CACHE,
        CLEAR_TILE_CACHE,
        BUMP_DOCUMENT_GENERATION,
        OPEN_MAIN_RENDERER,
    }

    fun documentSwitchPlan(): List<DocumentSwitchStep> = listOf(
        DocumentSwitchStep.CLOSE_MAIN_RENDERER,
        DocumentSwitchStep.CLOSE_THUMB_RENDERER,
        DocumentSwitchStep.CLEAR_PAGE_CACHE,
        DocumentSwitchStep.CLEAR_TILE_CACHE,
        DocumentSwitchStep.BUMP_DOCUMENT_GENERATION,
        DocumentSwitchStep.OPEN_MAIN_RENDERER,
    )

    /**
     * Simulate close with counters — unit-testable without Android PdfRenderer.
     * @return ordered steps that actually ran
     */
    fun executeClose(
        rendererOpened: Boolean,
        pfdOpened: Boolean,
        closeRenderer: () -> Unit,
        closePfd: () -> Unit,
    ): List<Step> {
        val ran = mutableListOf<Step>()
        for (step in stepsForSession(rendererOpened, pfdOpened)) {
            when (step) {
                Step.CLOSE_RENDERER -> {
                    closeRenderer()
                    ran += step
                }
                Step.CLOSE_PFD -> {
                    closePfd()
                    ran += step
                }
            }
        }
        return ran
    }

    /**
     * Open-path assignment order: PFD must be recorded **before** constructing
     * PdfRenderer so [executeClose] still closes the FD if the ctor throws.
     */
    fun openAssignmentOrder(): List<String> = listOf("assign_pfd", "construct_renderer")

    /**
     * Failed open after FD allocated, before renderer success: close PFD only.
     */
    fun failedOpenAfterDescriptor(): List<Step> =
        stepsForSession(rendererOpened = false, pfdOpened = true)
}
