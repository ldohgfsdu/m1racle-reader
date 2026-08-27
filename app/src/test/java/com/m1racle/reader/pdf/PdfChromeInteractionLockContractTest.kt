package com.m1racle.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R22.5 交互锁定契约（R22.4 行为冻结，禁止状态机逻辑改动）。
 *
 * 覆盖三类锁源（PINCH / PAGE_JUMP / TEXT_SELECTION）的统一语义：
 *  - 锁定期 chrome 不自动隐藏（level 冻结）、scroll 样本不累计、fling 不迁移；
 *  - fling 尝试不得污染解锁后的快速路径与冷却；
 *  - 解锁清空全部手势残留，从干净 session 重新起算，不立即触发错误 transition。
 * 单击语义（显式操作仍允许）与幂等性一并回归。
 */
class PdfChromeInteractionLockContractTest {

    private val t0 = 1_000_000L
    private val p = PdfChromeScrollParams()

    private fun machine() = PdfChromeScrollStateMachine(p)

    private fun PdfChromeScrollStateMachine.up(d: Float, now: Long = t0) =
        onScrollSample(ChromeContentDirection.UP, d, rawDeltaPx = -d, anchorDeltaYPx = -d, nowMs = now)

    private fun PdfChromeScrollStateMachine.down(d: Float, now: Long = t0) =
        onScrollSample(ChromeContentDirection.DOWN, d, rawDeltaPx = d, anchorDeltaYPx = d, nowMs = now)

    private fun PdfChromeScrollStateMachine.upFling(now: Long = t0) =
        onFling(ChromeContentDirection.UP, 2500f, 80f, anchorDeltaYPx = -80f, nowMs = now)

    private fun PdfChromeScrollStateMachine.downFling(now: Long = t0) =
        onFling(ChromeContentDirection.DOWN, 2500f, 80f, anchorDeltaYPx = 80f, nowMs = now)

