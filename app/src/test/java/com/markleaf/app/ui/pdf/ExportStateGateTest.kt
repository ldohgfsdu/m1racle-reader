package com.markleaf.app.ui.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tests for the export-state generation gate.
 *
 * Failure mode under test (Phase 4): a cancelled export job keeps running
 * inside its `withContext(Dispatchers.IO)` block until the next cancellation
 * checkpoint, and its late `onState` callbacks wrote into the shared
 * `_exportState` of a NEWER export — e.g. "cancel A, start B" ended with B
 * being overwritten by A's delayed Cancelled/Completed callback.
 */
class ExportStateGateTest {

    @Test
    fun cancelA_thenStartB_bLateCallbacksPass_aLateCallbacksDropped() {
        // Case 1 + 2 (core regression): cancel A → start B → B completes;
        // A's late callbacks are dropped, B's pass.
        val gate = ExportStateGate()
        val genA = gate.begin() // A starts
        gate.cancel() // A cancelled
        val genB = gate.begin() // B starts immediately after

        // A's late callback arrives after B started → dropped.
        assertFalse("A's late callback must be stale", gate.isCurrent(genA))
        // B's own callbacks still flow.
        assertTrue("B's callbacks must be current", gate.isCurrent(genB))
    }

    @Test
    fun rapidStartCancelCycle_onlyLastGenerationWins() {
        // Case 4: start / cancel / start / cancel / start — the last one wins.
        val gate = ExportStateGate()
        val stale = listOf(
            gate.begin(),
            gate.cancel(),
            gate.begin(),
            gate.cancel(),
        )
        val finalGen = gate.begin()
        stale.forEach { assertFalse("earlier generation $it must be stale", gate.isCurrent(it)) }
        assertTrue(gate.isCurrent(finalGen))
    }

    @Test
    fun beginWithoutCancel_isCurrent() {
        val gate = ExportStateGate()
        val gen = gate.begin()
        assertTrue(gate.isCurrent(gen))
    }

    @Test
    fun cancelWithoutBegin_keepsNextBeginCurrent() {
        val gate = ExportStateGate()
        gate.cancel() // no active job; harmless
        val gen = gate.begin()
        assertTrue(gate.isCurrent(gen))
    }
}
