package com.markleaf.app.data.repository


import com.markleaf.app.data.anchor.AnnotationAnchorResolvers
import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.BookmarkDao
import com.markleaf.app.data.local.BookmarkEntity
import com.markleaf.app.data.local.BookmarkWithDocument
import com.markleaf.app.data.local.FavoriteDocumentDao
import com.markleaf.app.data.local.FavoriteDocumentEntity
import com.markleaf.app.data.local.HighlightDao
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteDao
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.data.local.SearchContentType
import kotlinx.coroutines.flow.Flow

/**
 * H2.1/R23 Reading Library 门面：bookmark / favorite / highlight / note。
 *
 * 权威边界（H2_1_READING_MODEL_AUTHORITY_REPORT.md）：阅读位置/进度唯一事实源是
 * documents 阅读字段族；本仓库只管理阅读资产（书签/收藏/标注）。
 * R23 起本仓库是**阅读器书签写入的唯一通道**——ReaderBookmarkController 是
 * UI 侧唯一调用方，UI 禁止直访 DAO。
 */
class ReadingRepository(
    private val bookmarkDao: BookmarkDao,
    private val favoriteDao: FavoriteDocumentDao,
    private val highlightDao: HighlightDao,
    private val noteDao: NoteDao,
    private val searchRepository: SearchRepository,
) {

    // ── Bookmarks ──────────────────────────────────────────────────────────

    suspend fun createBookmark(bookmark: BookmarkEntity): Long =
        bookmarkDao.insert(bookmark)

    suspend fun deleteBookmark(id: Long) {
        bookmarkDao.delete(id)
    }

    /** 创建时间升序（阅读顺序即书签顺序）。 */
    suspend fun bookmarksForDocument(documentId: String): List<BookmarkEntity> =
        bookmarkDao.listForDocument(documentId)

    /** Hub 书签页：全量书签（新在前）+ 所属文档名，响应式流。 */
    fun observeAllBookmarks(): Flow<List<BookmarkWithDocument>> =
        bookmarkDao.observeAll()

    // ── Reader bookmark toggle（R23：阅读器唯一写入路径） ──────────────────

    /** 同一 (文档, 坐标系, 位置) 上已存在的书签；无则 null。 */
    suspend fun findBookmarkAt(
        documentId: String,
        locationKind: String,
        location: Int,
    ): BookmarkEntity? = bookmarkDao.getAt(documentId, locationKind, location)

    /** 删除该位置上的书签。返回是否确有删除。 */
    suspend fun removeBookmarkAt(
        documentId: String,
        locationKind: String,
        location: Int,
    ): Boolean = bookmarkDao.deleteAt(documentId, locationKind, location) > 0

    /** 该文档在指定坐标系下全部已标注位置集合（用于阅读器状态图标）。 */
    suspend fun bookmarkedLocations(documentId: String, locationKind: String): Set<Int> =
        bookmarkDao.listForDocumentAllKinds(documentId)
            .filter { it.locationKind == locationKind }
            .map { it.location }
            .toSet()

    // ── Highlights / Notes（R24：anchor-based，唯一保存/恢复通道） ──────────

    /**
     * 保存高亮：锚点经解析器投影（kind/location 派生列 + 规范化 anchor 原文）。
     * PDF 传 [DocumentAnchor.PdfPage]，Markdown 传 [DocumentAnchor.MarkdownBlock]。
     */
    suspend fun addHighlight(
        documentId: String,
        anchor: DocumentAnchor,
        createdAt: Long,
        excerpt: String? = null,
    ): Long {
        val projection = AnnotationAnchorResolvers.forAnchor(anchor).project(anchor)
        val row = HighlightEntity(
            documentId = documentId,
            locationKind = projection.locationKind,
            location = projection.location,
            anchor = projection.encoded,
            excerpt = excerpt,
            createdAt = createdAt,
        )
        val id = highlightDao.insert(row)
        searchRepository.index(row.copy(id = id), indexedAt = createdAt)
        return id
    }

    /** 恢复单条高亮的抽象锚点（历史行按 kind/location 合成）。 */
    suspend fun resolveHighlightAnchor(documentId: String, id: Long): DocumentAnchor? {
        val row = highlightDao.getById(documentId, id) ?: return null
        val resolver = AnnotationAnchorResolvers.forLocationKind(row.locationKind) ?: return null
        return resolver.resolve(row.locationKind, row.location, row.anchor, row.anchorStart, row.anchorEnd)
    }

    suspend fun removeHighlight(documentId: String, id: Long) {
        highlightDao.delete(documentId, id)
        searchRepository.delete(documentId, SearchContentType.HIGHLIGHT, id.toString())
    }

    suspend fun highlightsForDocument(documentId: String): List<HighlightEntity> =
        highlightDao.listForDocument(documentId)

    suspend fun highlightsAtLocation(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<HighlightEntity> = highlightDao.listAtLocation(documentId, locationKind, location)

    /** 保存批注：锚点投影同 [addHighlight]。 */
    suspend fun addNote(
        documentId: String,
        anchor: DocumentAnchor,
        content: String,
        createdAt: Long,
        updatedAt: Long = createdAt,
    ): Long {
        val projection = AnnotationAnchorResolvers.forAnchor(anchor).project(anchor)
        val row = NoteEntity(
            documentId = documentId,
            locationKind = projection.locationKind,
            location = projection.location,
            anchor = projection.encoded,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        val id = noteDao.insert(row)
        searchRepository.index(row.copy(id = id), indexedAt = updatedAt)
        return id
    }

    /** 恢复单条批注的抽象锚点。 */
    suspend fun resolveNoteAnchor(documentId: String, id: Long): DocumentAnchor? {
        val row = noteDao.getById(documentId, id) ?: return null
        val resolver = AnnotationAnchorResolvers.forLocationKind(row.locationKind) ?: return null
        return resolver.resolve(row.locationKind, row.location, row.anchor, row.anchorStart, row.anchorEnd)
    }

    suspend fun updateNoteContent(
        documentId: String,
        id: Long,
        content: String,
        updatedAt: Long,
    ) {
        noteDao.updateContent(documentId, id, content, updatedAt)
        noteDao.getById(documentId, id)?.let { searchRepository.index(it, indexedAt = updatedAt) }
    }

    suspend fun removeNote(documentId: String, id: Long) {
        noteDao.delete(documentId, id)
        searchRepository.delete(documentId, SearchContentType.NOTE, id.toString())
    }

    suspend fun notesForDocument(documentId: String): List<NoteEntity> =
        noteDao.listForDocument(documentId)

    suspend fun notesAtLocation(
        documentId: String,
        locationKind: String,
        location: Int,
    ): List<NoteEntity> = noteDao.listAtLocation(documentId, locationKind, location)

    // ── Favorites ──────────────────────────────────────────────────────────

    /** 幂等：重复收藏保留首次 createdAt。返回是否新收藏。 */
    suspend fun addFavorite(documentId: String, createdAt: Long): Boolean =
        favoriteDao.insert(FavoriteDocumentEntity(documentId, createdAt)) != -1L

    suspend fun removeFavorite(documentId: String) {
        favoriteDao.delete(documentId)
    }

    suspend fun isFavorite(documentId: String): Boolean =
        favoriteDao.get(documentId) != null

    /** 收藏列表：createdAt 降序（最新收藏在前）。 */
    suspend fun favorites(): List<FavoriteDocumentEntity> =
        favoriteDao.getAll().sortedByDescending { it.createdAt }
}
