package com.m1racle.reader.ui.viewer.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingAnchorPlacementTest {

    @Test
    fun jumpCompensationMatchesHeadingInternalTopPadding() {
        assertEquals(28.dp, headingTopPadding(1))
        assertEquals(24.dp, headingTopPadding(2))
        assertEquals(20.dp, headingTopPadding(3))
        assertEquals(16.dp, headingTopPadding(4))
    }
}
