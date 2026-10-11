package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.AnnotationAnchorResolvers
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.repository.ReadingRepository

/** 标注种类。 */
enum class AnnotationType { HIGHLIGHT, NOTE }

/**
 * R25 标注创建流状态机：Selection → Anchor → Draft → Repository。
 *
 * 合法迁移（线性主路径 + 任意阶段取消）：
 * ```
 * IDLE ──select(合法)──→ SELECTION_READY ──prepare(type)──→ DRAFT_READY
 *  ↑                            │ 非法选择 → FAILED              │ commit()
 *  └────── cancel()/reset() ────┴────────────────────────────────┘→ COMMITTED → IDLE
 * ```
 * - `select` 在进入状态机时就地解析锚点（非法选择直接失败，不带病前进）；
 * - commit 幂等保护：COMMITTED 后再次 commit 返回 Failed(AlreadyCommitted)；
 * - 状态与转换全部纯 JVM 可测；仓库只在 commit 这一步触达。
 */
class AnnotationCreationFlow(
    private val readingRepository: ReadingRepository,
    private val documentId: String,
) {

    sealed class Stage {
        data object Idle : Stage()

        /** 选择已接收且锚点解析成功。 */
        data class SelectionReady(val selection: TextSelection, val anchor: DocumentAnchor) : Stage()

        /** 草稿就绪（类型已定，NOTE 可带内容）。 */
        data class DraftReady(
            val type: AnnotationType,
            val anchor: DocumentAnchor,
            val content: String?,
            val excerpt: String?,
            val style: com.m1racle.reader.data.anchor.HighlightStyle = com.m1racle.reader.data.anchor.HighlightStyle.Yellow,
        ) : Stage()

        data class Committed(val id: Long, val type: AnnotationType) : Stage()

        data class Failed(val reason: Reason, val message: String) : Stage()

        enum class Reason { INVALID_SELECTION, EMPTY_NOTE_CONTENT, ALREADY_COMMITTED }
    }

    private var stage: Stage = Stage.Idle

    fun current(): Stage = stage

    /** 终态语义：把终态作为结果返回，同时立即回收状态机到 Idle（可观察、不留脏态）。 */
    private fun terminal(result: Stage): Stage {
        stage = Stage.Idle
        return result
    }

    /** 接收用户选择并解析锚点。非法选择 → Failed(INVALID_SELECTION)。 */
    fun select(selection: TextSelection): Stage {
        val anchor = selection.toAnchor()
        return if (selection.isValid() && anchor != null) {
            Stage.SelectionReady(selection, anchor).also { stage = it }
        } else {
            terminal(Stage.Failed(Stage.Reason.INVALID_SELECTION, "selection is empty or out of range"))
        }
    }

    /**
     * 准备草稿。HIGHLIGHT 只需类型；NOTE 要求非空 content，
     * 否则 Failed(EMPTY_NOTE_CONTENT)。
     */
    fun prepareDraft(type: AnnotationType, content: String? = null, style: com.m1racle.reader.data.anchor.HighlightStyle = com.m1racle.reader.data.anchor.HighlightStyle.Yellow): Stage {
        val ready = stage as? Stage.SelectionReady ?: return failNotSelectable()
        return when (type) {
            AnnotationType.HIGHLIGHT -> {
                Stage.DraftReady(
                    type = type,
                    anchor = ready.anchor,
                    content = null,
                    excerpt = ready.selection.excerpt,
                    style = style,
                ).also { stage = it }
            }
            AnnotationType.NOTE -> {
                val text = content?.trim().orEmpty()
                if (text.isEmpty()) {
                    terminal(Stage.Failed(Stage.Reason.EMPTY_NOTE_CONTENT, "note content required"))
                } else {
                    Stage.DraftReady(
                        type = type,
                        anchor = ready.anchor,
                        content = text,
                        excerpt = ready.selection.excerpt,
                    ).also { stage = it }
                }
            }
        }
    }

    /**
     * 提交到 Repository（唯一持久化步）。非 DRAFT_READY 或重复提交 → 失败且不落库。
     */
    suspend fun commit(nowMs: Long = System.currentTimeMillis()): Stage {
        val draft = stage as? Stage.DraftReady
        if (draft == null) {
            return terminal(Stage.Failed(Stage.Reason.ALREADY_COMMITTED, "no draft to commit"))
        }
        val resolver = AnnotationAnchorResolvers.forAnchor(draft.anchor)
        val projection = resolver.project(draft.anchor)
        val id = when (draft.type) {
            AnnotationType.HIGHLIGHT -> readingRepository.addHighlight(
                documentId = documentId,
                anchor = draft.anchor,
                createdAt = nowMs,
                excerpt = draft.excerpt,
                style = draft.style,
            )
            AnnotationType.NOTE -> readingRepository.addNote(
                documentId = documentId,
                anchor = draft.anchor,
                content = draft.content.orEmpty(),
                createdAt = nowMs,
            )
        }
        // 投影列与锚点编码必须自洽——防御性校验（理论不可达）。
        check(projection.locationKind.isNotEmpty())
        return terminal(Stage.Committed(id = id, type = draft.type))
    }

    /** 任意阶段取消，回到 Idle。 */
    fun cancel() {
        stage = Stage.Idle
    }

    private fun failNotSelectable(): Stage =
        terminal(Stage.Failed(Stage.Reason.INVALID_SELECTION, "flow is not in SELECTION_READY"))
}
