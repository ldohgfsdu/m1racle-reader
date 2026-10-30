package com.m1racle.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.BookmarkEntity
import com.m1racle.reader.data.local.BookmarkLocationKind
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.ui.reader.ReaderBookmarkController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkToggleAtomicInstrumentedTest {
    @Test
    fun concurrentControllersKeepOneAtomicToggleOrderInRealRoom() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.documentDao().upsert(DocumentEntity(
                id = "atomic-bookmarks", displayName = "Synthetic bookmark test.pdf",
                uri = "file:///synthetic-bookmarks.pdf", mimeType = "application/pdf", fileSize = 1,
                createdAt = 0, lastOpenedAt = 0, lastScrollIndex = 0, lastScrollOffset = 0,
                readingProgress = 0f, title = null, headingCount = 0, documentType = "PDF", pageCount = 10,
            ))
            val repository = ReadingRepository(
                db.bookmarkDao(), db.favoriteDocumentDao(), db.highlightDao(), db.noteDao(),
                SearchRepository(db.searchIndexDao()), RoomRepositoryTransactionRunner(db),
            )
            val start = CompletableDeferred<Unit>()
            val controllers = List(4) {
                ReaderBookmarkController(repository, "atomic-bookmarks", BookmarkLocationKind.PAGE_INDEX)
            }
            val results = withTimeout(15_000) {
                val calls = List(100) { index -> async(Dispatchers.Default) {
                    start.await()
                    controllers[index % controllers.size].toggle(7)
                } }
                start.complete(Unit)
                calls.awaitAll()
            }
            assertEquals(50, results.count { it is ReaderBookmarkController.ToggleResult.Added })
            assertEquals(50, results.count { it is ReaderBookmarkController.ToggleResult.Removed })
            assertTrue(db.bookmarkDao().listForDocument("atomic-bookmarks").isEmpty())

            // Existing duplicate rows are removed together, with other locations preserved.
            for (page in listOf(7, 7, 8)) db.bookmarkDao().insert(BookmarkEntity(
                documentId = "atomic-bookmarks", locationKind = BookmarkLocationKind.PAGE_INDEX.name,
                location = page, createdAt = 0,
            ))
            assertEquals(ReaderBookmarkController.ToggleResult.Removed, controllers.first().toggle(7))
            assertEquals(listOf(8), db.bookmarkDao().listForDocument("atomic-bookmarks").map { it.location })
        } finally {
            db.close()
        }
    }
}
