package com.m1racle.reader.ui.home

import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.ui.hub.LibraryFilter
import com.m1racle.reader.ui.hub.LibraryFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H2.2 Phase 1 契约：继续阅读数据来源正确（唯一事实源 = DocumentEntity 阅读字段族）、
 * 无阅读记录优雅降级、状态推导与乐观删除协同；文库过滤纯逻辑。
 */
class HomeViewModelTest {

    private fun doc(
        id: String,
        lastOpenedAt: Long = 0L,
        progress: Float = 0f,
        isPdf: Boolean = false,
        pageCount: Int = 0,
        scrollIndex: Int = 0,
        documentTypeOverride: String? = null,
    ) = DocumentEntity(
        id = id,
        displayName = "$id",
        uri = "content://x/$id",
        mimeType = null,
        fileSize = 10L,
        lastOpenedAt = lastOpenedAt,
        createdAt = 0L,
        lastScrollIndex = scrollIndex,
        lastScrollOffset = 0,
        readingProgress = progress,
        title = null,
        headingCount = 0,
        documentType = documentTypeOverride ?: if (isPdf) "PDF" else "MARKDOWN",
        pageCount = pageCount,
    )

    // ── 继续阅读数据来源 ────────────────────────────────────────────────────

    @Test
    fun `continue reading picks most recent opened document`() {
        val docs = listOf(
            doc("c", lastOpenedAt = 300L),
            doc("a", lastOpenedAt = 100L),
            doc("b", lastOpenedAt = 200L),
        )
        assertEquals("c", HomeHubSelectors.continueReading(docs)?.id)
    }

    @Test
    fun `never-opened documents are not continue candidates`() {
        val docs = listOf(doc("fresh", lastOpenedAt = 0L))
        assertNull(HomeHubSelectors.continueReading(docs))
    }

    @Test
    fun `zero progress but opened still counts as a reading record`() {
        val docs = listOf(doc("opened", lastOpenedAt = 42L, progress = 0f))
        assertEquals("opened", HomeHubSelectors.continueReading(docs)?.id)
    }

    // ── 无阅读记录降级 + 乐观删除协同 ──────────────────────────────────────

    @Test
    fun `empty state degrades - no continue card`() {
        val state = homeUiState(source = emptyList(), pendingDeleteIds = emptySet())
        assertEquals(true, state.isEmpty)
        assertNull(state.continueReading)
    }

    @Test
    fun `all never-opened degrades to null hero with non-empty list`() {
        val state = homeUiState(source = listOf(doc("a"), doc("b")), pendingDeleteIds = emptySet())
        assertEquals(false, state.isEmpty)
        assertNull(state.continueReading)
        assertEquals(2, state.documents.size)
    }

    @Test
    fun `pending-deleted document cannot be the continue target`() {
        val state = homeUiState(
            source = listOf(doc("gone", lastOpenedAt = 500L), doc("kept", lastOpenedAt = 100L)),
            pendingDeleteIds = setOf("gone"),
        )
        assertEquals(listOf("kept"), state.documents.map { it.id })
        assertEquals("kept", state.continueReading?.id)
    }

    // ── 位置行 / 进度 ───────────────────────────────────────────────────────

    @Test
    fun `pdf position line uses page of total`() {
        val line = readingPositionLine(doc("p", isPdf = true, pageCount = 982, scrollIndex = 77))
        assertEquals("第 78 / 982 页", line)
    }

    @Test
    fun `markdown position line uses progress semantics`() {
        assertEquals("已读 37%", readingPositionLine(doc("m", progress = 0.37f)))
        assertEquals("未读", readingPositionLine(doc("m", progress = 0f)))
        assertEquals("已读完", readingPositionLine(doc("m", progress = 1f)))
    }

    @Test
    fun `progress percent clamps to 0-100`() {
        assertEquals(0, progressPercent(doc("x", progress = -1f)))
        assertEquals(42, progressPercent(doc("x", progress = 0.424f)))
        assertEquals(100, progressPercent(doc("x", progress = 9f)))
    }

    // ── 文库过滤（Phase 3 纯逻辑） ─────────────────────────────────────────

    @Test
    fun `library filter keeps recent order and splits by type`() {
        val docs = listOf(
            doc("md1", lastOpenedAt = 30L),
            doc("pdf1", lastOpenedAt = 20L, isPdf = true),
            doc("pkg", lastOpenedAt = 10L, documentTypeOverride = "MARKDOWN_PACKAGE"),
        )
        assertEquals(listOf("md1", "pdf1", "pkg"), LibraryFilters.apply(docs, LibraryFilter.ALL).map { it.id })
        assertEquals(listOf("pdf1"), LibraryFilters.apply(docs, LibraryFilter.PDF).map { it.id })
        assertEquals(listOf("md1", "pkg"), LibraryFilters.apply(docs, LibraryFilter.MARKDOWN).map { it.id })
    }

}
