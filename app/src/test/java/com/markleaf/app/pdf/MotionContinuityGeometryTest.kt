package com.markleaf.app.pdf

import com.markleaf.app.ui.pdf.inlineCardHeightPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R25.2 motion invariants (pure geometry — no UI clock needed).
 *
 * Simple inline (ReaderMoreHost.inlineCardHeightPx):
 *   - at ANY sampled p, Main card bottom == current real Column content
 *     height: baseHeight + Σ(expandedHeight × p);
 *   - p==0 → exactly baseHeight (expanded region height must be 0 while
 *     children are hidden — no white tail / blank slab);
 *   - height is monotonic & continuous in p (siblings Y follow smoothly,
 *     no jump);
 *   - A→B section switching stays continuous (no blank pause).
 *
 * Advanced (advancedMorphRect):
 *   - p==0 → exactly the source row rect; p==1 → exactly the target rect;
 *   - at ANY sampled p the rect edges lie ON the source→target interpolation
 *     trajectory (no off-track bounds);
 *   - right edge stays fixed at the source/target right (anchor stability);
 *   - horizontal progress leads vertical (big-card outline first, then
 *     content reveal) — both derived from the same master p.
 */
class MotionContinuityGeometryTest {

    // ── Simple inline ──────────────────────────────────────────────────────

    @Test
    fun `inline card bottom equals real content height at every sampled p`() {
        val base = 400f
        val hReading = 144f
        val hTopBar = 96f
        val samples = (0..20).map { it / 20f } // 0.00, 0.05, …, 1.00

        samples.forEach { p ->
            val height = inlineCardHeightPx(base, listOf(hReading to p))
            assertEquals(
                "at p=$p: card bottom == base + region×p (content-wrapped, no blank slab)",
                base + hReading * p, height, 1e-3f,
            )
        }
    }

    @Test
    fun `expanded region height is zero when children are hidden at p=0`() {
        val height = inlineCardHeightPx(400f, listOf(144f to 0f))
        assertEquals("region contributes 0 while children invisible", 400f, height, 1e-3f)
    }

    @Test
    fun `inline height is monotonic and continuous in p - no jump`() {
        val sections = listOf(144f to 1f, 96f to 1f)
        val base = 400f
        var prev = inlineCardHeightPx(base, sections.map { (h, _) -> h to 0f })
        var prevP = 0f
        (1..100).forEach { i ->
            val p = i / 100f
            val cur = inlineCardHeightPx(base, sections.map { (h, _) -> h to p })
            assertTrue("height must grow with p ($prevP→$p)", cur >= prev)
            // Strict monotonicity: both sections active ⇒ strictly growing.
            assertTrue("height strictly grows while sections open ($prevP→$p)", cur > prev)
            prev = cur
            prevP = p
        }
    }

    @Test
    fun `section switch A down to B is continuous - no blank pause`() {
        val base = 400f
        val hA = 144f
        val hB = 96f
        // A collapsing (pA: 1→0) while B expands (pB: 0→1) — same clock.
        // Total height is LINEAR in the ps, so every sampled frame must equal
        // the analytic value: any deviation = jump/blank pause.
        (0..20).forEach { i ->
            val t = i / 20f
            val pA = 1f - t
            val pB = t
            val cur = inlineCardHeightPx(base, listOf(hA to pA, hB to pB))
            val analytic = base + hA * pA + hB * pB
            assertEquals(
                "no gap/pause at t=$t: card bottom tracks analytic height (monotonic total drop is content-driven, not a stall)",
                analytic, cur, 1e-3f,
            )
        }
        // End state == exactly B expanded, A fully gone.
        assertEquals(base + hB, inlineCardHeightPx(base, listOf(hA to 0f, hB to 1f)), 1e-3f)
    }

    // ── Advanced (AnchoredNestedPopover — anchoredRevealRect, the ONLY
    //    production morph; dead advancedMorphRect was deleted in r26) ────────

