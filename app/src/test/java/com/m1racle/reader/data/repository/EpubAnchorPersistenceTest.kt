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

    @Test fun openingSavedLocatorConvertsItsOwnRoomRecordOnceAndPreservesOtherBooks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = java.io.File("src/test/resources/fixtures/epub/sample-epub2.epub")
            .takeIf { it.isFile } ?: java.io.File("app/src/test/resources/fixtures/epub/sample-epub2.epub")
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            .joinToString("") { "%02x".format(it) }
        val parsed = com.m1racle.reader.epub.EpubPackageImporter.openCached(source,
            java.io.File(context.cacheDir, "rollback-source-fixture"), digest)
        val chapter = parsed.readingOrder.first { it.blocks.isNotEmpty() }
        val expected = requireNotNull(com.m1racle.reader.epub.EpubReadingAnchors.anchorAt(parsed,
            com.m1racle.reader.epub.EpubLocation(chapter.spineIndex, 0, 0)))
        val stored = "epub-locator:" + org.json.JSONObject().put("href", chapter.href)
            .put("text", org.json.JSONObject().put("highlight", expected.quote))
            .put("locations", org.json.JSONObject().put("position", 99).put("totalProgression", .4)).toString()
        val old = requireNotNull(db.documentDao().getById("epub"))
        db.documentDao().insertDocument(old.copy(id = "rollback", uri = source.toURI().toString(),
            readingAnchor = stored, contentSha256 = digest, lastScrollIndex = 98))
        repository.openEpubBook("rollback")
        val converted = requireNotNull(db.documentDao().getById("rollback"))
        assertEquals(expected, AnchorCodec.decode(converted.readingAnchor))
        assertEquals(0, converted.lastScrollIndex)
        assertEquals(.4f, converted.readingProgress, 0f)
        assertEquals(old.readingAnchor, db.documentDao().getById("epub")!!.readingAnchor)
        repository.openEpubBook("rollback")
        assertEquals(converted.readingAnchor, db.documentDao().getById("rollback")!!.readingAnchor)
    }

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
