package com.m1racle.reader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.*
import com.m1racle.reader.pdf.EmbeddedOutlineNodeDraft
import com.m1racle.reader.pdf.OutlineImportMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class OutlineImportTransactionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository
    private var writes = 0
    private var failAt = Int.MAX_VALUE
    private var cancel = false
    private val drafts = listOf(
        EmbeddedOutlineNodeDraft("One", 0, 2, 0, 0, -1, TextbookOutlineNodeType.CHAPTER, "one"),
        EmbeddedOutlineNodeDraft("Two", 3, 5, 0, 1, -1, TextbookOutlineNodeType.CHAPTER, "two"),
    )

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.documentDao().insertDocument(DocumentEntity("doc", "book", "file:///book.pdf", "application/pdf",
            10, 1, 1, 0, 0, 0f, null, 0, DocumentType.PDF.name))
        val real = db.textbookOutlineNodeDao()
        val guarded = object : TextbookOutlineNodeDao by real {
            override suspend fun upsert(node: TextbookOutlineNodeEntity) {
                checkpoint()
                real.upsert(node)
            }
            override suspend fun deleteById(id: String) {
                checkpoint()
                real.deleteById(id)
            }
        }
        repository = DocumentRepository(context, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            guarded, SearchRepository(db.searchIndexDao()))
        real.upsert(node("old"))
    }

    private fun checkpoint() {
        if (++writes == failAt) {
            if (cancel) throw CancellationException("cancel directory write")
            throw IOException("directory write failed")
        }
    }
    private fun node(id: String, parent: String? = null) = TextbookOutlineNodeEntity(
        id, "doc", parent, TextbookOutlineNodeType.CHAPTER.name, id, 0, 5, 0, 1, 1)

    @After fun close() { db.close() }

    private fun assertImportRollback(mode: OutlineImportMode, cancelled: Boolean = false) = runBlocking {
        val before = db.textbookOutlineNodeDao().listForDocument("doc")
        failAt = 2
        cancel = cancelled
        try {
            repository.importEmbeddedOutline("doc", drafts, mode)
            fail("expected write failure")
        } catch (error: Exception) {
            if (cancelled) assertTrue(error is CancellationException) else assertTrue(error is IOException)
        }
        assertEquals(before, db.textbookOutlineNodeDao().listForDocument("doc"))
    }
    @Test fun replaceRollbackPreservesOriginalDirectory() = assertImportRollback(OutlineImportMode.REPLACE)
    @Test fun mergeRollbackDoesNotLeavePartialNewDirectory() = assertImportRollback(OutlineImportMode.MERGE)
    @Test fun cancelledReplacementPreservesOriginalDirectory() = assertImportRollback(OutlineImportMode.REPLACE, true)

    @Test fun subtreeDeletionRollsBackEveryNode() = runBlocking {
        val dao = db.textbookOutlineNodeDao()
        dao.upsert(node("child", "old"))
        dao.upsert(node("grandchild", "child"))
        val before = dao.listForDocument("doc")
        failAt = 2
        try { repository.deleteOutlineNode("doc", "old"); fail("expected delete failure") }
        catch (_: IOException) { }
        assertEquals(before, dao.listForDocument("doc"))
    }

    @Test fun successfulReplacementCommitsCompleteDirectory() = runBlocking {
        assertEquals(2, repository.importEmbeddedOutline("doc", drafts, OutlineImportMode.REPLACE))
        assertEquals(setOf("One", "Two"), db.textbookOutlineNodeDao().listForDocument("doc").map { it.title }.toSet())
    }

    @Test fun batchInsertionRollsBackAllNewNodes() = runBlocking {
        val before = db.textbookOutlineNodeDao().listForDocument("doc")
        failAt = 2
        try { repository.insertOutlineNodes(listOf(node("new-one"), node("new-two"))); fail("expected batch failure") }
        catch (_: IOException) { }
        assertEquals(before, db.textbookOutlineNodeDao().listForDocument("doc"))
    }
}
