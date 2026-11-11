package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfResourceCloseOrderTest {

    @Test
    fun successPath_closesRendererThenPfd() {
        val order = mutableListOf<String>()
        val ran = PdfResourceCloseOrder.executeClose(
            rendererOpened = true,
            pfdOpened = true,
            closeRenderer = { order += "renderer" },
            closePfd = { order += "pfd" },
        )
        assertEquals(listOf("renderer", "pfd"), order)
        assertEquals(
            listOf(
                PdfResourceCloseOrder.Step.CLOSE_RENDERER,
                PdfResourceCloseOrder.Step.CLOSE_PFD,
            ),
            ran,
        )
    }

    @Test
    fun failedOpenAfterFd_stillClosesPfdOnly() {
        val order = mutableListOf<String>()
        PdfResourceCloseOrder.executeClose(
            rendererOpened = false,
            pfdOpened = true,
            closeRenderer = { order += "renderer" },
            closePfd = { order += "pfd" },
        )
        assertEquals(listOf("pfd"), order)
    }

    @Test
    fun neitherOpened_isNoop() {
        var calls = 0
        val ran = PdfResourceCloseOrder.executeClose(
            rendererOpened = false,
            pfdOpened = false,
            closeRenderer = { calls++ },
            closePfd = { calls++ },
        )
        assertTrue(ran.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun documentSwitchPlan_clearsCachesBeforeReopen() {
        val plan = PdfResourceCloseOrder.documentSwitchPlan()
        val openIdx = plan.indexOf(PdfResourceCloseOrder.DocumentSwitchStep.OPEN_MAIN_RENDERER)
        val clearPage = plan.indexOf(PdfResourceCloseOrder.DocumentSwitchStep.CLEAR_PAGE_CACHE)
        val clearTile = plan.indexOf(PdfResourceCloseOrder.DocumentSwitchStep.CLEAR_TILE_CACHE)
        val closeMain = plan.indexOf(PdfResourceCloseOrder.DocumentSwitchStep.CLOSE_MAIN_RENDERER)
        assertTrue(closeMain < openIdx)
        assertTrue(clearPage < openIdx)
        assertTrue(clearTile < openIdx)
        assertTrue(closeMain < clearPage || closeMain < clearTile)
    }

    @Test
    fun openAssignsPfdBeforeRendererConstruction() {
        assertEquals(
            listOf("assign_pfd", "construct_renderer"),
            PdfResourceCloseOrder.openAssignmentOrder(),
        )
        // Failed-open: PFD must still be closed.
        assertEquals(
            listOf(PdfResourceCloseOrder.Step.CLOSE_PFD),
            PdfResourceCloseOrder.failedOpenAfterDescriptor(),
        )
    }

    @Test
    fun failedOpen_closesPfdEvenWithoutRenderer() {
        var pfdClosed = 0
        var rendererClosed = 0
        PdfResourceCloseOrder.executeClose(
            rendererOpened = false,
            pfdOpened = true,
            closeRenderer = { rendererClosed++ },
            closePfd = { pfdClosed++ },
        )
        assertEquals(0, rendererClosed)
        assertEquals(1, pfdClosed)
    }
}
