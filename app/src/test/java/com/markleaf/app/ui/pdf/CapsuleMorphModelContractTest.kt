package com.markleaf.app.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.5 胶囊 morph 状态契约 —— 可执行规格（纯 JVM）。
 *
 * 本宿主约束：Robolectric 原生图形运行时不支持 Linux/aarch64，生产
 * `CapsulePopoverShape`/`PdfInfoCapsule` 的真实轮廓无法在 JVM 上执行，且本任务
 * 禁止为可测性改动生产代码。因此采用双层锁定：
 *  1. [CapsuleInteractionContractTest] 源码钉锁定生产端派生公式原文；
 *  2. 本文件的镜像模型按同一冻结常量执行**契约性质**扫描：宽度单调、header
 *     交叉淡变互补、body 可见性与宽度同步（禁止「宽了但内容没长」）、端点退化
 *     合法、内容 alpha 提前淡入且饱和封顶。
 * 中断连续性由 [CapsuleMorphSpringInterruptTest] 用真实冻结 spring 执行验证。
 * 真实渲染观感仍属设备验收清单（R22 报告 §设备回归清单）。
 */
class CapsuleMorphModelContractTest {

    /** 冻结的胶囊 morph 派生（与 PdfInfoCapsule 内联公式一一对应）。 */
    private object Frozen {
        const val COLLAPSED_WIDTH_DP = 200f
        const val EXPANDED_WIDTH_DP = 240f

        fun width(fraction: Float) =
            COLLAPSED_WIDTH_DP + (EXPANDED_WIDTH_DP - COLLAPSED_WIDTH_DP) * fraction

        fun usesTrapezoid(fraction: Float, widenedCapable: Boolean = true) =
            fraction > 0f && widenedCapable

        fun headerCollapsedAlpha(fraction: Float) = 1f - fraction

        fun expandedHeaderComposed(fraction: Float) = fraction > 0f

        fun headerExpandedAlpha(fraction: Float) = fraction

        fun bodyComposed(expanded: Boolean, fraction: Float) = expanded || fraction > 0f

        fun bodyHeightFraction(fraction: Float) = fraction

        fun bodyContentAlpha(fraction: Float) = (fraction * 1.5f).coerceAtMost(1f)
    }

    private val fractions = (0..20).map { it / 20f }

    @Test
    fun `mirror constants match the pinned production width band`() {
        // 与源码钉同源：若生产带被改而钉未动，钉先红；两处同时改则此处要求自觉修订。
        assertEquals(200f, Frozen.COLLAPSED_WIDTH_DP, 0f)
        assertEquals(240f, Frozen.EXPANDED_WIDTH_DP, 0f)
    }

    @Test
    fun `width interpolates monotonically between exact endpoints`() {
        var prev = -1f
        fractions.forEach { f ->
            val w = Frozen.width(f)
            assertTrue("monotonic@$f", w >= prev)
            prev = w
        }
        assertEquals(200f, Frozen.width(0f), 0f)
        assertEquals(240f, Frozen.width(1f), 0f)
        // 线性：中点恰为中值。
        assertEquals(220f, Frozen.width(0.5f), 1e-4f)
    }

    @Test
    fun `silhouette is plain pill at rest and only flares while widening`() {
        assertFalse(Frozen.usesTrapezoid(0f))
        assertTrue(Frozen.usesTrapezoid(0.0001f))
        // 两端轮廓都是普通圆角矩形：底部宽度在 f=0 时退化为收起态宽度。
        assertEquals(Frozen.COLLAPSED_WIDTH_DP, Frozen.width(0f), 0f)
        assertEquals(Frozen.EXPANDED_WIDTH_DP, Frozen.width(1f), 0f)
    }

    @Test
    fun `header crossfade stays complementary and inside bounds at every fraction`() {
        fractions.forEach { f ->
            val collapsedAlpha = Frozen.headerCollapsedAlpha(f)
            val expandedAlpha = if (Frozen.expandedHeaderComposed(f)) {
                Frozen.headerExpandedAlpha(f)
            } else {
                0f
            }
            assertTrue("collapsed in [0,1]@$f", collapsedAlpha in 0f..1f)
            assertTrue("expanded in [0,1]@$f", expandedAlpha in 0f..1f)
            assertEquals(
                "crossfade sums to one@$f",
                1f,
                collapsedAlpha + if (Frozen.expandedHeaderComposed(f)) Frozen.headerExpandedAlpha(f) else 0f,
                1e-6f,
            )
            if (f == 0f) {
                assertFalse(Frozen.expandedHeaderComposed(f))
            } else {
                assertTrue(Frozen.expandedHeaderComposed(f))
            }
        }
    }

    @Test
    fun `no illegal combination - widened width always has visible body content`() {
        // 禁例：宽度已展开但内容未展开。任何 f>0 都必须同时满足
        // body 已合成、区域高度随 fraction 生长、内容 alpha 已经开始淡入。
        fractions.forEach { f ->
            val widened = Frozen.width(f) > Frozen.COLLAPSED_WIDTH_DP + 1e-3f
            assertEquals("widened iff f>0@$f", f > 0f, widened)
            if (widened) {
                assertTrue(Frozen.bodyComposed(expanded = false, fraction = f))
                assertTrue("height grows@$f", Frozen.bodyHeightFraction(f) > 0f)
                assertTrue("content fading in@$f", Frozen.bodyContentAlpha(f) > 0f)
                assertTrue(Frozen.expandedHeaderComposed(f))
            }
        }
        // 反向禁例：fraction==0 时不得有任何展开残留。
        assertFalse(Frozen.bodyComposed(expanded = false, fraction = 0f))
        assertEquals(0f, Frozen.bodyContentAlpha(0f), 0f)
    }

    @Test
    fun `body content fades in early and saturates before full expansion`() {
        // ×1.5 提前淡入：约 2/3 处内容已完全不透明，全程不超过 1。
        assertEquals(1f, Frozen.bodyContentAlpha(2f / 3f), 1e-6f)
        fractions.forEach { f ->
            assertTrue("alpha bounded@$f", Frozen.bodyContentAlpha(f) in 0f..1f)
            assertTrue(
                "early fade-in@$f",
                Frozen.bodyContentAlpha(f) >= Frozen.bodyHeightFraction(f),
            )
        }
    }

    @Test
    fun `expanded flag alone composes the body even before the spring moves`() {
        // 点击瞬间（fraction 尚为 0）body 必须已经进入合成，否则首帧高度塌陷。
        assertTrue(Frozen.bodyComposed(expanded = true, fraction = 0f))
    }
}
