package com.m1racle.reader.data.repository

import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.NormalizedRect
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.data.local.SearchContentType
import com.m1racle.reader.data.local.SearchIndexDao
import com.m1racle.reader.data.local.SearchIndexEntity
import com.m1racle.reader.data.local.SearchIndexWithDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R29 SearchRepository creation/update/delete/isolation/navigation contracts. */
class SearchRepositoryTest {

    private class FakeSearchIndexDao : SearchIndexDao {
        private data class Key(val documentId: String, val contentType: String, val sourceId: String)

        val rows = LinkedHashMap<Key, SearchIndexEntity>()

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
                        documentTitle = row.documentId,
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

    @Test
    fun `creates document highlight and note index entries`() = runTest {
        val dao = FakeSearchIndexDao()
        val repo = SearchRepository(dao)
        assertTrue(repo.index(document("doc", title = "呼吸系统指南"), indexedAt = 1L))
        assertTrue(repo.index(highlight("doc", id = 7, page = 9, excerpt = "呼吸系统高亮"), indexedAt = 2L))
        assertTrue(repo.index(note("doc", id = 8, block = 4, content = "呼吸系统批注"), indexedAt = 3L))

        val results = repo.search("呼吸系统")
        assertEquals(3, results.size)
        assertEquals(
            listOf(SearchContentType.DOCUMENT, SearchContentType.HIGHLIGHT, SearchContentType.NOTE),
            results.map { it.contentType },
        )
        assertEquals(3, dao.rows.size)
    }

    @Test
    fun `results sort by type then newest source within the same type`() = runTest {
        val repo = SearchRepository(FakeSearchIndexDao())
        repo.index(note("doc", id = 1, block = 1, content = "排序词 older"), indexedAt = 10L)
        repo.index(note("doc", id = 2, block = 2, content = "排序词 newer"), indexedAt = 30L)
        repo.index(highlight("doc", id = 3, page = 3, excerpt = "排序词 highlight"), indexedAt = 20L)

        val results = repo.search("排序词")
        assertEquals(
            listOf(SearchContentType.HIGHLIGHT, SearchContentType.NOTE, SearchContentType.NOTE),
            results.map { it.contentType },
        )
        assertEquals(listOf("3", "2", "1"), results.map { it.sourceId })
    }

    @Test
    fun `reindex updates one source row instead of duplicating it`() = runTest {
        val dao = FakeSearchIndexDao()
        val repo = SearchRepository(dao)
        val initial = note("doc", id = 5, block = 2, content = "旧内容")
        repo.index(initial, indexedAt = 10L)
        repo.index(initial.copy(content = "新内容", updatedAt = 20L), indexedAt = 20L)

        assertTrue(repo.search("旧内容").isEmpty())
        val updated = repo.search("新内容").single()
        assertEquals("5", updated.sourceId)
        assertEquals(20L, updated.indexedAt)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `deletes one entry or all entries for a document`() = runTest {
        val dao = FakeSearchIndexDao()
        val repo = SearchRepository(dao)
        repo.index(document("doc", title = "共同词"), indexedAt = 1L)
        repo.index(highlight("doc", id = 1, page = 1, excerpt = "共同词"), indexedAt = 2L)
        repo.index(note("doc", id = 2, block = 1, content = "共同词"), indexedAt = 3L)

        repo.delete("doc", SearchContentType.HIGHLIGHT, "1")
        assertEquals(
            listOf(SearchContentType.DOCUMENT, SearchContentType.NOTE),
            repo.search("共同词").map { it.contentType },
        )

        repo.clearDocument("doc")
        assertTrue(repo.search("共同词").isEmpty())
    }

    @Test
    fun `updates deletes and queries are isolated by document`() = runTest {
        val dao = FakeSearchIndexDao()
        val repo = SearchRepository(dao)
        repo.index(note("doc-A", id = 1, block = 3, content = "needle A"), indexedAt = 1L)
        repo.index(note("doc-B", id = 1, block = 7, content = "needle B"), indexedAt = 2L)

        repo.index(note("doc-A", id = 1, block = 4, content = "needle A2"), indexedAt = 3L)
        assertEquals(listOf("doc-A"), repo.search("A2", documentId = "doc-A").map { it.documentId })
        assertTrue(repo.search("A2", documentId = "doc-B").isEmpty())

        repo.delete("doc-A", SearchContentType.NOTE, "1")
        assertEquals(listOf("doc-B"), repo.search("needle").map { it.documentId })
        assertEquals(7, repo.search("needle").single().location)
    }

    @Test
    fun `every result carries complete document and source locator`() = runTest {
        val repo = SearchRepository(FakeSearchIndexDao())
        repo.index(document("pdf", title = "定位", type = DocumentType.PDF), indexedAt = 1L)
        // v10-style null anchor must be synthesized from kind/location before indexing.
        repo.index(highlight("pdf", id = 2, page = 9, excerpt = "定位").copy(anchor = null), indexedAt = 2L)
        val markdownAnchor = DocumentAnchor.MarkdownBlock(blockIndex = 6, charStart = 3, charEnd = 12)
        repo.index(
            note("md", id = 3, block = 6, content = "定位").copy(
                anchor = AnchorCodec.encode(markdownAnchor),
                anchorStart = 3,
                anchorEnd = 12,
            ),
            indexedAt = 3L,
        )

        val results = repo.search("定位")
        assertTrue(results.all { it.documentId.isNotBlank() && it.locationKind.isNotBlank() })
        assertEquals(DocumentAnchor.PdfPage(page = 0), results.first { it.contentType == SearchContentType.DOCUMENT }.anchor)
        assertEquals(DocumentAnchor.PdfPage(page = 9), results.first { it.contentType == SearchContentType.HIGHLIGHT }.anchor)
        assertEquals(markdownAnchor, results.first { it.contentType == SearchContentType.NOTE }.anchor)
        assertFalse(results.any { it.location < 0 })
    }

    private fun document(
        id: String,
        title: String?,
        type: DocumentType = DocumentType.MARKDOWN,
    ) = DocumentEntity(
        id = id,
        displayName = "$id.${if (type == DocumentType.PDF) "pdf" else "md"}",
        uri = "content://$id",
        mimeType = null,
        fileSize = null,
        lastOpenedAt = 0L,
        createdAt = 0L,
        lastScrollIndex = 99,
        lastScrollOffset = 0,
        readingProgress = 0f,
        title = title,
        headingCount = 0,
        documentType = type.name,
    )

    private fun highlight(
        documentId: String,
        id: Long,
        page: Int,
        excerpt: String,
    ): HighlightEntity {
        val anchor = DocumentAnchor.PdfPage(
            page = page,
            rects = listOf(NormalizedRect(0.1f, 0.2f, 0.3f, 0.1f)),
        )
        return HighlightEntity(
            id = id,
            documentId = documentId,
            locationKind = "PAGE_INDEX",
            location = page,
            anchor = AnchorCodec.encode(anchor),
            excerpt = excerpt,
            createdAt = 1L,
        )
    }

    private fun note(
        documentId: String,
        id: Long,
        block: Int,
        content: String,
    ): NoteEntity {
        val anchor = DocumentAnchor.MarkdownBlock(blockIndex = block)
        return NoteEntity(
            id = id,
            documentId = documentId,
            locationKind = "SCROLL_POSITION",
            location = block,
            anchor = AnchorCodec.encode(anchor),
            content = content,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
