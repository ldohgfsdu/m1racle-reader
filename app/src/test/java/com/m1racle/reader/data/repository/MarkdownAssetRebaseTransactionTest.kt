package com.m1racle.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.SQLiteMode
import com.m1racle.reader.core.model.ContentSha256
import com.m1racle.reader.core.model.DocumentAnchor
import com.m1racle.reader.data.anchor.BookmarkTarget
import com.m1racle.reader.data.anchor.MarkdownBlockAnchorResolver
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.HighlightEntity
import com.m1racle.reader.data.local.NoteEntity
import com.m1racle.reader.data.local.SearchIndexDao
import com.m1racle.reader.data.local.SearchIndexEntity
import com.m1racle.reader.data.local.SearchContentType
import com.m1racle.reader.markdown.MarkdownBlock
import com.m1racle.reader.markdown.MarkdownParser
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room transaction proof for Markdown save-time asset and search relocation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MarkdownAssetRebaseTransactionTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var source: File
    private lateinit var original: String
    private lateinit var edited: String
    private lateinit var document: DocumentEntity
    private lateinit var target: com.m1racle.reader.data.anchor.AnchorProjection
    private var highlightId = 0L
    private var noteId = 0L
    private var bookmarkId = 0L

    @Before
    fun setup(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        original = "# Heading\n\nFirst paragraph.\n\nTarget paragraph.\n\nTail paragraph.\n"
        edited = "# Heading\n\nInserted paragraph.\n\nFirst paragraph.\n\nTarget paragraph.\n\nTail paragraph.\n"
        source = File(context.cacheDir, "rebase-${System.nanoTime()}.md").apply {
            writeText(original)
        }
        document = DocumentEntity(
            id = "rebase-document",
            displayName = "rebase.md",
            uri = source.toURI().toString(),
            mimeType = "text/markdown",
            fileSize = source.length(),
            lastOpenedAt = 1,
            createdAt = 1,
            lastScrollIndex = 0,
            lastScrollOffset = 0,
            readingProgress = 0f,
            title = "Rebase",
            headingCount = 1,
            documentType = DocumentType.MARKDOWN.name,
            contentSha256 = ContentSha256.of(source),
        )
        db.documentDao().insertDocument(document)
        val targetBlock = MarkdownParser.parse(original).blocks.indexOfFirst {
            it is MarkdownBlock.Paragraph &&
                it.text == "Target paragraph."
        }
        target = MarkdownBlockAnchorResolver.project(
            DocumentAnchor.MarkdownBlock(targetBlock, charStart = 0, charEnd = 6),
        )
        highlightId = db.highlightDao().insert(
            HighlightEntity(
                documentId = document.id,
                locationKind = target.locationKind,
                location = target.location,
                anchor = target.encoded,
                excerpt = "target highlight",
                createdAt = 1,
            ),
        )
        noteId = db.noteDao().insert(
            NoteEntity(
                documentId = document.id,
                locationKind = target.locationKind,
                location = target.location,
                anchor = target.encoded,
                content = "target note",
                createdAt = 2,
                updatedAt = 2,
            ),
        )
        bookmarkId = db.bookmarkDao().insert(
            BookmarkEntity(
                documentId = document.id,
                locationKind = BookmarkLocationKind.SCROLL_POSITION.name,
                location = target.location,
                offset = 37,
                anchor = BookmarkTarget(
                    DocumentAnchor.MarkdownBlock(targetBlock),
                    scrollOffset = 37,
                ).encode(),
                createdAt = 3,
            ),
        )
        val search = SearchRepository(db.searchIndexDao())
        search.index(db.highlightDao().getById(document.id, highlightId)!!)
        search.index(db.noteDao().getById(document.id, noteId)!!)
    }

    @After
    fun teardown() {
        db.close()
        source.delete()
    }

    @Test
    fun saveRelocatesHighlightNoteBookmarkAndSearchIndex() = runBlocking {
        val saved = repository(db.searchIndexDao()).saveDocumentEdits(
            document.id,
            document.contentSha256!!,
            editedFile(),
        )
        val expected = target.location + 1

        assertEquals(expected, db.highlightDao().getById(saved.id, highlightId)!!.location)
        assertEquals(expected, db.noteDao().getById(saved.id, noteId)!!.location)
        val bookmark = db.bookmarkDao().listForDocument(saved.id).single { it.id == bookmarkId }
        assertEquals(expected, bookmark.location)
        assertEquals(expected, (BookmarkTarget.decode(bookmark.anchor)?.anchor as DocumentAnchor.MarkdownBlock).blockIndex)
        assertEquals(37, BookmarkTarget.decode(bookmark.anchor)?.scrollOffset)
        assertEquals(
            expected,
            SearchRepository(db.searchIndexDao()).search("target highlight", saved.id).single().location,
        )
        assertEquals(
            expected,
            SearchRepository(db.searchIndexDao()).search("target note", saved.id).single().location,
        )
    }

    @Test
    fun searchIndexFailureRollsBackDocumentAndAllAssets() = runBlocking {
        val realIndex = db.searchIndexDao()
        val failingDao = object : SearchIndexDao by realIndex {
            override suspend fun upsert(entry: SearchIndexEntity) {
                if (entry.contentType == SearchContentType.DOCUMENT.name) {
                    // Fail after every asset and annotation index has really moved.
                    val moved = target.location + 1
                    assertEquals(moved, db.highlightDao().getById(document.id, highlightId)!!.location)
                    assertEquals(moved, db.noteDao().getById(document.id, noteId)!!.location)
                    assertEquals(moved, db.bookmarkDao().listForDocument(document.id).single().location)
                    assertEquals(moved, SearchRepository(realIndex).search("target note", document.id).single().location)
                    throw IOException("Injected final document index failure")
                }
                realIndex.upsert(entry)
            }
        }
        try {
            repository(failingDao).saveDocumentEdits(
                document.id,
                document.contentSha256!!,
                editedFile(),
            )
            fail("Expected search index failure")
        } catch (_: IOException) {
        }

        assertEquals(document.uri, db.documentDao().getById(document.id)!!.uri)
        assertEquals(target.location, db.highlightDao().getById(document.id, highlightId)!!.location)
        assertEquals(target.location, db.noteDao().getById(document.id, noteId)!!.location)
        assertEquals(
            target.location,
            db.bookmarkDao().listForDocument(document.id).single { it.id == bookmarkId }.location,
        )
        assertEquals(
            target.location,
            SearchRepository(db.searchIndexDao()).search("target highlight", document.id).single().location,
        )
        assertEquals(
            target.location,
            SearchRepository(db.searchIndexDao()).search("target note", document.id).single().location,
        )
        assertEquals(target.encoded, db.highlightDao().getById(document.id, highlightId)!!.anchor)
    }

    private fun editedFile(): File =
        File(context.cacheDir, "rebase-edited-${System.nanoTime()}.md").apply { writeText(edited) }

    private fun repository(searchIndexDao: SearchIndexDao): DocumentRepository =
        DocumentRepository(
            context = context,
            db = db,
            documentDao = db.documentDao(),
            pdfPrintPageAnchorDao = db.pdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = db.textbookOutlineNodeDao(),
            searchRepository = SearchRepository(searchIndexDao),
        )
}
