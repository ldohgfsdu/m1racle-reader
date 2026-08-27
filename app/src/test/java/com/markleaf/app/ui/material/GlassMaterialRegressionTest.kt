package com.markleaf.app.ui.material

import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.5 Glass Material Engine 防回归（Engine 已真机验收冻结，禁止参数改动）。
 *
 * 锁定三类不变量：
 *  1. 材质层级：Dialog > SettingsCard(Card) > Menu > Control/Capsule；
 *  2. Dark/Light 一致性：Dark 表面亮度低于 Light，同时两侧都保留 highlight 与
 *     edge separation（描边宽度 + 阴影）；
 *  3. NORMAL/PRESSED 连续性：按压只改受控通道且经 PressSpring 插值，
 *     不允许瞬时替换材质。
 */
class GlassMaterialRegressionTest {

    private val roles = GlassMaterialRole.values()

    private fun spec(role: GlassMaterialRole, dark: Boolean = false, pressed: Boolean = false) =
        GlassMaterialEngine.resolve(role, dark = dark, pressed = pressed)

    // ── 1. 材质层级 ────────────────────────────────────────────────────────

    @Test
    fun `frost hierarchy dialog exceeds card tier which exceeds controls`() {
        for (dark in listOf(false, true)) {
            val control = spec(GlassMaterialRole.Control, dark)
            val capsule = spec(GlassMaterialRole.Capsule, dark)
            val menu = spec(GlassMaterialRole.Menu, dark)
            val card = spec(GlassMaterialRole.SettingsCard, dark)
            val dialog = spec(GlassMaterialRole.Dialog, dark)
            // 表面不透明度严格递增。
            assertTrue(dialog.surfaceColor.alpha > card.surfaceColor.alpha)
            assertTrue(card.surfaceColor.alpha > menu.surfaceColor.alpha)
            assertTrue(menu.surfaceColor.alpha > capsule.surfaceColor.alpha)
            assertEquals(capsule.surfaceColor.alpha, control.surfaceColor.alpha, 0f)
            // 模糊与投影同向递增（card 与 menu 同 blur、更重 shadow）。
            assertTrue(dialog.blur >= card.blur)
            assertEquals(card.blur, menu.blur)
            assertTrue(menu.blur > control.blur)
            assertTrue(dialog.shadow >= card.shadow)
            assertTrue(card.shadow > menu.shadow)
            assertTrue(menu.shadow > control.shadow)
        }
    }

    @Test
    fun `surface alpha values stay at frozen recipe numbers`() {
        val expectedLight = mapOf(
            GlassMaterialRole.Control to 0.50f,
            GlassMaterialRole.Capsule to 0.50f,
            GlassMaterialRole.Menu to 0.60f,
            GlassMaterialRole.SettingsCard to 0.66f,
            GlassMaterialRole.Dialog to 0.97f,
        )
        val expectedDark = mapOf(
            GlassMaterialRole.Control to 0.68f,
            GlassMaterialRole.Capsule to 0.68f,
            GlassMaterialRole.Menu to 0.74f,
            GlassMaterialRole.SettingsCard to 0.78f,
            GlassMaterialRole.Dialog to 0.97f,
        )
        expectedLight.forEach { (role, alpha) ->
            // Color 分量经打包存在半精度量化（≈1/255 步进），比较必须带容差。
            assertEquals("light $role", alpha, spec(role).surfaceColor.alpha, 0.004f)
        }
        expectedDark.forEach { (role, alpha) ->
            assertEquals("dark $role", alpha, spec(role, dark = true).surfaceColor.alpha, 0.004f)
        }
    }

    // ── 2. Dark/Light 一致性 ───────────────────────────────────────────────

    @Test
    fun `dark surfaces are darker than light while keeping highlights and edges`() {
        for (role in roles) {
            val light = spec(role, dark = false)
            val dark = spec(role, dark = true)
            assertTrue(
                "$role dark luminance",
                dark.surfaceColor.copy(alpha = 1f).luminance() <
                    light.surfaceColor.copy(alpha = 1f).luminance(),
            )
            assertTrue("$role dark brightness", dark.brightness < light.brightness)
            // highlight 双侧保留：宽度恒定、alpha 为正。
            assertEquals("$role highlight width", 0.5.dp, light.highlightWidth)
            assertEquals("$role highlight width dark", light.highlightWidth, dark.highlightWidth)
            assertTrue(light.highlightColor.alpha > 0f)
            assertTrue(dark.highlightColor.alpha > 0f)
            // edge separation：阴影双侧保留，夜间更重。
            assertTrue(light.shadowColor.alpha > 0f)
            assertTrue(dark.shadowColor.alpha > light.shadowColor.alpha)
        }
    }

    @Test
    fun `theme-independent recipe metrics stay aligned across themes`() {
        for (role in roles) {
            val light = spec(role, dark = false)
            val dark = spec(role, dark = true)
            assertEquals("$role blur", light.blur, dark.blur)
            assertEquals("$role lensHeight", light.lensHeight, dark.lensHeight)
            assertEquals("$role lensAmount", light.lensAmount, dark.lensAmount)
            assertEquals("$role shadow", light.shadow, dark.shadow)
            assertEquals("$role divider", light.contentDividerAlpha, dark.contentDividerAlpha, 0f)
            assertEquals("$role saturation", light.saturation, dark.saturation, 0f)
            assertFalse("$role depthEffect", light.depthEffect || dark.depthEffect)
            // 夜间次级内容反而更实（muted 更高），保证暗色可读性方向不被反转。
            assertTrue("$role muted", dark.contentMutedAlpha > light.contentMutedAlpha)
            assertEquals(1.25f, light.saturation, 0f)
        }
    }

