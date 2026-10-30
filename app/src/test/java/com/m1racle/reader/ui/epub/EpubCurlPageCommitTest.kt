package com.m1racle.reader.ui.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubCurlPageCommitTest {
    @Test fun `initial restore and programmatic snapTo never commit`() {
        val commit = EpubCurlPageCommit(current = 0)
        assertNull(commit.observe(0, 0f))
        assertNull(commit.observe(7, 0f))
        assertNull(commit.observe(8, 0f))
        assertNull(commit.observe(3, 0f))
    }

    @Test fun `cancelled curl returns to the same page without commit`() {
        val commit = EpubCurlPageCommit(current = 4)
        commit.onPointerDown()
        commit.onPointerConsumedMove()
        commit.onPointerUp()
        assertNull(commit.observe(4, 0f))
        assertNull(commit.observe(5, 0f))
    }

    @Test fun `completed curl commits exactly once after settling`() {
        val commit = EpubCurlPageCommit(current = 4)
        commit.onPointerDown()
        commit.onPointerConsumedMove()
        commit.onPointerUp()
        assertNull(commit.observe(5, .8f))
        assertEquals(5, commit.observe(5, 0f))
        assertNull(commit.observe(5, 0f))
        assertEquals(6, run {
            commit.onPointerDown()
            commit.onPointerConsumedMove()
            commit.onPointerUp()
            assertNull(commit.observe(6, .3f))
            commit.observe(6, 0f)
        })
    }

    @Test fun `programmatic curl animation has no pointer and never commits`() {
        val commit = EpubCurlPageCommit(current = 4)
        assertNull(commit.observe(5, .8f))
        assertNull(commit.observe(5, 0f))
    }

    @Test fun `crossing a page boundary then cancelling back never commits`() {
        val commit = EpubCurlPageCommit(current = 4)
        commit.onPointerDown()
        commit.onPointerConsumedMove()
        assertNull(commit.observe(5, .4f))
        commit.onPointerUp()
        assertNull(commit.observe(4, 0f))
        assertNull(commit.observe(5, 0f))
    }

    @Test fun `settled page while finger is held only commits after release`() {
        val commit = EpubCurlPageCommit(current = 4)
        commit.onPointerDown()
        commit.onPointerConsumedMove()
        assertNull(commit.observe(5, 0f))
        commit.onPointerUp()
        assertEquals(5, commit.observe(5, 0f))
        assertNull(commit.observe(5, 0f))
    }

    @Test fun `pointer cancellation and reflow discard pending user intent`() {
        val commit = EpubCurlPageCommit(current = 4)
        commit.onPointerDown(); commit.onPointerConsumedMove()
        assertNull(commit.observe(5, .4f))
        commit.onPointerCancelled()
        assertNull(commit.observe(5, 0f))
        commit.onPointerDown(); commit.onPointerConsumedMove()
        commit.reset(8)
        commit.onPointerUp()
        assertNull(commit.observe(9, 0f))
    }
}
