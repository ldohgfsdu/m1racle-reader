package com.m1racle.reader.ui.material

import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Glass Material Engine 回归：配方跟随用户确认的材质，层级与交互约束保持稳定。
 *
 * 锁定三类不变量：
 *  1. 小控件低模糊/折射，面板高可读性/无折射，Dialog 保持窗口隔离；
 *  2. Dark/Light 一致性：Dark 表面亮度低于 Light，同时两侧都保留 highlight 与
 *     edge separation（描边宽度 + 阴影）；
 *  3. NORMAL/PRESSED 连续性：按压只改受控通道且经 PressSpring 插值，
 *     不允许瞬时替换材质。
 */
class GlassMaterialRegressionTest {

    private val roles = GlassMaterialRole.values()

    private fun spec(role: GlassMaterialRole, dark: Boolean = false, pressed: Boolean = false) =
        GlassMaterialEngine.resolve(role, dark = dark, pressed = pressed)

    @Test
    fun `dock retains pdf optics while reference tones leave reader controls unchanged`() {
        for (dark in listOf(false, true)) {
            val dock = spec(GlassMaterialRole.Dock, dark)
            val overlay = spec(GlassMaterialRole.Control, dark)
            assertEquals(overlay.blur, dock.blur)
            assertEquals(4.dp, overlay.blur)
            assertEquals(overlay.surfaceColor.alpha, dock.surfaceColor.alpha, 0f)
            assertEquals(if (dark) Color(0xFF707070) else Color.White, dock.surfaceColor.copy(alpha = 1f))
            assertEquals(if (dark) Color(0xFF242424) else Color.White, overlay.surfaceColor.copy(alpha = 1f))
            assertEquals(overlay.shadow, dock.shadow)
            assertEquals(overlay.shadowColor, dock.shadowColor)
            assertEquals(overlay.highlightWidth, dock.highlightWidth)
            assertEquals(if (dark) .60f else .90f, dock.highlightColor.alpha, .004f)
            assertEquals(.60f, overlay.highlightColor.alpha, .004f)
            assertEquals(overlay.lensHeight, dock.lensHeight)
            assertEquals(overlay.lensAmount, dock.lensAmount)
            for (role in listOf(
                GlassMaterialRole.Menu,
                GlassMaterialRole.SettingsCard,
            )) {
                val plate = spec(role, dark)
                assertTrue("$role remains readable above floating controls", plate.surfaceColor.alpha > overlay.surfaceColor.alpha)
                assertEquals("$role/$dark blur", overlay.blur, plate.blur)
                assertEquals("$role/$dark contrast", 1f, plate.contrast, 0f)
                assertEquals("$role/$dark brightness", 0f, plate.brightness, 0f)
                assertEquals("$role/$dark saturation", 1f, plate.saturation, 0f)
                assertTrue("$role/$dark vibrancy", plate.vibrancy)
                assertEquals("$role/$dark lens height", 8.dp, plate.lensHeight)
                assertEquals("$role/$dark lens amount", 24.dp, plate.lensAmount)
                assertEquals(overlay.highlightColor, plate.highlightColor)
                assertEquals("$role/$dark shadow radius", overlay.shadow, plate.shadow)
            }
        }
    }

    @Test
    fun `reader panels use readable liquid surfaces without refraction`() {
        for (dark in listOf(false, true)) {
            val control = spec(GlassMaterialRole.Control, dark)
            assertEquals(if (dark) 0.22f else 0.32f, control.surfaceColor.alpha, 0.004f)
            assertEquals(4.dp, control.blur)
            for (role in listOf(GlassMaterialRole.ExpandedMenu)) {
                val panel = spec(role, dark)
                assertEquals(0.90f, panel.surfaceColor.alpha, 0.004f)
                assertTrue(panel.surfaceColor.alpha > control.surfaceColor.alpha)
                assertEquals(24.dp, panel.blur)
                assertEquals(0.dp, panel.lensHeight)
                assertEquals(0.dp, panel.lensAmount)
                assertEquals(control.highlightColor, panel.highlightColor)
                assertFalse(panel.chromaticAberration)
            }
        }
    }

