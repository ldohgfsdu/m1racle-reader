package com.markleaf.app.ui.material

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.6 Glass Interaction Layer 契约：
 *  - 状态转换合法性（全对矩阵 + 代表性非法路径被拒）；
 *  - pointer 生命周期（down/move/up/cancel、就地更新零分配语义）；
 *  - pressed/release、drag start/end；
 *  - context reset 与 revision 单调性；
 *  - 与 Material Layer 的硬边界（双向源码钉）与「零 redraw」结构保证。
 */
class GlassInteractionContractTest {

    private fun context() = GlassInteractionContext()

    // ── 1. 状态转换合法性 ──────────────────────────────────────────────────

    @Test
    fun `transition table covers every state with legal targets only`() {
        for (from in GlassInteractionState.values()) {
            val targets = GlassInteractionTransitions.legal[from]
            assertTrue("table entry for $from", targets != null)
            for (to in targets!!) {
                assertTrue("entry must be legal per isLegal", GlassInteractionTransitions.isLegal(from, to))
                assertFalse("no self entries needed", from == to)
            }
        }
        assertEquals(GlassInteractionState.values().size, GlassInteractionTransitions.legal.size)
    }

    @Test
    fun `representative illegal jumps are rejected and leave state untouched`() {
        val c = context()
        // IDLE 不能直接 DRAGGING。
        assertFalse(c.dragStart())
        assertEquals(GlassInteractionState.IDLE, c.state)
        // HOVER 不能直接 DRAGGING/EXPANDED。
        assertTrue(c.beginHover())
        assertFalse(c.dragStart())
        assertEquals(GlassInteractionState.HOVER, c.state)
        assertFalse(c.expand())
        assertEquals(GlassInteractionState.HOVER, c.state)
        // EXPANDED 不能直接 HOVER。
        c.reset()
        assertTrue(c.expand())
        assertFalse(c.beginHover())
        assertEquals(GlassInteractionState.EXPANDED, c.state)
    }

    @Test
    fun `full gesture walk idle-hover-pressed-dragging-expanded-idle succeeds`() {
        val c = context()
        assertTrue(c.beginHover())
        assertTrue(c.press())
        assertEquals(GlassInteractionState.PRESSED, c.state)
        assertTrue(c.dragStart())
        assertEquals(GlassInteractionState.DRAGGING, c.state)
        assertTrue(c.expand())
        assertEquals(GlassInteractionState.EXPANDED, c.state)
        assertTrue(c.collapse())
        assertEquals(GlassInteractionState.IDLE, c.state)
    }

    @Test
    fun `tap path idle-pressed-idle and direct expand are legal`() {
        val tap = context()
        assertTrue(tap.press())
        assertTrue(tap.release())
        assertEquals(GlassInteractionState.IDLE, tap.state)

        val menu = context()
        assertTrue(menu.expand()) // IDLE → EXPANDED：单击直达展开（合成点击）
        assertEquals(GlassInteractionState.EXPANDED, menu.state)
        assertTrue(menu.release())
    }

    @Test
    fun `self transitions are idempotent`() {
        val c = context()
        assertTrue(c.press())
        val r = c.revision
        assertTrue(c.press())
        assertEquals(GlassInteractionState.PRESSED, c.state)
        assertEquals(r, c.revision) // 自环不推进 revision
    }

    @Test
    fun `rejected transitions do not advance revision`() {
        val c = context()
        val base = c.revision
        // IDLE → DRAGGING 非法：拒绝且状态、revision 均不动。
        assertFalse(c.dragStart())
        assertEquals(base, c.revision)
        // HOVER → EXPANDED 非法：同样拒绝。
        assertTrue(c.beginHover())
        val afterHover = c.revision
        assertFalse(c.expand())
        assertEquals(afterHover, c.revision)
        assertEquals(GlassInteractionState.HOVER, c.state)
    }

    // ── 2. pointer 生命周期 ────────────────────────────────────────────────

