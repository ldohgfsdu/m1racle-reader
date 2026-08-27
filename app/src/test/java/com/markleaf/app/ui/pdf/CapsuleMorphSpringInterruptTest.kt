package com.markleaf.app.ui.pdf

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.5 胶囊 morph 中断连续性（可执行部分）。
 *
 * 生产端 PdfInfoCapsule 用「单个 Animatable + animateTo(从当前值出发)」保证
 * expand/collapse 互相打断时不跳变、不重建状态源（接线由
 * CapsuleInteractionContractTest 源码钉锁定）。本文件用**真实冻结的**
 * CapsuleMorphSpring（反射读取，防参数漂移）驱动真实动画核，验证该机制本身：
 * 接缝零跳变、速度连续、端点收敛、过冲有界。
 */
class CapsuleMorphSpringInterruptTest {

    private val converter = Float.VectorConverter

    /** 反射读取生产文件里的冻结 spring —— 参数被改时这里先红。 */
    private val capsuleMorphSpring: SpringSpec<Float> by lazy {
        val facade = Class.forName("com.markleaf.app.ui.pdf.PdfViewerScreenKt")
        val field = facade.getDeclaredField("CapsuleMorphSpring")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(null) as SpringSpec<Float>
    }

    @Test
    fun `frozen capsule spring spec stays damping 0_8 stiffness 450`() {
        assertEquals(0.8f, capsuleMorphSpring.dampingRatio, 1e-6f)
        assertEquals(450f, capsuleMorphSpring.stiffness, 1e-6f)
    }

    @Test
    fun `expand interrupted near half resumes collapse with zero seam jump`() {
        // 展开 0→1，跑到约 50% 时被收起打断。
        val expand = TargetBasedAnimation(capsuleMorphSpring, converter, 0f, 1f)
        val seam = sampleNear(expand, targetValue = 0.5f)
        assertTrue("seam ${seam.value}", seam.value in 0.35f..0.65f)

        // 收起续播：从当前 (value, velocity) 出发 —— 与生产 animateTo 同机制。
        val collapse = TargetBasedAnimation(
            capsuleMorphSpring,
            converter,
            initialValue = seam.value,
            targetValue = 0f,
            initialVelocity = seam.velocity,
        )
        // 接缝处：值与速度都精确延续 —— 没有第二状态源、没有跳变。
        assertEquals(seam.value, collapse.getValueFromNanos(0L), 0f)
        assertEquals(seam.velocity, velocityOf(collapse, 0L), 0f)
        assertContinuousAndConvergesTo(collapse, expectedEnd = 0f)
    }

    @Test
    fun `collapse interrupted at thirty percent resumes expansion without jump`() {
        // 收起 1→0，完成约 30%（value ≈ 0.7）时再次展开打断。
        val collapse = TargetBasedAnimation(capsuleMorphSpring, converter, 1f, 0f)
        val seam = sampleNear(collapse, targetValue = 0.7f)
        // 「约三成」按 16ms 帧粒度取带：高速段单帧可越过数个百分点。
        assertTrue("seam ${seam.value}", seam.value in 0.55f..0.85f)

        val resume = TargetBasedAnimation(
            capsuleMorphSpring,
            converter,
            initialValue = seam.value,
            targetValue = 1f,
            initialVelocity = seam.velocity,
        )
        assertEquals(seam.value, resume.getValueFromNanos(0L), 0f)
        assertEquals(seam.velocity, velocityOf(resume, 0L), 0f)
        assertContinuousAndConvergesTo(resume, expectedEnd = 1f)
    }

    @Test
    fun `full expand stays bounded and settles exactly on the endpoint`() {
        val expand = TargetBasedAnimation(capsuleMorphSpring, converter, 0f, 1f)
        var t = 0L
        var peak = 0f
        while (!expand.isFinishedFromNanos(t)) {
            peak = maxOf(peak, expand.getValueFromNanos(t))
            t += FRAME_MS
        }
        // ζ=0.8 的轻微过冲（≈1.5%）是手感的一部分；越界即回归。
        assertTrue("overshoot bound $peak", peak <= 1.03f)
        assertEquals("settles at expanded", 1f, expand.getValueFromNanos(t), 0.01f)
    }

    private data class Seam(val value: Float, val velocity: Float)

    private fun velocityOf(animation: TargetBasedAnimation<Float, AnimationVector1D>, t: Long) =
        animation.getVelocityVectorFromNanos(t).value

    /** 帧步进采样直到 value 首次越过 target。 */
    private fun sampleNear(
        animation: TargetBasedAnimation<Float, AnimationVector1D>,
        targetValue: Float,
    ): Seam {
        var t = 0L
        while (!animation.isFinishedFromNanos(t)) {
            t += FRAME_MS
            val now = animation.getValueFromNanos(t)
            val before = animation.getValueFromNanos((t - FRAME_MS).coerceAtLeast(0L))
            val crossed = if (now > before) now >= targetValue else now <= targetValue
            if (crossed) {
                return Seam(value = now, velocity = velocityOf(animation, t))
            }
        }
        error("animation never reached $targetValue")
    }

    /** 接缝后逐帧连续（帧位移受速度约束），并最终收敛到目标端点。 */
    private fun assertContinuousAndConvergesTo(
        animation: TargetBasedAnimation<Float, AnimationVector1D>,
        expectedEnd: Float,
    ) {
        var prev = animation.getValueFromNanos(0L)
        var prevVel = velocityOf(animation, 0L)
        var t = 0L
        var iterations = 0
        while (!animation.isFinishedFromNanos(t)) {
            t += FRAME_MS
            val v = animation.getValueFromNanos(t)
            val vel = velocityOf(animation, t)
            // 连续性：帧位移受帧速度约束（梯形规则 ×2.5 裕量覆盖加速度项）。
            // 状态源重建类回归（snapTo / 重置 Animatable）会产生 O(值域) 级跳变。
            // 终帧例外：playTime 被 clamp 到 duration 时，剩余亚阈值位移一次性
            // 并入末步，允许 ±2×visibilityThreshold。
            val threshold = capsuleMorphSpring.visibilityThreshold ?: 0.01f
            val maxStep =
                maxOf(kotlin.math.abs(prevVel), kotlin.math.abs(vel)) * (FRAME_MS / 1e9f) * 2.5f +
                    1e-3f + 2f * threshold
            assertTrue("frame jump at ${t}ns: $prev→$v", kotlin.math.abs(v - prev) <= maxStep)
            assertTrue("illegal state $v", v > -0.02f && v < 1.03f)
            prev = v
            prevVel = vel
            check(++iterations < 20_000) { "animation did not converge" }
        }
        assertEquals("converges", expectedEnd, prev, 0.01f)
    }

    private companion object {
        /** 16ms 帧步进（playTimeNanos 单位是纳秒）。 */
        const val FRAME_MS = 16_000_000L
    }
}
