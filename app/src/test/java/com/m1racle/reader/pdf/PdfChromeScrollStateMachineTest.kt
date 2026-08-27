package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R18: 滚动方向规则修正（UP=收纳 / DOWN=恢复）、四态正交模型、单击只切胶囊。
 * 覆盖 r18.md §十 自动化回归 1-4、10。
 */
class PdfChromeScrollStateMachineTest {

    private val t0 = 1_000_000L
    private val p = PdfChromeScrollParams()

    private fun machine() = PdfChromeScrollStateMachine(p)

    private fun PdfChromeScrollStateMachine.up(d: Float, now: Long = t0) =
        onScrollSample(ChromeContentDirection.UP, d, rawDeltaPx = -d, anchorDeltaYPx = -d, nowMs = now)

    private fun PdfChromeScrollStateMachine.down(d: Float, now: Long = t0) =
        onScrollSample(ChromeContentDirection.DOWN, d, rawDeltaPx = d, anchorDeltaYPx = d, nowMs = now)

    // ── ① CONTENT_MOVING_UP：FULL → BUTTONS_ONLY → HIDDEN（完整收纳） ────────

    @Test
    fun `content moving up hides capsule first then buttons with additive thresholds`() {
        val m = machine()
        val first = m.up(40f)
        assertEquals(1, first.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, first[0].levelAfter)
        assertEquals(ChromeTriggerReason.CAPSULE_HIDE_THRESHOLD, first[0].reason)
        assertTrue(first[0].capsuleVisibleAfter.not())
        assertTrue(first[0].edgeButtonsVisibleAfter)
        // 追加 56dp 才收两圆（40-36=4 remainder，再 60 达 64 ≥ 56）。
        assertTrue(m.up(40f).isEmpty())
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        val second = m.up(20f)
        assertEquals(1, second.size)
        assertEquals(PdfChromeLevel.HIDDEN, second[0].levelAfter)
        assertEquals(ChromeTriggerReason.BUTTONS_HIDE_THRESHOLD, second[0].reason)
        assertTrue(second[0].edgeButtonsVisibleAfter.not())
        // HIDDEN 下继续 UP（收纳链端点）：保持。
        assertTrue(m.up(200f).isEmpty())
        assertEquals(PdfChromeLevel.HIDDEN, m.level)
    }

    @Test
    fun `single sample FULL UP 100dp consumes both stages to HIDDEN`() {
        val m = machine()
        val changes = m.up(100f) // 100-36=64 >= 56
        assertEquals(2, changes.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, changes[0].levelAfter)
        assertEquals(PdfChromeLevel.HIDDEN, changes[1].levelAfter)
    }

