package com.markleaf.app.pdf

import kotlin.math.abs

/**
 * PDF 内容在屏幕上的实际运动方向。
 *
 * R17 ground truth（本轮禁止反转，方向测量层不动）：
 * CONTENT_MOVING_UP   = 同一 PDF 锚点下一帧 screenY 更小（anchorDeltaY < 0），
 *                       内容肉眼向屏幕顶部移动（通常对应用户向上滑继续阅读）。
 * CONTENT_MOVING_DOWN = 锚点下一帧 screenY 更大（anchorDeltaY > 0），
 *                       内容肉眼向屏幕底部移动（通常对应用户向下滑寻找控制）。
 */
enum class ChromeContentDirection { UP, DOWN }

/**
 * 阅读器顶部 Chrome 的四态正交组合（capsuleVisible × edgeButtonsVisible）。
 * 允许四种合法组合，CAPSULE_ONLY 是正式状态，不得为兼容旧枚举而禁止。
 */
enum class PdfChromeLevel {
    /** capsule=true, buttons=true */
    FULL,
    /** capsule=false, buttons=true */
    BUTTONS_ONLY,
    /** capsule=true, buttons=false（仅单击可进入） */
    CAPSULE_ONLY,
    /** capsule=false, buttons=false */
    HIDDEN,
}

/** 状态迁移原因（日志/验收证据）。 */
enum class ChromeTriggerReason {
    CAPSULE_HIDE_THRESHOLD,      // 滚动累计：收纳第一级（FULL/BUTTONS_ONLY 中的胶囊隐藏）
    BUTTONS_HIDE_THRESHOLD,      // 滚动累计：收纳第二级（两圆隐藏）
    CAPSULE_SHOW_THRESHOLD,      // 滚动累计：恢复第二级（胶囊出现）
    BUTTONS_SHOW_THRESHOLD,      // 滚动累计：恢复第一级（两圆出现）
    FLING_HIDE_CAPSULE,          // 快速 fling：收纳第一级
    FLING_HIDE_BUTTONS,          // 快速 fling：收纳第二级
    FLING_SHOW_BUTTONS,          // 快速 fling：恢复第一级
    FLING_SHOW_CAPSULE,          // 快速 fling：恢复第二级
    TAP_CAPSULE_OFF,             // 正文单击：只切胶囊（隐藏）
    TAP_CAPSULE_ON,              // 正文单击：只切胶囊（显示）
    SESSION_RESET,               // 菜单关闭等：清除手势残留
}

/** 单次状态迁移事件（UI 层据此更新渲染状态并打日志）。 */
data class ChromeStateChange(
    val userGestureId: Long,
    val direction: ChromeContentDirection?,
    /** 触发样本的原始 delta（px，未解释，仅日志）。 */
    val rawDeltaPx: Float,
    /** 实测锚点屏幕位移（px，ground truth；<0 = 内容向上，>0 = 内容向下）。 */
    val anchorDeltaY: Float,
    /** 触发时该阶段累计的有效位移（dp）。 */
    val effectiveDistanceDp: Float,
    val levelBefore: PdfChromeLevel,
    val levelAfter: PdfChromeLevel,
    val reason: ChromeTriggerReason,
) {
    /** 迁移后的胶囊可见性（渲染用）。 */
    val capsuleVisibleAfter: Boolean
        get() = levelAfter == PdfChromeLevel.FULL || levelAfter == PdfChromeLevel.CAPSULE_ONLY

    /** 迁移后的左右圆可见性（渲染用）。 */
    val edgeButtonsVisibleAfter: Boolean
        get() = levelAfter == PdfChromeLevel.FULL || levelAfter == PdfChromeLevel.BUTTONS_ONLY
}

/**
 * 滚动适配层：把「同一 PDF 锚点的实测屏幕位移」映射为语义方向。
 * R17 ground truth，本轮禁止反转；只允许修改状态机产品行为表。
 */
fun mapScrollToContentMotion(anchorDeltaY: Float): ChromeContentDirection? = when {
    anchorDeltaY < 0f -> ChromeContentDirection.UP
    anchorDeltaY > 0f -> ChromeContentDirection.DOWN
    else -> null
}

/**
 * 阅读器 Chrome 滚动驱动状态机的集中参数（dp 阈值，实机调校入口）。
 * 交互语义稳定前禁止调整 36/56/32/28 等距离阈值。
 */