    @Test
    fun `every lock source freezes scroll and fling while keeping center tap available`() {
        for (lock in ChromeInteractionLock.values()) {
            val m = machine()
            m.beginGesture()
            m.beginInteractionLock(lock)
            assertTrue("locked: $lock", m.isInteractionLocked)
            // 大位移滚动样本：不迁移、不累计 —— chrome 不自动隐藏。
            repeat(3) { assertTrue(m.up(40f).isEmpty()) }
            assertEquals(PdfChromeLevel.FULL, m.level)
            assertNull(m.upFling())
            assertEquals(PdfChromeLevel.FULL, m.level)
            // 中央单击是显式操作：锁定期间仍允许。
            assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.onCenterTap().levelAfter)
            m.endInteractionLock(lock)
            assertFalse(m.isInteractionLocked)
        }
    }

    @Test
    fun `text selection lock lifecycle mirrors pinch end to end`() {
        val m = machine()
        m.beginInteractionLock(ChromeInteractionLock.TEXT_SELECTION)
        assertTrue(m.isInteractionLocked)
        assertTrue(m.up(100f).isEmpty())
        assertNull(m.upFling())
        assertEquals(PdfChromeLevel.FULL, m.level)
        m.endInteractionLock(ChromeInteractionLock.TEXT_SELECTION)
        assertFalse(m.isInteractionLocked)
        // 解锁后干净 session：36dp 阈值从零重新起算。
        assertTrue(m.up(30f).isEmpty())
        assertEquals(1, m.up(10f).size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
    }

    @Test
    fun `fling attempts during lock do not pollute the post-unlock fast path`() {
        val m = machine()
        assertEquals(1, m.up(40f).size) // FULL→BUTTONS_ONLY，进入 cooldown
        m.beginInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        // 锁定期内的 DOWN fling 全部被丢弃：不留速度、不留冷却、不留方向残留。
        assertNull(m.downFling(t0 + 10))
        assertNull(m.downFling(t0 + 20))
        m.endInteractionLock(ChromeInteractionLock.PAGE_JUMP)
        // 解锁后立即可响应：cooldown 已被清空，首个样本即按干净规则计算。
        val change = m.down(32f, t0 + 30)
        assertEquals(1, change.size)
        assertEquals(PdfChromeLevel.FULL, change[0].levelAfter)
        assertEquals(ChromeTriggerReason.CAPSULE_SHOW_THRESHOLD, change[0].reason)
    }

    @Test
    fun `unlock discards partial accumulation instead of carrying it over`() {
        val m = machine()
        m.up(30f) // 残留 30dp（< 36 阈值），direction=UP
        m.beginInteractionLock(ChromeInteractionLock.PINCH)
        m.endInteractionLock(ChromeInteractionLock.PINCH)
        // 若残留被携带，35dp 将直接跨过第一级；干净 session 下必须不触发。
        assertTrue(m.up(35f).isEmpty())
        assertEquals(PdfChromeLevel.FULL, m.level)
        assertEquals(1, m.up(5f).size) // 新 session 累计：35+5=40 ≥ 36
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
    }

    @Test
    fun `unlock does not let a stale direction trigger a wrong transition`() {
        val m = machine()
        m.up(40f)
        m.up(60f) // HIDDEN
        m.up(10f, t0 + 400) // 建立 UP 方向 + 10dp 残留（无 pending 迁移）
        m.beginInteractionLock(ChromeInteractionLock.PINCH)
        m.endInteractionLock(ChromeInteractionLock.PINCH)
        // 残留 UP 方向若未被清除，DOWN 首帧会被当作反向确认消耗掉、不产生迁移；
        // 干净 session 下单个恢复阈值样本必须立即正确迁移。
        val change = m.down(32f, t0 + 500)
        assertEquals(1, change.size)
        assertEquals(PdfChromeLevel.BUTTONS_ONLY, change[0].levelAfter)
        assertEquals(ChromeTriggerReason.BUTTONS_SHOW_THRESHOLD, change[0].reason)
    }

    @Test
    fun `begin and end are idempotent for every lock source`() {
        for (lock in ChromeInteractionLock.values()) {
            val m = machine()
            m.beginInteractionLock(lock)
            m.beginInteractionLock(lock)
            assertTrue(m.isInteractionLocked)
            m.endInteractionLock(lock)
            // 重复 end 不得把别人的锁误释放成空集之外的状态。
            m.endInteractionLock(lock)
            assertFalse("unlocked once: $lock", m.isInteractionLocked)
            assertEquals(1, m.up(40f).size)
        }
    }

    @Test
    fun `releasing one of several locks keeps the remaining ones active`() {
        val m = machine()
        ChromeInteractionLock.values().forEach { m.beginInteractionLock(it) }
        // 逐个释放：只要还有任一锁在，滚动依旧被忽略且 level 不变。
        for ((index, lock) in ChromeInteractionLock.values().withIndex()) {
            m.endInteractionLock(lock)
            if (index < ChromeInteractionLock.values().lastIndex) {
                assertTrue("still locked after releasing $lock", m.isInteractionLocked)
                assertTrue(m.up(100f).isEmpty())
                assertEquals(PdfChromeLevel.FULL, m.level)
            }
        }
        assertFalse(m.isInteractionLocked)
        assertEquals(1, m.up(40f).size)
    }

    @Test
    fun `center tap under each lock persists correctly after unlock`() {
        for (lock in ChromeInteractionLock.values()) {
            val m = machine()
            m.beginInteractionLock(lock)
            val tap = m.onCenterTap()
            assertEquals(PdfChromeLevel.BUTTONS_ONLY, tap.levelAfter)
            m.endInteractionLock(lock)
            // level 是正交模式位：锁定不回滚显式操作的结果。
            assertEquals(PdfChromeLevel.BUTTONS_ONLY, m.level)
        }
    }
}
