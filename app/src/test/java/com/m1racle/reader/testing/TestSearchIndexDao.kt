package com.m1racle.reader.testing

import com.m1racle.reader.data.local.SearchContentType
import com.m1racle.reader.data.local.SearchIndexDao
import com.m1racle.reader.data.local.SearchIndexEntity
import com.m1racle.reader.data.local.SearchIndexWithDocument
import com.m1racle.reader.data.repository.SearchRepository

/** Shared in-memory SearchIndex DAO for repository/controller JVM tests. */
class TestSearchIndexDao : SearchIndexDao {
    data class Key(val documentId: String, val contentType: String, val sourceId: String)

    val rows = LinkedHashMap<Key, SearchIndexEntity>()
    val documentTitles = mutableMapOf<String, String>()

    override suspend fun upsert(entry: SearchIndexEntity) {
        rows[Key(entry.documentId, entry.contentType, entry.sourceId)] = entry
    }

    override suspend fun delete(documentId: String, contentType: String, sourceId: String) {
        rows.remove(Key(documentId, contentType, sourceId))
    }

    override suspend fun deleteForDocument(documentId: String) {
        rows.entries.removeAll { it.value.documentId == documentId }
    }

    override suspend fun searchAll(escapedQuery: String, limit: Int): List<SearchIndexWithDocument> =
        matching(escapedQuery, documentId = null, limit)

    override suspend fun searchInDocument(
        documentId: String,
        escapedQuery: String,
        limit: Int,
    ): List<SearchIndexWithDocument> = matching(escapedQuery, documentId, limit)

    private fun matching(
        escapedQuery: String,
        documentId: String?,
        limit: Int,
    ): List<SearchIndexWithDocument> {
        val query = unescapeLike(escapedQuery)
        return rows.values
            .filter { documentId == null || it.documentId == documentId }
            .filter { it.content.contains(query, ignoreCase = true) }
            .sortedWith(
                compareBy<SearchIndexEntity> { typeRank(it.contentType) }
                    .thenByDescending { it.indexedAt }
                    .thenBy { it.documentId }
                    .thenBy { it.sourceId },
            )
            .take(limit)
            .map { row ->
                SearchIndexWithDocument(
                    documentId = row.documentId,
                    contentType = row.contentType,
                    sourceId = row.sourceId,
                    content = row.content,
                    locationKind = row.locationKind,
                    location = row.location,
                    anchor = row.anchor,
                    indexedAt = row.indexedAt,
                    documentTitle = documentTitles[row.documentId] ?: row.documentId,
                )
            }
    }

    private fun unescapeLike(value: String): String = buildString(value.length) {
        var escaped = false
        value.forEach { character ->
            if (escaped) {
                append(character)
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else {
                append(character)
            }
        }
        if (escaped) append('\\')
    }

    private fun typeRank(value: String): Int = when (value) {
        SearchContentType.DOCUMENT.name -> 0
        SearchContentType.HIGHLIGHT.name -> 1
        SearchContentType.NOTE.name -> 2
        else -> 3
    }
}

fun testSearchRepository(dao: TestSearchIndexDao = TestSearchIndexDao()): SearchRepository =
    SearchRepository(dao)
