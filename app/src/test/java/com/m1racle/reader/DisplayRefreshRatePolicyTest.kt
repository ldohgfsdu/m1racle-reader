package com.m1racle.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayRefreshRatePolicyTest {

    @Test
    fun selectsHighestRefreshRateAtCurrentResolution() {
        val selected = selectHighestRefreshMode(
            currentWidth = 1220,
            currentHeight = 2656,
            candidates = listOf(
                DisplayModeCandidate(4, 1220, 2656, 60f),
                DisplayModeCandidate(3, 1220, 2656, 90f),
                DisplayModeCandidate(1, 1220, 2656, 120f),
                DisplayModeCandidate(9, 904, 572, 144f),
            ),
        )

        assertEquals(1, selected?.modeId)
        assertEquals(120f, selected?.refreshRate)
    }

    @Test
    fun refusesResolutionChangingMode() {
        assertNull(
            selectHighestRefreshMode(
                currentWidth = 1220,
                currentHeight = 2656,
                candidates = listOf(DisplayModeCandidate(9, 904, 572, 144f)),
            ),
        )
    }
}
