package com.m1racle.reader.data.local

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * H2.1 阅读模型权威收口（源码钉，防第二事实源复活）：
 *
 * 1. 唯一阅读位置事实源 = documents 阅读字段族（DocumentEntity KDoc 权威标注，
 *    DocumentRepository 单点写入，Home 直接按 lastOpenedAt 排序消费）；
 * 2. reading_states 投影路径已删除 —— 主源码除 v8→v9 迁移 SQL 外不得出现；
 * 3. Bookmark 定位文档中性：locationKind/location 取代 PDF 分页绑定的 page。
 */
class ReadingModelAuthorityTest {

    @Test
    fun `documents reading field family is the declared single authority`() {
        val entity = norm(appSource("data/local/DocumentEntity.kt").readText())
        assertTrue(entity.contains("唯一阅读位置事实源"))
        assertTrue(entity.contains("禁止任何第二阅读状态存储或投影表"))
        // 精度字段仍在权威处（滚动偏移与视口会话只有这里有）。
        assertTrue(entity.contains("lastScrollOffset"))
        assertTrue(entity.contains("pdfViewportSessionVersion"))
    }

    @Test
    fun `reading_states projection path is fully removed from production sources`() {
        val entity = norm(appSource("data/local/ReadingLibraryEntities.kt").readText())
        assertFalse("entity layer must not declare reading_states", entity.contains("reading_states"))
        assertFalse(entity.contains("ReadingStateEntity"))

        val daos = norm(appSource("data/local/ReadingLibraryDaos.kt").readText())
        assertFalse(daos.contains("ReadingStateDao"))
        assertFalse(daos.contains("reading_states"))

        val repo = norm(appSource("data/repository/ReadingRepository.kt").readText())
        assertFalse(repo.contains("ReadingState"))
        assertFalse(repo.contains("recentReadings"))
        assertFalse(repo.contains("updateReadingProgress"))

        // 唯一允许的出现位置：v8→v9 迁移 SQL 常量（DROP + 冻结的历史 7→8 建表）。
        val db = norm(appSource("data/local/AppDatabase.kt").readText())
        assertTrue(db.contains("MIGRATION_8_9_DROP_READING_STATES_SQL"))
        assertTrue(db.contains("DROP TABLE IF EXISTS reading_states"))
        assertEquals(
            "reading_states 只允许出现在冻结迁移 SQL 常量内",
            listOf(
                "MIGRATION_7_8_CREATE_READING_STATES_SQL",
                "MIGRATION_7_8_INDEX_READING_STATES_DOC_SQL",
                "MIGRATION_7_8_INDEX_READING_STATES_TIME_SQL",
                "MIGRATION_8_9_DROP_READING_STATES_SQL",
            ),
            Regex("internal (?:const )?val (MIGRATION_[A-Z0-9_]*READING_STATES[A-Z_]*)")
                .findAll(db)
                .map { it.groupValues[1] }
                .sorted()
                .toList(),
        )
        assertEquals(17, Regex("version = ([0-9]+),").find(db)!!.groupValues[1].toInt())
    }

    @Test
    fun `bookmark location is document neutral - no pdf page binding`() {
        val n = norm(appSource("data/local/ReadingLibraryEntities.kt").readText())
        assertTrue(n.contains("enum class BookmarkLocationKind"))
        assertTrue(n.contains("PAGE_INDEX"))
        assertTrue(n.contains("SCROLL_POSITION"))
        assertTrue(n.contains("val locationKind: String"))
        assertTrue(n.contains("val location: Int"))
        // 旧 PDF 绑定列名不得复活；kind 无默认值（写入方显式声明坐标系）。
        assertFalse(Regex("val page: Int").containsMatchIn(n))
        assertFalse(Regex("val locationKind: String = ").containsMatchIn(n))
    }

    @Test
    fun `application wiring exposes no reading state dao`() {
        val app = norm(appSource("M1racleReaderApplication.kt").readText())
        assertFalse(app.contains("readingStateDao"))
        assertTrue(app.contains("bookmarkDao = database.bookmarkDao()"))
    }

    private fun appSource(relative: String): File =
        ProductionSource.file("com/m1racle/reader/$relative")

    private fun norm(s: String): String = s.replace(Regex("\\s+"), " ")
}