    @Test
    fun `dark palette keeps the single frozen tint`() {
        roles.forEach { role ->
            assertEquals(
                "dark tint $role",
                androidx.compose.ui.graphics.Color(0xFF111820),
                spec(role, dark = true).surfaceColor.copy(alpha = 1f),
            )
        }
    }

    // ── 3. NORMAL/PRESSED 连续性 ───────────────────────────────────────────

    @Test
    fun `pressed state shifts only the press channels`() {
        for (role in roles) {
            for (dark in listOf(false, true)) {
                val idle = spec(role, dark)
                val pressed = spec(role, dark, pressed = true)
                // 受控通道增量：表面 +0.04（0.98 封顶，Dialog 会触顶）、
                // highlight +0.10、scale ×0.965。
                // （读回值含打包量化，增量比较带容差。）
                val expectedSurfaceBoost =
                    (0.98f - idle.surfaceColor.alpha).coerceAtMost(0.04f).coerceAtLeast(0f)
                assertEquals(
                    "$role/$dark surface boost",
                    expectedSurfaceBoost,
                    pressed.surfaceColor.alpha - idle.surfaceColor.alpha,
                    0.006f,
                )
                assertEquals(
                    "$role/$dark highlight boost",
                    0.10f,
                    pressed.highlightColor.alpha - idle.highlightColor.alpha,
                    0.006f,
                )
                assertEquals("$role/$dark scale", 0.965f, pressed.scale, 0f)
                assertEquals(1f, idle.scale, 0f)
                // 其余全部通道必须原样保留 —— 按压不是换一种材质。
                assertEquals(idle.blur, pressed.blur)
                assertEquals(idle.lensHeight, pressed.lensHeight)
                assertEquals(idle.lensAmount, pressed.lensAmount)
                assertEquals(idle.shadow, pressed.shadow)
                assertEquals(idle.shadowColor, pressed.shadowColor)
                assertEquals(idle.highlightWidth, pressed.highlightWidth)
                assertEquals(idle.contentDividerAlpha, pressed.contentDividerAlpha, 0f)
                assertEquals(idle.contentMutedAlpha, pressed.contentMutedAlpha, 0f)
                assertEquals(idle.brightness, pressed.brightness, 0f)
                assertEquals(idle.saturation, pressed.saturation, 0f)
                assertEquals(idle.depthEffect, pressed.depthEffect)
                assertEquals(idle.contentPressedOverlayAlpha, pressed.contentPressedOverlayAlpha, 0f)
            }
        }
    }

    @Test
    fun `press response interpolates through one spring instead of swapping material`() {
        // 冻结的按压弹簧：阻尼/刚度/可见阈值任一漂移都会改变手感。
        assertEquals(0.82f, GlassMaterialEngine.PressSpring.dampingRatio, 1e-6f)
        assertEquals(Spring.StiffnessMedium.toFloat(), GlassMaterialEngine.PressSpring.stiffness, 0f)
        assertEquals(0.001f, GlassMaterialEngine.PressSpring.visibilityThreshold!!, 1e-9f)

        for (role in roles) {
            val idle = spec(role)
            val pressedSpec = spec(role, pressed = true)
            var prevSurface = -1f
            var prevHighlight = -1f
            var prevScale = Float.MAX_VALUE // scale 递减通道：上界哨兵
            for (step in 0..20) {
                val p = step / 20f
                val surface = lerp(idle.surfaceColor, pressedSpec.surfaceColor, p).alpha
                val highlight = lerp(idle.highlightColor, pressedSpec.highlightColor, p).alpha
                val scale = idle.scale + (pressedSpec.scale - idle.scale) * p
                // 单调插值 + 端点封闭：中间态永远落在两个端点规格之间。
                assertTrue(surface >= prevSurface - 1e-6f && surface <= pressedSpec.surfaceColor.alpha + 1e-6f)
                assertTrue(highlight >= prevHighlight - 1e-6f)
                // 按压是轻微缩小（1 → 0.965），中间态落在缩小区间内。
                assertTrue(scale in pressedSpec.scale..idle.scale + 1e-6f)
                assertTrue(scale <= prevScale + 1e-6f)
                prevSurface = surface
                prevHighlight = highlight
                prevScale = scale
            }
            assertEquals(pressedSpec.surfaceColor.alpha, prevSurface, 1e-6f)
        }
    }

    @Test
    fun `glass modifier press path lerps rather than replaces specs`() {
        val n = materialSource().replace(Regex("\\s+"), " ")
        assertTrue(n.contains("animateFloatAsState("))
        assertTrue(n.contains("animationSpec = GlassMaterialEngine.PressSpring"))
        assertTrue(n.contains("pressLayerActive = pressed || pressProgress.value > 0.001f"))
        // plate 路径对 (idleSpec, pressedSpec) 做颜色插值；backdrop 路径表面用同一
        // lerp、highlight 用 alpha 混合 —— 两条路径都不允许整块替换规格。
        assertEquals(2, Regex("lerp\\(idleSpec\\.surfaceColor, pressedSpec\\.surfaceColor, progress\\)").findAll(n).count())
        assertEquals(1, Regex("lerp\\(idleSpec\\.highlightColor, pressedSpec\\.highlightColor, progress\\)").findAll(n).count())
        assertEquals(
            1,
            Regex("pressedSpec\\.highlightColor\\.alpha - idleSpec\\.highlightColor\\.alpha").findAll(n).count(),
        )
    }

    private fun materialSource(): String = appSource(
        "com/markleaf/app/ui/material/GlassMaterial.kt",
    ).readText()

    private fun appSource(relative: String): File = listOf(
        File("src/main/java/$relative"),
        File("app/src/main/java/$relative"),
        File("/root/code/MarkLeaf/app/src/main/java/$relative"),
    ).first { it.isFile }
}
