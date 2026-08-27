package com.markleaf.app.ui.reader

import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.repository.ReadingRepository
import com.markleaf.app.ui.annotation.AnnotationCreationFlow
import com.markleaf.app.ui.annotation.AnnotationOverlayItem
import com.markleaf.app.ui.annotation.AnnotationType
import com.markleaf.app.ui.annotation.RepositoryAnnotationOverlaySource
import com.markleaf.app.ui.annotation.TextSelection

/**
 * R26.1 PDF 高亮控制器：Selection → AnnotationCreationFlow → Repository →
 * OverlaySource 的编排门面。UI 只与本类对话，不直访仓库/DAO。
 * 纯 JVM 可测（依赖接口面 + 数据类）。
 */
class ReaderHighlightController(
    private val readingRepository: ReadingRepository,
    private val documentId: String,
) {

    sealed class CommitResult {
        data class Success(val id: Long) : CommitResult()
        data class Failure(val message: String) : CommitResult()
    }

    private val flow = AnnotationCreationFlow(readingRepository, documentId)
    private val overlaySource = RepositoryAnnotationOverlaySource(readingRepository)

    /** 指定页的覆盖项快照（恢复端：重开文档/翻页时调用）。 */
    suspend fun itemsForPage(page: Int): List<AnnotationOverlayItem> =
        overlaySource.highlightItemsFor(documentId, "PAGE_INDEX", page)

    /** R26.2 管理列表：全量高亮（location ASC, createdAt ASC —— DAO 排序契约）。 */
    suspend fun listForDocument(): List<HighlightEntity> =
        readingRepository.highlightsForDocument(documentId)

    /** R27：恢复某条高亮的抽象锚点（Note 关联键来源）。 */
    suspend fun anchorOf(highlight: HighlightEntity): com.markleaf.app.data.anchor.DocumentAnchor? {
        if (highlight.documentId != documentId) return null
        return com.markleaf.app.data.anchor.AnnotationAnchorResolvers
            .forLocationKind(highlight.locationKind)
            ?.resolve(
                locationKind = highlight.locationKind,
                location = highlight.location,
                anchorRaw = highlight.anchor,
                legacyAnchorStart = highlight.anchorStart,
                legacyAnchorEnd = highlight.anchorEnd,
            )
    }

    /**
     * 提交高亮：内部驱动 AnnotationCreationFlow
     * select → prepareDraft(HIGHLIGHT) → commit。
     */
    suspend fun commitHighlight(selection: TextSelection.PdfSelection, nowMs: Long = System.currentTimeMillis()): CommitResult {
        flow.select(selection)
        when (val draft = flow.prepareDraft(AnnotationType.HIGHLIGHT)) {
            is AnnotationCreationFlow.Stage.DraftReady -> Unit
            is AnnotationCreationFlow.Stage.Failed -> return CommitResult.Failure(draft.message)
            else -> return CommitResult.Failure("flow not in draft stage")
        }
        return when (val committed = flow.commit(nowMs)) {
            is AnnotationCreationFlow.Stage.Committed -> CommitResult.Success(committed.id)
            is AnnotationCreationFlow.Stage.Failed -> CommitResult.Failure(committed.message)
            else -> CommitResult.Failure("unexpected terminal stage")
        }
    }

    /** 删除高亮。 */
    suspend fun deleteHighlight(id: Long) = readingRepository.removeHighlight(documentId, id)

    fun currentStage(): AnnotationCreationFlow.Stage = flow.current()
}
