package com.m1racle.reader.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.repository.DocumentRepository
import com.m1racle.reader.data.repository.SearchRepository
import com.m1racle.reader.identity.ContentIdentityResolver
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeOperationsTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()
    private val release = CountDownLatch(1)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        release.countDown()
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(
        dao: DocumentDao = db.documentDao(),
        resolver: ContentIdentityResolver = ContentIdentityResolver(context, dao),
    ): HomeViewModel = HomeViewModel(DocumentRepository(
        context, db, dao, db.pdfPrintPageAnchorDao(), db.textbookOutlineNodeDao(),
        SearchRepository(db.searchIndexDao()), contentIdentityResolver = resolver,
    )).also { store.put("home", it) }

    @Test fun `import is busy immediately rejects duplicate and queues error until collected`() = runBlocking {
        val entered = CountDownLatch(1)
        val calls = AtomicInteger()
        val resolver = ContentIdentityResolver(db.documentDao(), context.cacheDir.resolve("home-import")) {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            throw IOException("provider read failed")
        }
        val vm = viewModel(resolver = resolver)
        val file = context.cacheDir.resolve("home-source.md").apply { writeText("# Test") }
        val uri = Uri.fromFile(file)
        vm.onImportDocument(uri)
        assertTrue(vm.isImporting.value)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        vm.onImportDocument(uri)
        assertEquals(1, calls.get())
        release.countDown()
        withTimeout(5000) { vm.isImporting.first { !it } }
        // No event collector was attached while the operation completed.
        assertEquals(HomeEvent.ReadFailed, withTimeout(5000) { vm.events.first() })
        vm.onImportDocument(uri)
        assertEquals(HomeEvent.ReadFailed, withTimeout(5000) { vm.events.first() })
        withTimeout(5000) { vm.isImporting.first { !it } }
        assertEquals(2, calls.get())
    }

    @Test fun `failed removal restores optimistic row and emits failure`() = runBlocking {
        val document = DocumentEntity(
            id = "retained", displayName = "book.pdf", uri = "content://test/book.pdf",
            mimeType = "application/pdf", fileSize = 1L, lastOpenedAt = 1L, createdAt = 1L,
            lastScrollIndex = 0, lastScrollOffset = 0, readingProgress = 0f,
            title = null, headingCount = 0, documentType = "PDF",
        )
        val source = MutableStateFlow(listOf(document))
        val entered = CountDownLatch(1)
        val calls = AtomicInteger()
        val dao = object : DocumentDao by db.documentDao() {
            override fun observeRecentDocuments() = source
            override suspend fun getById(id: String): DocumentEntity? {
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                throw IOException("storage unavailable")
            }
        }
        val vm = viewModel(dao)
        withTimeout(5000) { vm.uiState.first { it.ready } }
        vm.deleteDocument(document.id)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        withTimeout(5000) { vm.uiState.first { it.documents.isEmpty() } }
        vm.deleteDocument(document.id)
        assertEquals(1, calls.get())
        release.countDown()
        assertEquals(HomeEvent.DeleteFailed, withTimeout(5000) { vm.events.first() })
        val restored = withTimeout(5000) { vm.uiState.first { it.documents.isNotEmpty() } }
        assertEquals(document, restored.continueReading)
        assertEquals(listOf(document), restored.documents)
    }
}