    // ── 1. 材质层级 ────────────────────────────────────────────────────────

    @Test
    fun `liquid surfaces preserve role hierarchy and dialog stays denser`() {
        for (dark in listOf(false, true)) {
            val control = spec(GlassMaterialRole.Control, dark)
            val capsule = spec(GlassMaterialRole.SelectionLens, dark)
            val menu = spec(GlassMaterialRole.Menu, dark)
            val card = spec(GlassMaterialRole.SettingsCard, dark)
            val dock = spec(GlassMaterialRole.Dock, dark)
            val dialog = spec(GlassMaterialRole.Dialog, dark)
            assertEquals(0.06f, capsule.surfaceColor.alpha, 0.004f)
            assertEquals(menu.surfaceColor.alpha, card.surfaceColor.alpha, 0f)
            assertTrue(control.surfaceColor.alpha < menu.surfaceColor.alpha)
            assertTrue(dock.surfaceColor.alpha < menu.surfaceColor.alpha)
            assertEquals(0.dp, capsule.blur)
            assertEquals(control.blur, menu.blur)
            assertEquals(4.dp, card.blur)
            assertEquals(control.blur, dock.blur)
            assertEquals(0.dp, capsule.shadow)
            assertEquals(control.shadow, menu.shadow)
            assertEquals(control.shadow, card.shadow)
            assertTrue(dialog.surfaceColor.alpha > card.surfaceColor.alpha)
            assertEquals(18.dp, dialog.blur)
            assertTrue(dialog.shadow >= card.shadow)
        }
    }

