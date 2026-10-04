package com.m1racle.reader.ui.hub

import com.m1racle.reader.ui.hub.glasslab.GlassLabControl
import com.m1racle.reader.ui.hub.glasslab.GlassLabParameters
import com.m1racle.reader.ui.hub.glasslab.glassLabGeometry
import org.junit.Assert.*
import org.junit.Test

class GlassLabParametersTest {
    @Test fun corruptedRestoredValuesCannotReachShaderUniforms() {
        val restored = GlassLabParameters.from(listOf(Float.NaN, Float.POSITIVE_INFINITY, -30f, 100f))
        assertEquals(180f, restored.width, 0f)
        assertEquals(160f, restored.height, 0f)
        assertEquals(0f, restored.radius, 0f)
        assertEquals(1.4f, restored.scale, 0f)
        assertTrue(restored.values().all { it.isFinite() })
    }

    @Test fun largestSpecimenFitsNarrowCanvasWithSafeRadius() {
        val p = GlassLabParameters(width=320f,height=280f,scale=1.4f,radius=140f)
        val g = glassLabGeometry(p, 240f, 160f)
        assertTrue(g.width <= 208f)
        assertTrue(g.height <= 128.001f)
        assertTrue(g.radius <= minOf(g.width,g.height)/2)
        assertEquals(p.width/p.height,g.width/g.height,.0001f)
    }

    @Test fun eachControlPreservesOtherParametersAndClampsInvalidInput() {
        val original=GlassLabParameters()
        GlassLabControl.entries.forEach { control ->
            val changed=control.update(original,control.max+100f)
            assertEquals(control.max,control.value(changed),0f)
            GlassLabControl.entries.filter {it!=control}.forEach { unchanged ->
                assertEquals(unchanged.label,unchanged.value(original),unchanged.value(changed),0f)
            }
        }
    }

    @Test fun clearPresetLetsThroughMoreDetailThanRegular() {
        assertTrue(GlassLabParameters.Clear.blur < GlassLabParameters.Regular.blur)
        assertTrue(GlassLabParameters.Clear.opacity < GlassLabParameters.Regular.opacity)
    }

    @Test fun savedParametersRoundTripExactly() {
        val p=GlassLabParameters(width=250f,chroma=.73f,lightAngle=278f)
        assertEquals(p,GlassLabParameters.from(p.values()))
    }
}
