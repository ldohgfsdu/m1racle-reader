package com.m1racle.reader.image

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalImageSampleTest {
    @Test
    fun sampleSize_keepsEdgesWithinLimit() {
        assertEquals(1, LocalImageLoader.sampleSize(1600, 1600))
        assertEquals(2, LocalImageLoader.sampleSize(1601, 10))
        assertEquals(4, LocalImageLoader.sampleSize(5000, 5000))
    }

    @Test
    fun constrainToMaxEdge_scalesOversizedBitmap() {
        val source = Bitmap.createBitmap(3200, 100, Bitmap.Config.ARGB_8888)
        val limited = LocalImageLoader.constrainToMaxEdge(source)
        checkNotNull(limited)
        assertTrue(limited.width <= 1600)
        assertTrue(limited.height <= 1600)
        assertEquals(1600, limited.width)
        assertEquals(50, limited.height)
    }
}