data class PdfChromeScrollParams(
    /** 收纳第一级（胶囊隐藏）：内容向上。 */
    val hideCapsuleDp: Float = 36f,
    /** 收纳第二级（两圆隐藏）：内容向上，追加。 */
    val hideButtonsAddDp: Float = 56f,
    /** 恢复第一级（两圆出现）：内容向下。 */
    val showButtonsDp: Float = 32f,
    /** 恢复第二级（胶囊出现）：内容向下，追加。 */
    val showCapsuleAddDp: Float = 28f,
    /**
     * 方向噪声 epsilon（dp）：subpixel 级抖动视为 NO_MOTION_SAMPLE。
     * 真实位移（哪怕 120Hz 慢拖每帧仅 1dp）一律累计，保证帧率无关。
     */
    val deadZoneDp: Float = 0.5f,
    /** 方向确认距离（cooldown 内加倍以抑制反向抖动）。 */
    val reverseConfirmDp: Float = 12f,
    /** 状态迁移后的防反向抖动冷却（同方向迁移不受冷却限制）。 */
    val cooldownMs: Long = 350L,
    /** fling 快速路径最小速度（dp/s）。 */
    val flingMinSpeedDpPerSec: Float = 1400f,
    /** fling 快速路径最小位移（dp）。 */
    val flingMinDistanceDp: Float = 24f,
    /** 打开文档后的初始化锁定（ms）。 */
    val initialLockMs: Long = 2500L,
)

/**
 * 滚动/单击驱动的 Chrome 显隐状态机（纯逻辑，可单测）。
 *
 * R18 产品语义（r18.md 一~四条）：
 *  1. 内容向上（CONTENT_MOVING_UP，用户向上滑继续阅读）＝ 收纳方向：
 *     FULL →36→ BUTTONS_ONLY（胶囊先收）→56→ HIDDEN（两圆后收）。
 *  2. 内容向下（CONTENT_MOVING_DOWN，用户向下滑寻找控制）＝ 恢复方向：
 *     HIDDEN →32→ BUTTONS_ONLY（两圆先现）→28→ FULL（胶囊后现）。
 *  3. 正文单击（Tap controller）只 toggle 胶囊：edgeButtonsVisible 永不变。
 *     FULL↔BUTTONS_ONLY；HIDDEN↔CAPSULE_ONLY。
 *  4. 滚动与点击解耦：Scroll controller 负责两个维度，Tap controller 只负责胶囊。
 *
 * 保留 R17 全部机制：anchor epoch/reset（UI 层）、remainder/overshoot、
 * subpixel 累计、transform/programmatic 隔离、手势 session 生命周期、诊断日志。
 */
