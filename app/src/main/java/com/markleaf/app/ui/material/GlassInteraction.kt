package com.markleaf.app.ui.material

/**
 * R22.6 Glass Interaction Layer —— 交互上下文能力层（能力先行，未接入任何生产组件）。
 *
 * ## 与 Material Layer 的边界（架构红线）
 * - Material 层（静态材质引擎与其配方对象）决定 blur / opacity(surface alpha) /
 *   tint / stroke(highlight)。
 * - 本层只产出「当前交互状态 + 动态参数输入」（pointer / velocity / progress），
 *   是未来动态效果（radial highlight、directional edge light、press glow、
 *   drag response）的统一数据源。
 * - 禁止把 interaction state 直接写入 Material preset：本文件不引用任何
 *   Material 引擎/配方符号，引擎侧也不感知本层（由 GlassInteractionContractTest
 *   源码钉双向锁定）。未来接入时由渲染适配层在组件自己的绘制阶段读取 context
 *   完成映射，而非改配方。
 *
 * ## 性能契约
 * - 零每帧分配：全部字段就地更新（原始类型 + 单实例复用），API 只收原始参数。
 * - 零持续 redraw：本层不是 Compose 可观察状态，自身永不触发重组/重绘；
 *   接入方显式选择观察时机（如帧循环或快照流），idle 0-redraw 基线不被破坏。
 * - 生命周期可控：单组件单实例（remember 持有），[revision] 单调递增用于
 *   变更检测与泄漏诊断，[reset] 强制归位。
 */
enum class GlassInteractionState {
    IDLE,
    HOVER,
    PRESSED,
    DRAGGING,
    EXPANDED,
}

/** 交互状态机合法迁移表（冻结契约；自环 = 幂等重入，允许）。 */
object GlassInteractionTransitions {

    val legal: Map<GlassInteractionState, Set<GlassInteractionState>> = mapOf(
        GlassInteractionState.IDLE to setOf(
            GlassInteractionState.HOVER,
            GlassInteractionState.PRESSED,
            GlassInteractionState.EXPANDED,
        ),
        GlassInteractionState.HOVER to setOf(
            GlassInteractionState.IDLE,
            GlassInteractionState.PRESSED,
        ),
        GlassInteractionState.PRESSED to setOf(
            GlassInteractionState.DRAGGING,
            GlassInteractionState.EXPANDED,
            GlassInteractionState.HOVER,
            GlassInteractionState.IDLE,
        ),
        GlassInteractionState.DRAGGING to setOf(
            GlassInteractionState.EXPANDED,
            GlassInteractionState.PRESSED,
            GlassInteractionState.IDLE,
            GlassInteractionState.HOVER,
        ),
        GlassInteractionState.EXPANDED to setOf(
            GlassInteractionState.DRAGGING,
            GlassInteractionState.PRESSED,
            GlassInteractionState.IDLE,
        ),
    )

    fun isLegal(from: GlassInteractionState, to: GlassInteractionState): Boolean =
        from == to || legal[from]?.contains(to) == true
}

/**
 * 可复用交互上下文。非线程安全（限定主线程/UI 语义），不持有任何 Android/
 * Compose 引用，可被纯 JVM 单测驱动。
 *
 * 非法迁移策略：拒绝并返回 false，状态与 revision 保持不变（手势竞态下安全，
 * 不抛异常打断 UI）；调用方可用返回值做诊断。
 */
class GlassInteractionContext {

    var state: GlassInteractionState = GlassInteractionState.IDLE
        private set

    /** 最近一次指针位置（组件局部坐标；up/cancel 后保留为释放点）。 */
    var pointerX: Float = 0f
        private set
    var pointerY: Float = 0f
        private set

    /** 最近一次上报的速度向量（px/s 语义由接入方定义；down/up/cancel 清零）。 */
    var velocityX: Float = 0f
        private set
    var velocityY: Float = 0f
        private set

    /** 归一化进度输入 [0,1]（如展开比例、拖拽行程）。 */
    var progress: Float = 0f
        private set

    /** 指针是否处于按下序列中。 */
    var isPointerActive: Boolean = false
        private set

    /** 单调递增变更戳：每次被接受的变更 +1；被拒绝的迁移不动。 */
    var revision: Long = 0L
        private set

    // ── 状态事件 ───────────────────────────────────────────────────────────

    fun beginHover(): Boolean = transitionTo(GlassInteractionState.HOVER)
    fun endHover(): Boolean = transitionTo(GlassInteractionState.IDLE)

    /** 按下；若指针尚未登记，视为在当前位置按下。 */
    fun press(): Boolean = transitionTo(GlassInteractionState.PRESSED)

    /**
     * 释放：PRESSED/DRAGGING/EXPANDED → IDLE（触屏为主的产品默认无 hover 余留；
     * 悬停设备由接入方在 release 后显式 beginHover()）。
     */
    fun release(): Boolean = transitionTo(GlassInteractionState.IDLE)

    fun dragStart(): Boolean = transitionTo(GlassInteractionState.DRAGGING)
    fun dragEnd(): Boolean = transitionTo(GlassInteractionState.IDLE)
    fun expand(): Boolean = transitionTo(GlassInteractionState.EXPANDED)
    fun collapse(): Boolean = transitionTo(GlassInteractionState.IDLE)

    // ── 指针动态输入 ───────────────────────────────────────────────────────

    fun onPointerDown(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        velocityX = 0f
        velocityY = 0f
        isPointerActive = true
        revision++
    }

    fun onPointerMove(x: Float, y: Float, velocityXPerSec: Float = 0f, velocityYPerSec: Float = 0f) {
        pointerX = x
        pointerY = y
        velocityX = velocityXPerSec
        velocityY = velocityYPerSec
        revision++
    }

    fun onPointerUp() {
        if (!isPointerActive && state == GlassInteractionState.IDLE &&
            velocityX == 0f && velocityY == 0f
        ) {
            return // 无活跃序列：幂等空操作
        }
        velocityX = 0f
        velocityY = 0f
        isPointerActive = false
        revision++
    }

    /** 取消等价于 up（保留释放点，清速度）。 */
    fun onPointerCancel() = onPointerUp()

    fun updateProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped == progress) return
        progress = clamped
        revision++
    }

    // ── 生命周期 ───────────────────────────────────────────────────────────

    /** 强制归位：IDLE + 清全部动态输入（组件离开组合/复用前调用）。 */
    fun reset() {
        state = GlassInteractionState.IDLE
        pointerX = 0f
        pointerY = 0f
        velocityX = 0f
        velocityY = 0f
        progress = 0f
        isPointerActive = false
        revision++
    }

    private fun transitionTo(target: GlassInteractionState): Boolean {
        if (target == state) {
            return true // 自环幂等
        }
        if (!GlassInteractionTransitions.isLegal(state, target)) {
            return false
        }
        state = target
        revision++
        return true
    }
}
