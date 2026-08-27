package com.markleaf.app.data.repository

import com.markleaf.app.data.anchor.AnchorCodec
import com.markleaf.app.data.anchor.AnnotationAnchorResolvers
import com.markleaf.app.data.anchor.DocumentAnchor
import com.markleaf.app.data.local.DocumentEntity
import com.markleaf.app.data.local.HighlightEntity
import com.markleaf.app.data.local.NoteEntity
import com.markleaf.app.data.local.SearchContentType
import com.markleaf.app.data.local.SearchIndexDao
import com.markleaf.app.data.local.SearchIndexEntity
import com.markleaf.app.data.local.SearchIndexWithDocument

/** A local-search hit with a complete, document-scoped navigation target. */
data class SearchResult(
    val documentId: String,
    val documentTitle: String,
    val contentType: SearchContentType,
    val sourceId: String,
    val content: String,
    val anchor: DocumentAnchor,
    val locationKind: String,
    val location: Int,
    val indexedAt: Long,
)

/**
 * R29 single local-search boundary. Source entities are projected into the
 * derived index here; callers never write SearchIndexDao directly.
 */
class SearchRepository(
    private val searchIndexDao: SearchIndexDao,
) {

    /**
     * Index a document's human-facing title at its stable root location.
     * Reading progress is deliberately not copied into the derived index.
     */
    suspend fun index(document: DocumentEntity, indexedAt: Long = System.currentTimeMillis()): Boolean {
        val content = listOfNotNull(document.title, document.displayName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
        val anchor = if (document.isPdf) {
            DocumentAnchor.PdfPage(page = 0)
        } else {
            DocumentAnchor.MarkdownBlock(blockIndex = 0)
        }
        return upsertOrDelete(
            documentId = document.id,
            contentType = SearchContentType.DOCUMENT,
            sourceId = document.id,
            content = content,
            anchor = anchor,
            indexedAt = indexedAt,
        )
    }

    /** Blank excerpts are not searchable and remove any stale derived row. */
    suspend fun index(highlight: HighlightEntity, indexedAt: Long = System.currentTimeMillis()): Boolean =
        upsertAnnotationOrDelete(
            documentId = highlight.documentId,
            contentType = SearchContentType.HIGHLIGHT,
            sourceId = highlight.id.toString(),
            content = highlight.excerpt.orEmpty(),
            locationKind = highlight.locationKind,
            location = highlight.location,
            anchorRaw = highlight.anchor,
            legacyAnchorStart = highlight.anchorStart,
            legacyAnchorEnd = highlight.anchorEnd,
            indexedAt = indexedAt,
        )

    /** Blank note content is not searchable and removes any stale derived row. */
    suspend fun index(note: NoteEntity, indexedAt: Long = System.currentTimeMillis()): Boolean =
        upsertAnnotationOrDelete(
            documentId = note.documentId,
            contentType = SearchContentType.NOTE,
            sourceId = note.id.toString(),
            content = note.content,
            locationKind = note.locationKind,
            location = note.location,
            anchorRaw = note.anchor,
            legacyAnchorStart = note.anchorStart,
            legacyAnchorEnd = note.anchorEnd,
            indexedAt = indexedAt,
        )

    suspend fun delete(documentId: String, contentType: SearchContentType, sourceId: String) {
        searchIndexDao.delete(documentId, contentType.name, sourceId)
    }

    /** Explicit rebuild/cleanup boundary; document deletion also cascades in Room. */
    suspend fun clearDocument(documentId: String) {
        searchIndexDao.deleteForDocument(documentId)
    }

    /** Global search by default; [documentId] narrows the same entry point. */
    suspend fun search(
        query: String,
        documentId: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResult> {
        require(limit in 1..MAX_LIMIT) { "limit must be in 1..$MAX_LIMIT" }
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val escapedQuery = escapeLike(normalizedQuery)
        val rows = if (documentId == null) {
            searchIndexDao.searchAll(escapedQuery, limit)
        } else {
            searchIndexDao.searchInDocument(documentId, escapedQuery, limit)
        }
        return rows.mapNotNull(::toResult)
    }

    private suspend fun upsertAnnotationOrDelete(
        documentId: String,
        contentType: SearchContentType,
        sourceId: String,
        content: String,
        locationKind: String,
        location: Int,
        anchorRaw: String?,
        legacyAnchorStart: Int?,
        legacyAnchorEnd: Int?,
        indexedAt: Long,
    ): Boolean {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty()) {
            searchIndexDao.delete(documentId, contentType.name, sourceId)
            return false
        }
        val resolver = requireNotNull(AnnotationAnchorResolvers.forLocationKind(locationKind)) {
            "unsupported annotation locationKind: $locationKind"
        }
        val anchor = requireNotNull(
            resolver.resolve(
                locationKind,
                location,
                anchorRaw,
                legacyAnchorStart,
                legacyAnchorEnd,
            ),
        ) { "annotation locator cannot be resolved" }
        return upsertOrDelete(
            documentId,
            contentType,
            sourceId,
            normalizedContent,
            anchor,
            indexedAt,
        )
    }

    private suspend fun upsertOrDelete(
        documentId: String,
        contentType: SearchContentType,
        sourceId: String,
        content: String,
        anchor: DocumentAnchor,
        indexedAt: Long,
    ): Boolean {
        val normalizedContent = content.trim()
        if (normalizedContent.isEmpty()) {
            searchIndexDao.delete(documentId, contentType.name, sourceId)
            return false
        }
        val projection = AnnotationAnchorResolvers.forAnchor(anchor).project(anchor)
        searchIndexDao.upsert(
            SearchIndexEntity(
                documentId = documentId,
                contentType = contentType.name,
                sourceId = sourceId,
                content = normalizedContent,
                locationKind = projection.locationKind,
                location = projection.location,
                anchor = projection.encoded,
                indexedAt = indexedAt,
            ),
        )
        return true
    }

    private fun toResult(row: SearchIndexWithDocument): SearchResult? {
        val contentType = SearchContentType.fromStorage(row.contentType) ?: return null
        val anchor = AnchorCodec.decode(row.anchor) ?: return null
        return SearchResult(
            documentId = row.documentId,
            documentTitle = row.documentTitle,
            contentType = contentType,
            sourceId = row.sourceId,
            content = row.content,
            anchor = anchor,
            locationKind = row.locationKind,
            location = row.location,
            indexedAt = row.indexedAt,
        )
    }

    private fun escapeLike(query: String): String = buildString(query.length) {
        query.forEach { character ->
            when (character) {
                '\\', '%', '_' -> append('\\')
            }
            append(character)
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
    }
}
