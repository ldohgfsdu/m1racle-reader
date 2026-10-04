package com.m1racle.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.epub.EpubRestoreResult
import com.m1racle.reader.epub.EpubRestoreSession
import com.m1racle.reader.epub.EpubRestoreTier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class EpubAnchorPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository
    private val original = DocumentAnchor.EpubText("chapter.xhtml", 12, 42,
        parserVersion = 1, quote = "original text")
    private val nearby = original.copy(blockIndex = 0, charStart = 0, quote = "nearby text")

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = DocumentRepository(context, db, db.documentDao(), db.pdfPrintPageAnchorDao(),
            db.textbookOutlineNodeDao(), SearchRepository(db.searchIndexDao()))
        db.documentDao().insertDocument(DocumentEntity("epub", "book", "file:///book.epub",
            "application/epub+zip", 10, 1, 1, 12, 42, 0.5f, null, 0,
            DocumentType.EPUB.name, readingAnchor = AnchorCodec.encode(original)))
        Unit
    }

    @After fun close() { db.close() }

    @Test fun automaticSavesPreserveStoredAnchorUntilUserNavigation() = runBlocking {
        for (tier in listOf(EpubRestoreTier.Quote, EpubRestoreTier.QuotePrefix,
            EpubRestoreTier.ChapterStart, EpubRestoreTier.Progress)) {
            repository.updateEpubReadingPosition("epub", original, 12, 42, 0.5f)
            val session = EpubRestoreSession(EpubRestoreResult(tier))
            repeat(3) { offset ->
                repository.updateEpubReadingPosition("epub", session.anchorForSave(nearby),
                    0, offset, 0.1f)
                val stored = requireNotNull(db.documentDao().getById("epub"))
                assertEquals("$tier must retain the original encoded anchor",
                    AnchorCodec.encode(original), stored.readingAnchor)
                assertEquals(offset, stored.lastScrollOffset)
                assertEquals(0.1f, stored.readingProgress, 0f)
            }
            session.onUserNavigation()
            repository.updateEpubReadingPosition("epub", session.anchorForSave(nearby), 1, 0, 0.2f)
            val navigated = requireNotNull(db.documentDao().getById("epub"))
            assertEquals(AnchorCodec.encode(nearby), navigated.readingAnchor)
            assertEquals(1, navigated.lastScrollIndex)
        }
    }
}