    @Test
    fun `surface alpha values stay at promoted liquid recipe numbers`() {
        // Dock and PDF controls share the same material endpoints.
        val expectedLight = mapOf(
            GlassMaterialRole.Dock to 0.32f,
            GlassMaterialRole.Control to 0.32f,
            GlassMaterialRole.SelectionLens to 0.06f,
            GlassMaterialRole.Menu to 0.60f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.60f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.24f,
        )
        val expectedDark = mapOf(
            GlassMaterialRole.Dock to 0.22f,
            GlassMaterialRole.Control to 0.22f,
            GlassMaterialRole.SelectionLens to 0.06f,
            GlassMaterialRole.Menu to 0.64f,
            GlassMaterialRole.ExpandedMenu to 0.90f,
            GlassMaterialRole.SettingsCard to 0.64f,
            GlassMaterialRole.Dialog to 0.97f,
            GlassMaterialRole.ProgressiveChrome to 0.52f,
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
    fun `dark surfaces stay darker and floating glass keeps a highlight`() {
        for (role in roles) {
            val light = spec(role, dark = false)
            val dark = spec(role, dark = true)
            assertTrue(
                "$role dark luminance",
                dark.surfaceColor.compositeOver(Color.Black).luminance() <
                    light.surfaceColor.compositeOver(Color.White).luminance(),
            )
            assertEquals("$role light brightness", 0f, light.brightness, 0f)
            assertEquals("$role dark brightness", 0f, dark.brightness, 0f)
            // highlight 双侧保留：宽度恒定、alpha 为正。
            val expectedWidth = when (role) {
                GlassMaterialRole.Dialog -> 0.5.dp
                GlassMaterialRole.ProgressiveChrome -> 0.dp
                else -> 0.75.dp
            }
            assertEquals("$role highlight width", expectedWidth, light.highlightWidth)
            assertEquals("$role highlight width dark", light.highlightWidth, dark.highlightWidth)
            val expectedLightHl = when (role) {
                GlassMaterialRole.Dock -> 0.90f
                GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens -> 0f
                GlassMaterialRole.Dialog -> 0.30f

                GlassMaterialRole.ProgressiveChrome -> 0f
                else -> 0.60f
            }
            val expectedDarkHl = when (role) {
                GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens -> 0f
                GlassMaterialRole.Dialog -> 0.38f

                GlassMaterialRole.ProgressiveChrome -> 0f
                else -> 0.60f
            }
            assertEquals(expectedLightHl, light.highlightColor.alpha, 0.004f)
            assertEquals(expectedDarkHl, dark.highlightColor.alpha, 0.004f)
            // edge separation：阴影双侧保留，夜间更重。
            if (role != GlassMaterialRole.ProgressiveChrome) {
                assertTrue(light.shadowColor.alpha > 0f)
                assertTrue(dark.shadowColor.alpha > light.shadowColor.alpha)
            }
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
            assertEquals(1f, light.saturation, 0f)
        }
    }

    @Test
    fun `dark palette follows liquid role colors`() {
        roles.forEach { role ->
            assertEquals(
                "dark tint $role",
                if (role == GlassMaterialRole.SelectionLens || role == GlassMaterialRole.DockLens) {
                    Color.White
                } else if (role == GlassMaterialRole.Dialog) {
                    Color(0xFF121212)
                } else if (role == GlassMaterialRole.Dock) {
                    Color(0xFF707070)
                } else if (role in listOf(GlassMaterialRole.Dock, GlassMaterialRole.Control, GlassMaterialRole.Capsule, GlassMaterialRole.SelectionLens, GlassMaterialRole.Menu, GlassMaterialRole.SettingsCard)) {
                    Color(0xFF242424)
                } else if (role in listOf(GlassMaterialRole.ExpandedMenu)) {
                    Color(0xFF202020)
                } else {
                    Color.Black
                },
                spec(role, dark = true).surfaceColor.copy(alpha = 1f),
            )
        }
    }

    // ── 3. NORMAL/PRESSED 连续性 ───────────────────────────────────────────

    @Test
    fun `pressed state shifts only the press channels`() {
        val frozenRoles = roles.filter {
            it !in listOf(GlassMaterialRole.Dock, GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens)
        }
        for (role in frozenRoles) {
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
                    if (role == GlassMaterialRole.Control || role == GlassMaterialRole.Capsule)
                        .40f - idle.surfaceColor.alpha else expectedSurfaceBoost,
                    pressed.surfaceColor.alpha - idle.surfaceColor.alpha,
                    0.006f,
                )
                assertEquals(
                    "$role/$dark highlight boost",
                    if (idle.highlightWidth > 0.dp) 0.10f else 0f,
                    pressed.highlightColor.alpha - idle.highlightColor.alpha,
                    0.006f,
                )
                assertEquals("$role/$dark scale", when(role) {
                    GlassMaterialRole.Control -> 1.085f
                    GlassMaterialRole.Capsule -> 1.075f
                    else -> .965f
                }, pressed.scale, 0f)
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
    fun `dock pickup leaves pdf material stable while the separate lens responds`() {
        val dock = spec(GlassMaterialRole.Dock)
        val dockPressed = spec(GlassMaterialRole.Dock, pressed = true)
        assertTrue(dock.vibrancy)
        assertEquals(8.dp, dock.lensHeight)
        assertEquals(24.dp, dock.lensAmount)
        assertEquals(1f, dock.scale, 0f)
        assertEquals(1f, dockPressed.scale, 0f)
        assertEquals(dock.lensHeight, dockPressed.lensHeight)
        assertEquals(dock.lensAmount, dockPressed.lensAmount)
        assertEquals(dock.highlightColor, dockPressed.highlightColor)
        assertEquals(dock.surfaceColor, dockPressed.surfaceColor)
    }

    @Test
    fun `pickup preserves rail tint and does not process captured colors twice`() {
        for (dark in listOf(false, true)) {
            val idle = spec(GlassMaterialRole.Dock, dark = dark)
            val pressed = spec(GlassMaterialRole.Dock, dark = dark, pressed = true)
            assertEquals(idle.surfaceColor, pressed.surfaceColor)
            assertEquals(idle.blur, pressed.blur)
            assertEquals(idle.lensHeight, pressed.lensHeight)
            assertEquals(idle.lensAmount, pressed.lensAmount)
            assertEquals(idle.highlightColor, pressed.highlightColor)
            for (held in listOf(false, true)) {
                assertFalse(spec(GlassMaterialRole.SelectionLens, dark = dark, pressed = held).vibrancy)
                assertTrue(spec(GlassMaterialRole.Dock, dark = dark, pressed = held).vibrancy)
            }
        }
    }

    @Test
    fun `every surface stays dispersion free when idle pressed or expanded`() {
        for (role in roles) for (dark in listOf(false, true)) {
            for (pressed in listOf(false, true)) for (expanded in listOf(false, true)) {
                val material = GlassMaterialEngine.resolve(role, dark, pressed, expanded)
                assertEquals("$role dark=$dark pressed=$pressed expanded=$expanded",
                    role in listOf(GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens), material.chromaticAberration)
                if (role == GlassMaterialRole.ProgressiveChrome) {
                    assertEquals(0f, material.highlightColor.alpha, 0f)
                    assertEquals(0.dp, material.highlightWidth)
                }
                if (role in listOf(GlassMaterialRole.Dialog, GlassMaterialRole.ProgressiveChrome, GlassMaterialRole.ExpandedMenu)) {
                    assertTrue("no strong edge lens in any state", material.lensHeight == 0.dp)
                    assertTrue("no strong refraction in any state", material.lensAmount == 0.dp)
                }
            }
        }
    }

    @Test
    fun `press response interpolates through one spring instead of swapping material`() {
        // 冻结的按压弹簧：阻尼/刚度/可见阈值任一漂移都会改变手感。
        assertEquals(0.82f, GlassMaterialEngine.PressSpring.dampingRatio, 1e-6f)
        assertEquals(Spring.StiffnessMedium.toFloat(), GlassMaterialEngine.PressSpring.stiffness, 0f)
        assertEquals(0.001f, GlassMaterialEngine.PressSpring.visibilityThreshold!!, 1e-9f)

        val frozenRoles = roles.filter {
            it !in listOf(GlassMaterialRole.Dock, GlassMaterialRole.SelectionLens, GlassMaterialRole.DockLens)
        }
        for (role in frozenRoles) {
            val idle = spec(role)
            val pressedSpec = spec(role, pressed = true)
            var prevSurface = -1f
            var prevHighlight = -1f
            var prevScale = idle.scale
            for (step in 0..20) {
                val p = step / 20f
                val surface = lerp(idle.surfaceColor, pressedSpec.surfaceColor, p).alpha
                val highlight = lerp(idle.highlightColor, pressedSpec.highlightColor, p).alpha
                val scale = idle.scale + (pressedSpec.scale - idle.scale) * p
                // 单调插值 + 端点封闭：中间态永远落在两个端点规格之间。
                assertTrue(surface >= prevSurface - 1e-6f && surface <= pressedSpec.surfaceColor.alpha + 1e-6f)
                assertTrue(highlight >= prevHighlight - 1e-6f)
                // 按压是轻微缩小（1 → 0.965），中间态落在缩小区间内。
                assertTrue(scale >= minOf(idle.scale, pressedSpec.scale) - 1e-6f &&
                    scale <= maxOf(idle.scale, pressedSpec.scale) + 1e-6f)
                if (pressedSpec.scale > idle.scale) assertTrue(scale >= prevScale - 1e-6f)
                else assertTrue(scale <= prevScale + 1e-6f)
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
        assertTrue(n.contains("else -> GlassMaterialEngine.PressSpring"))
        assertTrue(n.contains("pressLayerActive = pressed || kotlin.math.abs(pressProgress.value) > 0.001f"))
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
        "com/m1racle/reader/ui/material/GlassMaterial.kt",
    ).readText()

    private fun appSource(relative: String): File = ProductionSource.file(relative)
}