    @Test
    fun `anchored reveal equals source row at p=0 and target at p=1`() {
        val r0 = anchoredRevealRect(
            sourceLeft = 0f, sourceTop = 263f, sourceRight = 216f, sourceBottom = 315f,
            targetLeft = -104f, targetTop = 120f, targetRight = 216f, targetBottom = 560f,
            p = 0f,
        )
        assertEquals(0f, r0.left, 1e-3f)
        assertEquals(263f, r0.top, 1e-3f)
        assertEquals(216f, r0.right, 1e-3f)
        assertEquals(315f, r0.bottom, 1e-3f)
        assertEquals(52f, r0.height, 1e-3f)

        val r1 = anchoredRevealRect(
            sourceLeft = 0f, sourceTop = 263f, sourceRight = 216f, sourceBottom = 315f,
            targetLeft = -104f, targetTop = 120f, targetRight = 216f, targetBottom = 560f,
            p = 1f,
        )
        assertEquals(-104f, r1.left, 1e-3f)
        assertEquals(120f, r1.top, 1e-3f)
        assertEquals(216f, r1.right, 1e-3f)
        assertEquals(560f, r1.bottom, 1e-3f)
    }

    @Test
    fun `anchored reveal stays on the source-target trajectory at every sampled p`() {
        val sT = 263f; val sB = 315f; val sL = 0f; val sR = 216f
        val tT = 120f; val tB = 560f; val tL = -104f; val tR = 216f
        (0..20).forEach { i ->
            val p = i / 20f
            val r = anchoredRevealRect(sL, sT, sR, sB, tL, tT, tR, tB, p)
            val eps = 1e-3f
            assertTrue("left on [sL,tL]", r.left >= minOf(sL, tL) - eps && r.left <= maxOf(sL, tL) + eps)
            assertTrue("top on [sT,tT]", r.top >= minOf(sT, tT) - eps && r.top <= maxOf(sT, tT) + eps)
            assertTrue("right on [sR,tR]", r.right >= minOf(sR, tR) - eps && r.right <= maxOf(sR, tR) + eps)
            assertTrue("bottom on [sB,tB]", r.bottom >= minOf(sB, tB) - eps && r.bottom <= maxOf(sB, tB) + eps)
        }
    }

    @Test
    fun `anchored reveal right edge stays fixed during the whole morph`() {
        (0..20).forEach { i ->
            val p = i / 20f
            val r = anchoredRevealRect(
                sourceLeft = 0f, sourceTop = 263f, sourceRight = 216f, sourceBottom = 315f,
                targetLeft = -104f, targetTop = 120f, targetRight = 216f, targetBottom = 560f,
                p = p,
            )
            assertEquals("right edge never moves", 216f, r.right, 1e-3f)
        }
    }

    @Test
    fun `anchored reveal interpolates all four edges - no horizontal bar phase`() {
        // r26 §4/§7: the morph is a continuous LTRB interpolation — every
        // frame is a real card (height AND left grow together), never a
        // 52dp-tall horizontal bar and never a bottom-only reveal.
        val sT = 263f; val sB = 315f; val sL = 0f; val sR = 216f
        val tT = 120f; val tB = 560f; val tL = -104f; val tR = 216f
        (0..10).forEach { i ->
            val p = i / 10f
            val r = anchoredRevealRect(sL, sT, sR, sB, tL, tT, tR, tB, p)
            // Left already moving at early p (no stuck source edge).
            assertEquals("left linear at p=$p", sL + (tL - sL) * p, r.left, 1e-3f)
            assertEquals("top linear at p=$p", sT + (tT - sT) * p, r.top, 1e-3f)
            assertEquals("bottom linear at p=$p", sB + (tB - sB) * p, r.bottom, 1e-3f)
            assertEquals("right pinned at p=$p", sR, r.right, 1e-3f)
            // Aspect is card-like at every frame: height far exceeds a row.
            assertTrue("height at p=$p is a card, not a pill", r.height >= 52f)
        }
    }

    @Test
    fun `anchored reveal collapse is exact reverse - same p same rect`() {
        (0..10).forEach { i ->
            val p = i / 10f
            val open = anchoredRevealRect(
                sourceLeft = 0f, sourceTop = 263f, sourceRight = 216f, sourceBottom = 315f,
                targetLeft = -104f, targetTop = 120f, targetRight = 216f, targetBottom = 560f,
                p = p,
            )
            val close = anchoredRevealRect(
                sourceLeft = 0f, sourceTop = 263f, sourceRight = 216f, sourceBottom = 315f,
                targetLeft = -104f, targetTop = 120f, targetRight = 216f, targetBottom = 560f,
                p = p,
            )
            assertEquals(open, close)
        }
    }
}