    @Test
    fun `pointer down-move-up updates in place on the same instance`() {
        val c = context()
        val same: GlassInteractionContext = c
        c.onPointerDown(10f, 20f)
        assertTrue(c.isPointerActive)
        repeat(100) { i ->
            c.onPointerMove(10f + i, 20f + i, velocityXPerSec = 5f * i, velocityYPerSec = -2f * i)
        }
        assertEquals(109f, c.pointerX, 0f)
        assertEquals(119f, c.pointerY, 0f)
        assertEquals(495f, c.velocityX, 0.001f)
        assertEquals(-198f, c.velocityY, 0.001f)
        assertTrue(same === c) // 复用单实例：无每帧换对象
        c.onPointerUp()
        assertFalse(c.isPointerActive)
        assertEquals(0f, c.velocityX, 0f)
        assertEquals(0f, c.velocityY, 0f)
        assertEquals(109f, c.pointerX, 0f) // 释放点保留
    }

    @Test
    fun `pointer cancel behaves like up`() {
        val c = context()
        c.onPointerDown(1f, 2f)
        c.onPointerMove(3f, 4f, 9f, 9f)
        c.onPointerCancel()
        assertFalse(c.isPointerActive)
        assertEquals(3f, c.pointerX, 0f)
        assertEquals(0f, c.velocityX, 0f)
    }

    // ── 3. progress 输入 ───────────────────────────────────────────────────

    @Test
    fun `progress is clamped to unit range`() {
        val c = context()
        c.updateProgress(-0.5f)
        assertEquals(0f, c.progress, 0f)
        c.updateProgress(0.42f)
        assertEquals(0.42f, c.progress, 0f)
        c.updateProgress(7f)
        assertEquals(1f, c.progress, 0f)
    }

    // ── 4. reset / revision 生命周期 ───────────────────────────────────────

    @Test
    fun `reset forces idle and clears all dynamic inputs`() {
        val c = context()
        c.onPointerDown(5f, 6f)
        c.onPointerMove(7f, 8f, 3f, 4f)
        c.press()
        c.dragStart()
        c.updateProgress(0.8f)
        assertTrue(c.revision > 0)
        c.reset()
        assertEquals(GlassInteractionState.IDLE, c.state)
        assertEquals(0f, c.pointerX, 0f)
        assertEquals(0f, c.pointerY, 0f)
        assertEquals(0f, c.velocityX, 0f)
        assertEquals(0f, c.velocityY, 0f)
        assertEquals(0f, c.progress, 0f)
        assertFalse(c.isPointerActive)
    }

    @Test
    fun `revision advances only on accepted mutations`() {
        val c = context()
        val base = c.revision
        c.onPointerDown(0f, 0f)
        c.onPointerMove(1f, 1f, 1f, 1f)
        c.press()
        c.updateProgress(0.5f)
        c.release()
        c.onPointerUp() // 关闭指针序列
        assertEquals(base + 6, c.revision)
        // 拒绝的迁移与幂等空操作不动 revision。
        val frozen = c.revision
        assertFalse(c.dragStart())
        c.updateProgress(0.5f) // 相同值
        c.onPointerUp() // 无活跃序列
        assertEquals(frozen, c.revision)
    }

    // ── 5. 架构红线：与 Material 层隔离、零 redraw 结构 ────────────────────

    @Test
    fun `interaction layer never touches material presets or compose clocks`() {
        val n = norm(source("GlassInteraction.kt"))
        // 不引用引擎/配方 —— interaction state 无法被写入 Material preset。
        assertFalse(n.contains("GlassMaterialEngine"))
        assertFalse(n.contains("GlassMaterialSpec"))
        assertFalse(n.contains("glassMaterial("))
        // 零持续 redraw 结构：不是 Compose State，无绘制/重组入口。
        assertFalse(n.contains("mutableStateOf"))
        assertFalse(n.contains("StateFlow"))
        assertFalse(n.contains("drawWithContent"))
        assertFalse(n.contains("graphicsLayer"))
        assertFalse(n.contains("@Composable"))
    }

    @Test
    fun `material engine stays blind to the interaction layer`() {
        val material = norm(source("GlassMaterial.kt"))
        assertFalse(material.contains("GlassInteraction"))
        // 引擎公开面保持纯静态配方解析。
        assertTrue(material.contains("fun resolve("))
    }

    private fun source(name: String): String = appSource(
        "com/markleaf/app/ui/material/$name",
    ).readText()

    private fun appSource(relative: String): File = listOf(
        File("src/main/java/$relative"),
        File("app/src/main/java/$relative"),
        File("/root/code/MarkLeaf/app/src/main/java/$relative"),
    ).first { it.isFile }

    private fun norm(s: String): String = s.replace(Regex("\\s+"), " ")
}