    @Test
    fun `overshoot remainder carries into the next hide stage`() {
        val m = machine()
        assertTrue(m.up(90f).isNotEmpty()) // 36 消费，remainder 54 进入第二级
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        val changes = m.up(10f) // 54+10=64 >= 56
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.HIDDEN, changes[0].levelAfter)
    }

    // ── ② CONTENT_MOVING_DOWN：HIDDEN → BUTTONS_ONLY → FULL（完整恢复） ──────

    @Test
    fun `content moving down restores buttons first then capsule`() {
        val m = machine()
        m.onCenterTap() // FULL→BUTTONS_ONLY
        m.onCenterTap() // BUTTONS_ONLY→FULL
        // 用滚动把两圆收进 HIDDEN（UP 收纳链）。
        m.up(40f)
        m.up(60f)
        assertEquals(PdfChromeLevel.HIDDEN, m.level)

        assertTrue(m.down(30f).isEmpty()) // reverse confirm (cooldown, 24) — no displacement
        val first = m.down(40f) // >= 32：先两圆
        assertEquals(1, first.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, first[0].levelAfter)
        assertEquals(ChromeTriggerReason.BUTTONS_SHOW_THRESHOLD, first[0].reason)
        assertTrue(first[0].edgeButtonsVisibleAfter)
        assertTrue(first[0].capsuleVisibleAfter.not())
        // 追加 28dp：胶囊（40-32=8 remainder，再 20 达 28）。
        assertTrue(m.down(10f).isEmpty())
        val second = m.down(10f)
        assertEquals(1, second.size)
        assertEquals(PdfChromeLevel.FULL, second[0].levelAfter)
        assertEquals(ChromeTriggerReason.CAPSULE_SHOW_THRESHOLD, second[0].reason)
        // FULL 下继续 DOWN（恢复链端点）：保持。
        assertTrue(m.down(200f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
    }

    @Test
    fun `single sample HIDDEN DOWN 70dp consumes both restore stages to FULL`() {
        val m = machine()
        m.up(40f)
        m.up(60f)
        assertEquals(PdfChromeLevel.HIDDEN, m.level)
        m.down(30f) // reverse confirm (cooldown)
        val changes = m.down(70f) // 70-32=38 >= 28
        assertEquals(2, changes.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, changes[0].levelAfter)
        assertEquals(PdfChromeLevel.FULL, changes[1].levelAfter)
    }

    // ── ③ 正文单击：只切胶囊，两圆纹丝不动 ──────────────────────────────────

    @Test
    fun `single tap toggles capsule only across all four states`() {
        val m = machine()
        // FULL tap → BUTTONS_ONLY（buttons 保留）
        val a = m.onCenterTap()
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, a.levelAfter)
        assertTrue(a.edgeButtonsVisibleAfter)
        // BUTTONS_ONLY tap → FULL
        val b = m.onCenterTap()
        assertEquals(PdfChromeLevel.FULL, b.levelAfter)
        assertTrue(b.edgeButtonsVisibleAfter)
        // 收两圆（滚动）→ HIDDEN tap → CAPSULE_ONLY（胶囊现，两圆仍隐藏）
        m.up(40f)
        m.up(60f)
        assertEquals(PdfChromeLevel.HIDDEN, m.level)
        val c = m.onCenterTap()
        assertEquals(PdfChromeLevel.CAPSULE_ONLY, c.levelAfter)
        assertTrue(c.capsuleVisibleAfter)
        assertTrue(c.edgeButtonsVisibleAfter.not())
        // CAPSULE_ONLY tap → HIDDEN
        val d = m.onCenterTap()
        assertEquals(PdfChromeLevel.HIDDEN, d.levelAfter)
        assertTrue(d.capsuleVisibleAfter.not())
        assertTrue(d.edgeButtonsVisibleAfter.not())
    }

    // ── ④ scroll + tap 交错：四组合无非法跳态 ───────────────────────────────

    @Test
    fun `scroll UP then tap then scroll DOWN never produces illegal states`() {
        val m = machine()
        m.up(40f) // FULL→BUTTONS_ONLY
        m.up(60f) // BUTTONS_ONLY→HIDDEN
        m.onCenterTap() // HIDDEN→CAPSULE_ONLY
        assertEquals(PdfChromeLevel.CAPSULE_ONLY, m.level)
        // CAPSULE_ONLY × UP：收胶囊 → HIDDEN。
        val changes = m.up(40f)
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.HIDDEN, changes[0].levelAfter)
        // CAPSULE_ONLY × DOWN：恢复两圆 → FULL。
        m.onCenterTap() // HIDDEN→CAPSULE_ONLY
        val restore = m.down(40f)
        assertEquals(1, restore.size)
        assertEquals(PdfChromeLevel.FULL, restore[0].levelAfter)
        assertEquals(ChromeTriggerReason.BUTTONS_SHOW_THRESHOLD, restore[0].reason)
    }

    @Test
    fun `scroll DOWN restores to FULL then tap only toggles capsule`() {
        val m = machine()
        m.up(40f)
        m.up(60f)
        m.down(30f) // reverse confirm (cooldown)
        m.down(40f) // HIDDEN→BUTTONS_ONLY
        m.down(30f) // →FULL
        assertEquals(PdfChromeLevel.FULL, m.level)
        val t = m.onCenterTap()
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, t.levelAfter)
        assertTrue(t.edgeButtonsVisibleAfter)
    }

    // ── ⑤ 抖动 / 确认 / cooldown（R17 机制保留） ────────────────────────────

    @Test
    fun `micro jitter below reverse confirm does not flip direction`() {
        val m = machine()
        m.up(40f) // → BUTTONS_ONLY, direction=UP
        assertTrue(m.down(8f).isEmpty()) // 8 < 12 confirm
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
    }

    @Test
    fun `cooldown doubles reverse confirmation distance`() {
        val m = machine()
        m.up(40f, t0) // → BUTTONS_ONLY + cooldown
        assertTrue(m.down(15f, t0 + 100L).isEmpty()) // 15 < 24 (doubled)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        assertTrue(m.down(10f, t0 + 150L).isEmpty()) // 25 >= 24: flips, stays BO
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
    }

    @Test
    fun `cooldown does not block same-direction progression`() {
        val m = machine()
        val now = t0
        m.up(40f, now) // → BUTTONS_ONLY, cooldown until now+350
        val changes = m.up(60f, now + 50L) // same direction, additive 56
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.HIDDEN, changes[0].levelAfter)
    }

    @Test
    fun `subpixel alternating noise inside noise epsilon leaves accumulators untouched`() {
        val m = machine()
        m.up(30f)
        repeat(10) {
            assertTrue(m.up(0.4f).isEmpty())
            assertTrue(m.down(0.4f).isEmpty())
        }
        val changes = m.up(10f) // 30+10 = 40 >= 36
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, changes[0].levelAfter)
    }

    // ── ⑥ fling 服从方向矩阵 ───────────────────────────────────────────────

    @Test
    fun `up fling hides capsule then buttons one level per call`() {
        val m = machine()
        val c1 = m.onFling(ChromeContentDirection.UP, 2000f, 60f, anchorDeltaYPx = -60f, nowMs = t0)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, c1?.levelAfter)
        assertEquals(ChromeTriggerReason.FLING_HIDE_CAPSULE, c1?.reason)
        val c2 = m.onFling(ChromeContentDirection.UP, 2000f, 60f, anchorDeltaYPx = -60f, nowMs = t0)
        assertEquals(PdfChromeLevel.HIDDEN, c2?.levelAfter)
        assertEquals(ChromeTriggerReason.FLING_HIDE_BUTTONS, c2?.reason)
        assertNull(m.onFling(ChromeContentDirection.UP, 2000f, 60f, anchorDeltaYPx = -60f, nowMs = t0))
        assertEquals(PdfChromeLevel.HIDDEN, m.level)
    }

    @Test
    fun `down fling restores buttons then capsule one level per call`() {
        val m = machine()
        m.up(40f)
        m.up(60f)
        val c1 = m.onFling(ChromeContentDirection.DOWN, 2000f, 60f, anchorDeltaYPx = 60f, nowMs = t0)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, c1?.levelAfter)
        assertEquals(ChromeTriggerReason.FLING_SHOW_BUTTONS, c1?.reason)
        val c2 = m.onFling(ChromeContentDirection.DOWN, 2000f, 60f, anchorDeltaYPx = 60f, nowMs = t0)
        assertEquals(PdfChromeLevel.FULL, c2?.levelAfter)
        assertEquals(ChromeTriggerReason.FLING_SHOW_CAPSULE, c2?.reason)
        assertNull(m.onFling(ChromeContentDirection.DOWN, 2000f, 60f, anchorDeltaYPx = 60f, nowMs = t0))
        assertEquals(PdfChromeLevel.FULL, m.level)
    }

    @Test
    fun `slow fling does not take the fast path`() {
        val m = machine()
        assertNull(m.onFling(ChromeContentDirection.UP, 800f, 60f, anchorDeltaYPx = -60f, nowMs = t0))
        assertEquals(PdfChromeLevel.FULL, m.level)
    }

    // ── ⑦ session / reset（R17 机制保留） ───────────────────────────────────

    @Test
    fun `end gesture clears residue and keeps level`() {
        val m = machine()
        m.up(30f)
        m.endGesture()
        assertEquals(PdfChromeLevel.FULL, m.level)
        assertTrue(m.up(30f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
        assertTrue(m.up(10f).isNotEmpty())
    }

    @Test
    fun `reset for new session keeps level but clears residue`() {
        val m = machine()
        m.up(36f) // BUTTONS_ONLY（恰好第一级，无 remainder）
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        m.resetForNewSession()
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        // 新 session：UP 从 BUTTONS_ONLY 继续收纳（第二级 56 追加，无历史累计）。
        assertTrue(m.up(40f).isEmpty())
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        val changes = m.up(20f) // 60 >= 56
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.HIDDEN, changes[0].levelAfter)
    }

    @Test
    fun `user gesture id increments per begin`() {
        val m = machine()
        m.beginGesture()
        m.beginGesture()
        assertEquals(2L, m.userGestureId)
        val changes = m.up(40f)
        assertEquals(2L, changes[0].userGestureId)
    }

    // ── ⑧ 帧率无关（R17 机制保留） ─────────────────────────────────────────

    @Test
    fun `same 60dp UP trajectory at different frame granularities ends identically`() {
        val coarse = machine().also { it.up(60f) } // 60-36=24 < 56
        val medium = machine().also { m -> repeat(6) { m.up(10f) } }
        val fine = machine().also { m -> repeat(60) { m.up(1f) } }
        listOf(coarse, medium, fine).forEach { m ->
            assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        }
    }

    @Test
    fun `same 100dp UP trajectory at different frame granularities ends identically`() {
        val coarse = machine().also { it.up(100f) }
        val medium = machine().also { m -> repeat(10) { m.up(10f) } }
        val fine = machine().also { m -> repeat(100) { m.up(1f) } }
        listOf(coarse, medium, fine).forEach { m ->
            assertEquals(PdfChromeLevel.HIDDEN, m.level)
        }
    }

    // ── ⑨ R22.4 INTERACTION_LOCKED：pinch / 跳转期间 chrome 冻结 ────────────

    @Test
    fun `interaction lock ignores scroll samples and keeps level`() {
        val m = machine()
        m.beginInteractionLock(ChromeInteractionLock.PINCH)
        assertTrue(m.isInteractionLocked)
        // 锁定期间大量 UP 位移：不迁移、不累计。
        repeat(10) { assertTrue(m.up(40f).isEmpty()) }
        assertNull(m.onFling(ChromeContentDirection.UP, 2000f, 60f, -60f, t0))
        assertEquals(PdfChromeLevel.FULL, m.level)
        // 解锁后从干净 session 开始：36dp 阈值重新起算（无锁定期残留）。
        m.endInteractionLock(ChromeInteractionLock.PINCH)
        assertFalse(m.isInteractionLocked)
        assertTrue(m.up(30f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
        val changes = m.up(10f) // 30+10=40 >= 36
        assertEquals(1, changes.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, changes[0].levelAfter)
    }

    @Test
    fun `nested locks require all reasons released`() {
        val m = machine()
        m.beginInteractionLock(ChromeInteractionLock.PINCH)
        m.beginInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        assertTrue(m.up(100f).isEmpty())
        m.endInteractionLock(ChromeInteractionLock.PINCH)
        assertTrue(m.isInteractionLocked)
        assertTrue(m.up(100f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
        m.endInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        assertFalse(m.isInteractionLocked)
        assertEquals(1, m.up(40f).size)
    }

    @Test
    fun `begin and end lock are idempotent and clear residue once`() {
        val m = machine()
        m.up(30f) // 残留 30dp
        m.beginInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        m.beginInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        m.endInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        m.endInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        assertFalse(m.isInteractionLocked)
        // 残留被清空：30+30=60 本应跨级，现在必须重新累计。
        assertTrue(m.up(30f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
    }

    @Test
    fun `center tap still works while locked but level change persists after unlock`() {
        val m = machine()
        m.beginInteractionLock(ChromeInteractionLock.PINCH)
        m.onCenterTap()
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        m.endInteractionLock(ChromeInteractionLock.PINCH)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
    }

    // ── ⑩ adapter ground truth（R17 映射，禁止反转） ───────────────────────

    @Test
    fun `adapter maps negative anchor delta to content moving up`() {
        assertEquals(ChromeContentDirection.UP, mapScrollToContentMotion(-3.5f))
    }

    @Test
    fun `adapter maps positive anchor delta to content moving down`() {
        assertEquals(ChromeContentDirection.DOWN, mapScrollToContentMotion(3.5f))
    }

    @Test
    fun `adapter returns null for zero anchor delta`() {
        assertNull(mapScrollToContentMotion(0f))
    }

    @Test
    fun `adapter hardcoded oracle old 500 new 480 is content moving up`() {
        val oldAnchorY = 500f
        val newAnchorY = 480f
        val anchorDeltaY = newAnchorY - oldAnchorY
        assertEquals(-20f, anchorDeltaY, 0f)
        assertEquals(ChromeContentDirection.UP, mapScrollToContentMotion(anchorDeltaY))
    }

    @Test
    fun `adapter hardcoded oracle old 500 new 525 is content moving down`() {
        val oldAnchorY = 500f
        val newAnchorY = 525f
        val anchorDeltaY = newAnchorY - oldAnchorY
        assertEquals(25f, anchorDeltaY, 0f)
        assertEquals(ChromeContentDirection.DOWN, mapScrollToContentMotion(anchorDeltaY))
    }
}
