package com.markleaf.app.ui.reader

import com.markleaf.app.data.anchor.AnnotationAnchorResolvers
import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.data.repository.ReadingRepository

/**
 * R27 阅读内批注（Note）控制器：UI → 本类 → ReadingRepository 的唯一写入路径。
 *
 * 与 Highlight 的关联语义（协议冻结，无新列）：**同 documentId + 等价 anchor**
 * ——由 [com.markleaf.app.data.anchor.AnchorCodec] 的确定性编码保证：
 * 同一锚点的编码字符串恒等，可直接作为关联键。
 */
class ReaderNoteController(
    private val readingRepository: ReadingRepository,
    private val documentId: String,
) {

    /** 当前文档全部批注（DAO 排序：location ASC, createdAt ASC）。 */
    suspend fun list(): List<NoteEntity> = readingRepository.notesForDocument(documentId)

    /** 在锚点上创建批注，返回 id。 */
    suspend fun create(
        anchor: DocumentAnchor,
        content: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        val normalizedContent = content.trim()
        require(normalizedContent.isNotEmpty()) { "note content required" }
        return readingRepository.addNote(
            documentId = documentId,
            anchor = anchor,
            content = normalizedContent,
            createdAt = nowMs,
        )
    }

    /** 编辑内容；DAO 层只更新 content/updatedAt —— createdAt 永不覆盖。 */
    suspend fun update(id: Long, content: String, nowMs: Long = System.currentTimeMillis()) {
        val normalizedContent = content.trim()
        require(normalizedContent.isNotEmpty()) { "note content required" }
        readingRepository.updateNoteContent(documentId, id, normalizedContent, nowMs)
    }

    /** 删除批注（不影响同锚点的高亮）。 */
    suspend fun delete(id: Long) {
        readingRepository.removeNote(documentId, id)
    }

    /** 编辑入口直接恢复已加载 Note 的抽象锚点，不产生第二次主键查询。 */
    fun anchorOf(note: NoteEntity): DocumentAnchor? {
        if (note.documentId != documentId) return null
        val resolver = AnnotationAnchorResolvers.forLocationKind(note.locationKind) ?: return null
        return resolver.resolve(
            note.locationKind,
            note.location,
            note.anchor,
            note.anchorStart,
            note.anchorEnd,
        )
    }

    /** 同锚点 Note 查询：复合索引先缩到单一位置，再比较规范化 anchor。 */
    suspend fun linkedAt(anchor: DocumentAnchor): NoteEntity? {
        val projection = AnnotationAnchorResolvers.forAnchor(anchor).project(anchor)
        return findLinked(
            readingRepository.notesAtLocation(
                documentId,
                projection.locationKind,
                projection.location,
            ),
            anchor,
        )
    }

    companion object {
        /** 关联键：锚点编码串（同 documentId 范围内比较）。 */
        fun anchorKey(anchor: DocumentAnchor): String =
            com.markleaf.app.data.anchor.AnchorCodec.encode(anchor)

        fun anchorKeyOf(note: NoteEntity): String = note.anchor.orEmpty()

        /** 在批注集合中找到与该锚点关联的第一条（用于「再点即编辑」语义）。 */
        fun findLinked(notes: List<NoteEntity>, anchor: DocumentAnchor): NoteEntity? {
            val key = anchorKey(anchor)
            return notes.firstOrNull { anchorKeyOf(it) == key }
        }
    }
}
