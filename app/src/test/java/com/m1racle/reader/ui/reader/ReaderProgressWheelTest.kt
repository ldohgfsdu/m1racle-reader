package com.m1racle.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ReaderProgressWheelTest {
    @Test fun arcMapsStartMiddleEndWithoutEndpointWrap() {
        fun fraction(angle: Double): Float = wheelFraction(cos(Math.toRadians(angle)).toFloat(), sin(Math.toRadians(angle)).toFloat())
        assertEquals(0f, fraction(135.0), 0.0001f)
        assertEquals(0.5f, fraction(270.0), 0.0001f)
        assertEquals(1f, fraction(45.0), 0.0001f)
        assertEquals(1f, fraction(65.0), 0.0001f)
        assertEquals(0f, fraction(115.0), 0.0001f)
    }
}
