package com.m1racle.reader.ui.pdf

/**
 * Pure host-level transition decisions for mutual exclusion and Back priority.
 *
 * Enum state already cannot be inline AND child at once; these helpers encode
 * the *visual* exclusion rules (collapse inline before child morph; section
 * while child open → close child first; Back: Child → inline → dismiss).
 */
enum class ReaderMoreBackAction {
    /** Capsule jump card (screen-level; host may not own this). */
    CLOSE_CAPSULE,
    /** Reverse-morph Child → MAIN. */
    CLOSE_CHILD,
    /** Collapse inline 阅读方式/阅读界面 → MAIN. */
    COLLAPSE_INLINE,
    /** Reverse-morph Parent → circle / CLOSED. */
    DISMISS_PARENT,
}

data class SectionToggleDecision(
    /** Immediate next state when no child reverse-morph is required. */
    val immediateState: ReaderMoreState? = null,
    /** Start Child reverse-morph; apply [pendingInline] after collapse. */
    val closeChildFirst: Boolean = false,
    val pendingInline: ReaderMoreState? = null,
)

data class OpenChildDecision(
    val nextChild: ReaderMoreState,
    /** Always collapse inline progress before freezing source bounds. */
    val collapseInlineFirst: Boolean = true,
)

object ReaderMoreTransitions {

    /**
     * Back priority (OBJECTIVE §十七): Child open → close Child only;
     * else if only Parent low-level inline expanded → collapse inline;
     * else dismiss Parent/menu.
     */
    fun onBack(
        current: ReaderMoreState,
        childClosing: Boolean = false,
        capsuleExpanded: Boolean = false,
    ): ReaderMoreBackAction = when {
        capsuleExpanded -> ReaderMoreBackAction.CLOSE_CAPSULE
        current.isChild() && !childClosing -> ReaderMoreBackAction.CLOSE_CHILD
        current.isPreparing() -> ReaderMoreBackAction.COLLAPSE_INLINE // cancel prep → Main
        current.isInline() -> ReaderMoreBackAction.COLLAPSE_INLINE
        current != ReaderMoreState.CLOSED -> ReaderMoreBackAction.DISMISS_PARENT
        else -> ReaderMoreBackAction.DISMISS_PARENT
    }

    /**
     * Section (阅读方式 / 阅读界面) while a Child is open: close Child first,
     * then run the inline expansion after reverse-morph completes.
     * Otherwise normal accordion toggle.
     */
    fun onSectionToggle(
        current: ReaderMoreState,
        section: ReaderMoreState,
        childClosing: Boolean = false,
    ): SectionToggleDecision {
        require(section.isInline()) { "section must be INLINE_*" }
        return when {
            current.isChild() && !childClosing -> SectionToggleDecision(
                closeChildFirst = true,
                pendingInline = section,
            )
            current.isChild() && childClosing -> SectionToggleDecision(
                // Already closing; remember the pending section.
                closeChildFirst = true,
                pendingInline = section,
            )
            else -> SectionToggleDecision(
                immediateState = ReaderMoreStateMachine.onSectionToggle(current, section),
            )
        }
    }

    /**
     * Opening advanced: from Basic → PREPARING (collapse first); from Main → CHILD.
     */
    fun onOpenChild(
        current: ReaderMoreState,
        child: ReaderMoreState,
    ): OpenChildDecision {
        require(child.isChild()) { "child must be CHILD_*" }
        val next = ReaderMoreStateMachine.onOpenChild(current, child)
        return OpenChildDecision(
            nextChild = next,
            collapseInlineFirst = current.isInline() || next.isPreparing(),
        )
    }

    /**
     * After Child reverse-morph completes: land on pending inline, else MAIN.
     */
    fun afterChildCollapsed(pendingInline: ReaderMoreState?): ReaderMoreState =
        if (pendingInline != null && pendingInline.isInline()) {
            ReaderMoreStateMachine.onSectionToggle(ReaderMoreState.MAIN, pendingInline)
        } else {
            ReaderMoreState.MAIN
        }

    /** True when inline progress must be forced to 0 before Child morph. */
    fun mustCollapseInlineForChild(current: ReaderMoreState): Boolean =
        current.isChild() || current.isInline()
}