class PdfChromeScrollStateMachine(
    private val params: PdfChromeScrollParams = PdfChromeScrollParams(),
) {

    var level: PdfChromeLevel = PdfChromeLevel.FULL
        private set

    var userGestureId: Long = 0L
        private set

    private var direction: ChromeContentDirection? = null
    private var phaseAccumDp: Float = 0f
    private var reverseAccumDp: Float = 0f
    private var flingAccumDp: Float = 0f
    private var pendingTarget: PdfChromeLevel? = null
    private var pendingThresholdDp: Float = 0f
    private var cooldownUntilMs: Long = 0L

    /** 一次真实手势开始（pointer down 后的首个滚动样本）。 */
    fun beginGesture() {
        userGestureId += 1
    }

    /** 手势结束（phase 回 IDLE）：清除累计与方向，保留 level（下次手势重新开始）。 */
    fun endGesture() {
        direction = null
        phaseAccumDp = 0f
        reverseAccumDp = 0f
        flingAccumDp = 0f
        pendingTarget = null
        pendingThresholdDp = 0f
    }

    /**
     * 滚动样本（每帧）。[deltaDp] 为该样本在该方向上的位移绝对值（dp）。
     * 迁移链与方向绑定（r18 一）：UP=收纳、DOWN=恢复；overshoot remainder
     * 保留给下一阶段，单帧大位移可连续消费多级。
     */
    fun onScrollSample(
        dir: ChromeContentDirection,
        deltaDp: Float,
        rawDeltaPx: Float,
        anchorDeltaYPx: Float,
        nowMs: Long,
    ): List<ChromeStateChange> {
        if (abs(deltaDp) < params.deadZoneDp) return emptyList()
        val changes = mutableListOf<ChromeStateChange>()
        val active = direction
        if (active != null && dir != active) {
            // 方向反转需要显式确认；cooldown 内确认距离加倍（防反向抖动）。
            // 同方向迁移永远不受 cooldown 阻碍。
            val confirm = if (nowMs < cooldownUntilMs) {
                params.reverseConfirmDp * 2f
            } else {
                params.reverseConfirmDp
            }
            reverseAccumDp += abs(deltaDp)
            if (reverseAccumDp >= confirm) {
                direction = dir
                reverseAccumDp = 0f
                phaseAccumDp = 0f
                flingAccumDp = 0f
                computePending(dir)
            }
        } else {
            if (active == null) {
                direction = dir
                reverseAccumDp = 0f
                computePending(dir)
            }
            phaseAccumDp += abs(deltaDp)
            var guard = 0
            while (pendingTarget != null && phaseAccumDp >= pendingThresholdDp && guard < 3) {
                guard++
                val target = pendingTarget!!
                val before = level
                level = target
                cooldownUntilMs = nowMs + params.cooldownMs
                changes += ChromeStateChange(
                    userGestureId = userGestureId,
                    direction = dir,
                    rawDeltaPx = rawDeltaPx,
                    anchorDeltaY = anchorDeltaYPx,
                    effectiveDistanceDp = phaseAccumDp,
                    levelBefore = before,
                    levelAfter = target,
                    reason = reasonFor(before, target),
                )
                // Overshoot 保留：单帧大位移连续消费多级，remainder 计入下一阶段。
                phaseAccumDp -= pendingThresholdDp
                reverseAccumDp = 0f
                computePending(dir)
            }
        }
        return changes
    }

    /**
     * 快速 fling：按方向约束直接迁移一级（UP=收纳、DOWN=恢复），
     * 视觉 stagger 由渲染层动画延迟实现。
     */
    fun onFling(
        dir: ChromeContentDirection,
        velocityDpPerSec: Float,
        distanceDp: Float,
        anchorDeltaYPx: Float,
        nowMs: Long,
    ): ChromeStateChange? {
        if (velocityDpPerSec < params.flingMinSpeedDpPerSec || distanceDp < params.flingMinDistanceDp) {
            return null
        }
        val before = level
        val target = transitionForFling(before, dir) ?: return null
        level = target
        cooldownUntilMs = nowMs + params.cooldownMs
        phaseAccumDp = 0f
        reverseAccumDp = 0f
        flingAccumDp = 0f
        computePending(dir)
        return ChromeStateChange(
            userGestureId = userGestureId,
            direction = dir,
            rawDeltaPx = 0f,
            anchorDeltaY = anchorDeltaYPx,
            effectiveDistanceDp = distanceDp,
            levelBefore = before,
            levelAfter = target,
            reason = flingReason(before, target),
        )
    }

    /**
     * 正文中央单击（Tap controller）：只 toggle 胶囊，两圆完全不变。
     * 单击后清除全部手势残留，从新 level 开始下一次手势 session。
     */
    fun onCenterTap(): ChromeStateChange {
        val before = level
        // 只切胶囊：两圆完全不变。
        val target = when (before) {
            PdfChromeLevel.FULL -> PdfChromeLevel.BUTTONS_ONLY // capsule off
            PdfChromeLevel.CAPSULE_ONLY -> PdfChromeLevel.HIDDEN // capsule off
            PdfChromeLevel.BUTTONS_ONLY -> PdfChromeLevel.FULL // capsule on
            PdfChromeLevel.HIDDEN -> PdfChromeLevel.CAPSULE_ONLY // capsule on
        }
        level = target
        resetForNewSession()
        return ChromeStateChange(
            userGestureId = userGestureId,
            direction = null,
            rawDeltaPx = 0f,
            anchorDeltaY = 0f,
            effectiveDistanceDp = 0f,
            levelBefore = before,
            levelAfter = target,
            reason = if (target == PdfChromeLevel.BUTTONS_ONLY || target == PdfChromeLevel.HIDDEN) {
                ChromeTriggerReason.TAP_CAPSULE_OFF
            } else {
                ChromeTriggerReason.TAP_CAPSULE_ON
            },
        )
    }

    /**
     * 清空全部手势残留（菜单打开/关闭等场景）：保留当前 level，
     * 从当前状态开始新的手势 session。
     */
    fun resetForNewSession() {
        direction = null
        phaseAccumDp = 0f
        reverseAccumDp = 0f
        flingAccumDp = 0f
        pendingTarget = null
        pendingThresholdDp = 0f
        cooldownUntilMs = 0L
    }

    /**
     * 显式方向迁移表（r18 一）：每条规则只属于一个方向。
     * UP＝收纳（信息层先收、导航层后收）；DOWN＝恢复（导航层先现、信息层后现）。
     * CAPSULE_ONLY 为合法状态（仅单击进入），滚动从该状态继续按方向迁移。
     */
    private fun transitionFor(
        level: PdfChromeLevel,
        dir: ChromeContentDirection,
    ): Pair<PdfChromeLevel, Float>? = when {
        // ── 内容向上（收纳方向）──
        level == PdfChromeLevel.FULL && dir == ChromeContentDirection.UP ->
            PdfChromeLevel.BUTTONS_ONLY to params.hideCapsuleDp
        level == PdfChromeLevel.BUTTONS_ONLY && dir == ChromeContentDirection.UP ->
            PdfChromeLevel.HIDDEN to params.hideButtonsAddDp
        level == PdfChromeLevel.CAPSULE_ONLY && dir == ChromeContentDirection.UP ->
            PdfChromeLevel.HIDDEN to params.hideCapsuleDp

        // ── 内容向下（恢复方向）──
        level == PdfChromeLevel.HIDDEN && dir == ChromeContentDirection.DOWN ->
            PdfChromeLevel.BUTTONS_ONLY to params.showButtonsDp
        level == PdfChromeLevel.BUTTONS_ONLY && dir == ChromeContentDirection.DOWN ->
            PdfChromeLevel.FULL to params.showCapsuleAddDp
        level == PdfChromeLevel.CAPSULE_ONLY && dir == ChromeContentDirection.DOWN ->
            PdfChromeLevel.FULL to params.showButtonsDp

        // 收纳/恢复链端点（HIDDEN×UP、FULL×DOWN）＝ 停留。
        else -> null
    }

    private fun transitionForFling(
        level: PdfChromeLevel,
        dir: ChromeContentDirection,
    ): PdfChromeLevel? = when {
        // UP fling：收纳一级。
        level == PdfChromeLevel.FULL && dir == ChromeContentDirection.UP -> PdfChromeLevel.BUTTONS_ONLY
        level == PdfChromeLevel.BUTTONS_ONLY && dir == ChromeContentDirection.UP -> PdfChromeLevel.HIDDEN
        level == PdfChromeLevel.CAPSULE_ONLY && dir == ChromeContentDirection.UP -> PdfChromeLevel.HIDDEN
        // DOWN fling：恢复一级。
        level == PdfChromeLevel.HIDDEN && dir == ChromeContentDirection.DOWN -> PdfChromeLevel.BUTTONS_ONLY
        level == PdfChromeLevel.BUTTONS_ONLY && dir == ChromeContentDirection.DOWN -> PdfChromeLevel.FULL
        level == PdfChromeLevel.CAPSULE_ONLY && dir == ChromeContentDirection.DOWN -> PdfChromeLevel.FULL
        else -> null
    }

    /** 按当前 level + 方向计算下一迁移目标（null = 停留）。 */
    private fun computePending(dir: ChromeContentDirection) {
        val target = transitionFor(level, dir)
        pendingTarget = target?.first
        pendingThresholdDp = target?.second ?: 0f
    }

    private fun reasonFor(
        before: PdfChromeLevel,
        after: PdfChromeLevel,
    ): ChromeTriggerReason = when {
        after == PdfChromeLevel.BUTTONS_ONLY && before == PdfChromeLevel.FULL ->
            ChromeTriggerReason.CAPSULE_HIDE_THRESHOLD
        after == PdfChromeLevel.HIDDEN && before == PdfChromeLevel.CAPSULE_ONLY ->
            ChromeTriggerReason.CAPSULE_HIDE_THRESHOLD
        after == PdfChromeLevel.HIDDEN -> ChromeTriggerReason.BUTTONS_HIDE_THRESHOLD
        after == PdfChromeLevel.BUTTONS_ONLY -> ChromeTriggerReason.BUTTONS_SHOW_THRESHOLD
        after == PdfChromeLevel.FULL && before == PdfChromeLevel.CAPSULE_ONLY ->
            ChromeTriggerReason.BUTTONS_SHOW_THRESHOLD
        else -> ChromeTriggerReason.CAPSULE_SHOW_THRESHOLD
    }

    private fun flingReason(
        before: PdfChromeLevel,
        after: PdfChromeLevel,
    ): ChromeTriggerReason = when {
        after == PdfChromeLevel.BUTTONS_ONLY && before == PdfChromeLevel.FULL ->
            ChromeTriggerReason.FLING_HIDE_CAPSULE
        after == PdfChromeLevel.HIDDEN && before == PdfChromeLevel.CAPSULE_ONLY ->
            ChromeTriggerReason.FLING_HIDE_CAPSULE
        after == PdfChromeLevel.HIDDEN -> ChromeTriggerReason.FLING_HIDE_BUTTONS
        after == PdfChromeLevel.BUTTONS_ONLY -> ChromeTriggerReason.FLING_SHOW_BUTTONS
        after == PdfChromeLevel.FULL && before == PdfChromeLevel.CAPSULE_ONLY ->
            ChromeTriggerReason.FLING_SHOW_BUTTONS
        else -> ChromeTriggerReason.FLING_SHOW_CAPSULE
    }
}
