package com.m1racle.reader.ui.viewer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTrace

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorJumpTraceTest {
    @Test fun concurrentSuspendedJumpsCloseOnlyTheirOwnSection() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { traceAnchorJump { finish.await() } }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { traceAnchorJump { awaitCancellation() } }
        assertEquals(2, ShadowTrace.getCurrentAsyncSections().size)
        assertTrue(ShadowTrace.getCurrentSections().isEmpty())
        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        assertEquals(1, ShadowTrace.getCurrentAsyncSections().size)
        second.cancelAndJoin()
        assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
        assertEquals(2, ShadowTrace.getPreviousAsyncSections().size)
    }

    @Test fun completionAndFailureBothCloseTheSection() = runBlocking {
        assertEquals(7, traceAnchorJump { 7 })
        val failure = IllegalStateException("scroll disposed")
        try {
            traceAnchorJump { throw failure }
            fail("failure must propagate")
        } catch (caught: IllegalStateException) {
            assertSame(failure, caught)
        }
        assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
        assertTrue(ShadowTrace.getCurrentSections().isEmpty())
    }
}
