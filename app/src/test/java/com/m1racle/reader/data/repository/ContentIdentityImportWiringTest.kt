package com.m1racle.reader.data.repository

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.data.local.PdfPrintPageAnchorDao
import com.m1racle.reader.data.local.PdfPrintPageAnchorEntity
import com.m1racle.reader.data.local.TextbookOutlineNodeDao
import com.m1racle.reader.data.local.TextbookOutlineNodeEntity
import com.m1racle.reader.identity.ContentIdentityResolver
import com.m1racle.reader.identity.DocumentAliasKeys
import com.m1racle.reader.identity.SidecarAliasMigrationStore
import com.m1racle.reader.identity.SidecarAliasMigrationTransaction
import com.m1racle.reader.identity.SidecarIdentityMigrationFiles
import com.m1racle.reader.identity.SidecarIdentityMigrationV1
import com.m1racle.reader.identity.SidecarIdentityV1Reader
import com.m1racle.reader.identity.migrateSidecarAliases
import com.m1racle.reader.share.CommitOutcome
import com.m1racle.reader.share.ExternalDocumentImporter
import com.m1racle.reader.share.ExternalIntentUri
import com.m1racle.reader.share.PrepareOutcome
import com.m1racle.reader.testing.ImmediateRepositoryTransactionRunner
import com.m1racle.reader.testing.TestDocumentAliasDao
import com.m1racle.reader.testing.TestSearchIndexDao
import com.m1racle.reader.testing.testSearchRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** R37.3 import-commit wiring acceptance tests for all three document entries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ContentIdentityImportWiringTest {

    private lateinit var fixture: Fixture

    @Before
    fun setUp() {
        fixture = Fixture(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun safImport_newPdf_usesResolverAndOpaqueCommitId() = runBlocking {
        val uri = fixture.source("content://r37.test/saf/new.pdf")

        val outcome = fixture.repository.importDocument(uri)

        val document = (outcome as ImportResult.Success).document
        fixture.assertNewPdf(document, uri)
    }

    @Test
    fun actionView_newPdf_usesSharedResolverAndCommit() = runBlocking {
        val uri = fixture.source("content://r37.test/view/new.pdf")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")

        val document = fixture.externalImport(requireNotNull(ExternalIntentUri.extract(intent)))

        fixture.assertNewPdf(document, uri)
    }

    @Test
    fun actionSend_newPdf_usesSharedResolverAndCommit() = runBlocking {
        val uri = fixture.source("content://r37.test/send/new.pdf")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)

        val document = fixture.externalImport(requireNotNull(ExternalIntentUri.extract(intent)))

        fixture.assertNewPdf(document, uri)
    }

    @Test
    fun sameBytesDifferentUris_produceOneDocumentAndTwoAliases() = runBlocking {
        val safUri = fixture.source("content://r37.test/saf/original.pdf")
        val sendUri = fixture.source("content://r37.test/send/renamed.pdf")

        val first = (fixture.repository.importDocument(safUri) as ImportResult.Success).document
        val second = fixture.externalImport(sendUri)

        assertEquals(first.id, second.id)
        assertEquals(1, fixture.documentDao.rows.size)
        assertEquals(2, fixture.aliasDao.allAliases().size)
        assertEquals(setOf(first.id), fixture.aliasDao.allAliases().map { it.documentId }.toSet())
        assertEquals(2, fixture.aliasDao.allAliases().map { it.aliasKey }.distinct().size)
        assertTrue(fixture.aliasDao.allAliases().all { it.aliasKey.startsWith("uri-v1:") })
        assertTrue(fixture.aliasDao.allAliases().none { it.aliasKey.contains("content://") })
        assertEquals(0, fixture.documentDao.replaceCalls)
    }

    @Test
    fun duplicateImport_preservesExistingIdBookmarkHighlightAndSearchIndex() = runBlocking {
        val viewUri = fixture.source("content://r37.test/view/bookmarked.pdf")
        val safUri = fixture.source("content://r37.test/saf/same-content.pdf")
        val original = fixture.externalImport(viewUri)
        fixture.documentDao.bookmarks += "bookmark-7" to original.id
        fixture.documentDao.highlights += "highlight-11" to original.id
        fixture.documentDao.notes += "note-13" to original.id
        fixture.documentDao.anchors += "anchor-17" to original.id
        val indexedBefore = fixture.searchDao.rows.toMap()

        val duplicate = (fixture.repository.importDocument(safUri) as ImportResult.Success).document

        assertEquals(original.id, duplicate.id)
        assertEquals(listOf("bookmark-7" to original.id), fixture.documentDao.bookmarks)
        assertEquals(listOf("highlight-11" to original.id), fixture.documentDao.highlights)
        assertEquals(listOf("note-13" to original.id), fixture.documentDao.notes)
        assertEquals(listOf("anchor-17" to original.id), fixture.documentDao.anchors)
        assertEquals(indexedBefore, fixture.searchDao.rows)
        assertEquals(1, fixture.documentDao.rows.size)
        assertEquals(0, fixture.documentDao.replaceCalls)
    }

    @Test
    fun homeSaf_sameBytesDifferentFilenameAndUri_reusesLegacyDocumentWithoutDuplicateRow() =
        runBlocking {
            val oldId = "b".repeat(64)
            val oldUri = fixture.source("content://r60.test/download/1.md")
            val renamedUri = fixture.source("content://r60.test/download/1-r59-u2.md")
            fixture.seedLegacyPathA(oldId, oldUri)

            val result = fixture.repository.importDocument(renamedUri)

            val reused = (result as ImportResult.Success).document
            assertEquals(oldId, reused.id)
            assertEquals(EXPECTED_SHA256, reused.contentSha256)
            assertEquals(setOf(oldId), fixture.documentDao.rows.keys)
            assertEquals(1, fixture.documentDao.rows.size)
            assertEquals(listOf("bookmark-legacy" to oldId), fixture.documentDao.bookmarks)
            assertEquals(listOf("highlight-legacy" to oldId), fixture.documentDao.highlights)
            assertEquals(listOf("note-legacy" to oldId), fixture.documentDao.notes)
            assertEquals(listOf("anchor-legacy" to oldId), fixture.documentDao.anchors)
            assertEquals(0, fixture.documentDao.replaceCalls)
            assertEquals(
                oldId,
                fixture.aliasDao.allAliases().single {
                    it.aliasKey == DocumentAliasKeys.uriV1("doc:r60.test/1-r59-u2.md")
                }.documentId,
            )
        }

    @Test
    fun sidecarPathABridge_survivesUriChangeAndReusesOriginalDocument() = runBlocking {
        val oldId = "a".repeat(64)
        val oldUri = fixture.source("content://r37.test/tree/old/document/42.pdf")
        fixture.seedLegacyPathA(oldId, oldUri)
        fixture.writeSidecar(
            """{"formatVersion":1,"entries":[{"documentId":"$oldId","canonicalUriIdentity":"doc:r37.test/42.pdf","sha256":"not-content-authority"}]}""",
        )
        val changedUri = fixture.source("content://r37.test/42.pdf")

        val result = fixture.repository.importDocument(changedUri)

        val document = (result as ImportResult.Success).document
        assertEquals(oldId, document.id)
        assertEquals(EXPECTED_SHA256, document.contentSha256)
        assertEquals(setOf(oldId), fixture.documentDao.rows.keys)
        assertEquals(listOf("bookmark-legacy" to oldId), fixture.documentDao.bookmarks)
        assertEquals(listOf("highlight-legacy" to oldId), fixture.documentDao.highlights)
        assertEquals(listOf("note-legacy" to oldId), fixture.documentDao.notes)
        assertEquals(listOf("anchor-legacy" to oldId), fixture.documentDao.anchors)
        assertEquals(
            oldId,
            fixture.aliasDao.allAliases().single {
                it.aliasKey == DocumentAliasKeys.uriV1("doc:r37.test/42.pdf")
            }.documentId,
        )
        assertFalse(fixture.migrationFiles.liveFile.exists())
        assertEquals(1, fixture.retiredArtifacts().size)
    }

    @Test
    fun inconsistentMigrationState_blocksHomeAndExternalBeforeSourceRead() = runBlocking {
        fixture.makeMigrationStateInconsistent()
        val uri = fixture.source("content://r37.test/blocked.pdf")

        assertEquals(ImportResult.ReadFailed, fixture.repository.importDocument(uri))
        assertTrue(fixture.prepareExternal(uri) is PrepareOutcome.Failed)
        assertEquals(0, fixture.sourceOpenCount)
        assertTrue(fixture.documentDao.rows.isEmpty())
        assertTrue(fixture.aliasDao.allAliases().isEmpty())
    }

    @Test
    fun savingEditedPdfKeepsStableIdentityAndReadingAssets() = runBlocking {
        val source=fixture.localPdf("original PDF bytes")
        fixture.seedLegacyPathA("editable",Uri.fromFile(source))
        val before=fixture.documentDao.getById("editable")!!
        val candidate=fixture.localPdf("edited PDF bytes")
        val saved=fixture.repository.savePdfEdits(before.id,com.m1racle.reader.pdf.edit.PdfSourceWriteStore.sha256(source),candidate)
        assertEquals(before.id,saved.id); assertEquals(before.lastScrollIndex,saved.lastScrollIndex)
        assertEquals(before.readingProgress,saved.readingProgress)
        assertEquals(1,fixture.documentDao.rows.size); assertEquals(0,fixture.documentDao.replaceCalls)
        assertEquals("original PDF bytes",source.readText())
        assertEquals("edited PDF bytes",File(Uri.parse(saved.uri).path!!).readText())
        assertTrue(fixture.documentDao.bookmarks.any { it.second == saved.id })
        assertTrue(fixture.documentDao.highlights.any { it.second == saved.id })
        assertTrue(fixture.documentDao.notes.any { it.second == saved.id })
    }

    @Test
    fun overwriteAfterLocalSaveUsesOriginalSourceHashAndUpdatesSameBook() = runBlocking {
        val source=fixture.localPdf("original PDF bytes")
        fixture.seedLegacyPathA("editable",Uri.fromFile(source))
        val first=fixture.localPdf("first revision")
        val local=fixture.repository.savePdfEdits("editable",com.m1racle.reader.pdf.edit.PdfSourceWriteStore.sha256(source),first)
        val next=fixture.localPdf("second revision")
        val saved=fixture.repository.savePdfEdits(local.id,local.contentSha256!!,next,Uri.fromFile(source))
        assertEquals("second revision",source.readText())
        assertEquals(local.id,saved.id); assertEquals(saved.contentSha256,saved.sourceSha256)
        assertEquals(Uri.fromFile(source).toString(),saved.sourceUri)
        assertEquals(1,fixture.documentDao.rows.size)
        assertEquals(null,fixture.repository.pdfSourceRecoveryBackup(saved.id))
    }

    @Test
    fun failedDocumentUpdateRestoresExternalSource() = runBlocking {
        val source=fixture.localPdf("original PDF bytes")
        fixture.seedLegacyPathA("editable",Uri.fromFile(source))
        val candidate=fixture.localPdf("replacement PDF bytes")
        fixture.documentDao.rejectUpdates=true
        var failed=false
        try { fixture.repository.savePdfEdits("editable",com.m1racle.reader.pdf.edit.PdfSourceWriteStore.sha256(source),candidate,Uri.fromFile(source)) }
        catch (_: IllegalStateException) { failed=true }
        assertTrue(failed); assertEquals("original PDF bytes",source.readText())
        assertEquals(Uri.fromFile(source).toString(),fixture.documentDao.getById("editable")!!.uri)
        assertEquals(null,fixture.repository.pdfSourceRecoveryBackup("editable"))
    }

    @Test
    fun changedLibraryRevisionRejectsStaleEditorSave() = runBlocking {
        val source=fixture.localPdf("original PDF bytes")
        fixture.seedLegacyPathA("editable",Uri.fromFile(source))
        val candidate=fixture.localPdf("new revision")
        var failed=false
        try { fixture.repository.savePdfEdits("editable","stale hash",candidate) }
        catch (_: IllegalStateException) { failed=true }
        assertTrue(failed); assertEquals("original PDF bytes",source.readText())
        assertEquals(1,fixture.documentDao.rows.size)
    }

    @Test
    fun equalBytesFromAnotherFileUpdateTheWriteBackTarget() = runBlocking {
        val first=fixture.source("content://r37.test/source-a.pdf")
        val second=fixture.source("content://r37.test/source-b.pdf")
        val a=(fixture.repository.importDocument(first) as ImportResult.Success).document
        val b=(fixture.repository.importDocument(second) as ImportResult.Success).document
        assertEquals(a.id,b.id)
        assertEquals(second.toString(),b.sourceUri)
        assertEquals(second.toString(),fixture.documentDao.getById(a.id)!!.sourceUri)
        assertEquals(b.contentSha256,b.sourceSha256)
        assertEquals(0,fixture.documentDao.replaceCalls)
    }

    @Test
    fun mutableMarkdownSourceCannotChangeImportedIdentity() = runBlocking {
        val a = "# Original\nOriginal text".toByteArray()
        val uri = fixture.source("content://r37.test/book.md", a)
        val first = (fixture.repository.importDocument(uri) as ImportResult.Success).document
        fixture.documentDao.bookmarks += "saved" to first.id
        assertEquals(a.toString(Charsets.UTF_8), File(Uri.parse(first.uri).path!!).readText())
        fixture.source(uri.toString(), "# Changed".toByteArray())
        val copy = fixture.source("content://r37.test/copy.md", a)
        val duplicate = (fixture.repository.importDocument(copy) as ImportResult.Success).document
        assertEquals(first.id, duplicate.id)
        assertEquals(first.uri, duplicate.uri)
        assertEquals(a.toString(Charsets.UTF_8), File(Uri.parse(duplicate.uri).path!!).readText())
        assertEquals(uri.toString(), duplicate.sourceUri)
        assertEquals(listOf("saved" to first.id), fixture.documentDao.bookmarks)
    }

    @Test
    fun oldHashedMarkdownIsRestoredFromVerifiedIncomingBytes() = runBlocking {
        val a = "Original".toByteArray()
        val old = fixture.source("content://r37.test/old.md", a)
        fixture.seedLegacyPathA("old", old)
        val hash = com.m1racle.reader.pdf.edit.PdfSourceWriteStore.sha256(a.inputStream())
        fixture.documentDao.assignContentSha256IfNull("old", hash, 1)
        fixture.source(old.toString(), "Changed".toByteArray())
        val incoming = fixture.source("content://r37.test/copy.md", a)
        val document = (fixture.repository.importDocument(incoming) as ImportResult.Success).document
        assertEquals("old", document.id)
        assertEquals("Original", File(Uri.parse(document.uri).path!!).readText())
        assertEquals(hash, document.contentSha256)
        assertEquals(listOf("bookmark-legacy" to "old"), fixture.documentDao.bookmarks)
    }

    @Test
    fun unreadableLegacyAliasCannotClaimUnverifiedBytes() = runBlocking {
        val oldId = "a".repeat(64)
        val old = Uri.parse("content://r37.test/tree/old/document/42.pdf")
        fixture.seedLegacyPathA(oldId, old)
        fixture.writeSidecar("""{"formatVersion":1,"entries":[{"documentId":"$oldId","canonicalUriIdentity":"doc:r37.test/42.pdf","sha256":"not-content-authority"}]}""")
        val incoming = fixture.source("content://r37.test/42.pdf")
        val document = (fixture.repository.importDocument(incoming) as ImportResult.Success).document
        assertNotEquals(oldId, document.id)
        assertEquals(null, fixture.documentDao.getById(oldId)!!.contentSha256)
        assertEquals(old.toString(), fixture.documentDao.getById(oldId)!!.uri)
        assertEquals(listOf("bookmark-legacy" to oldId), fixture.documentDao.bookmarks)
        assertEquals(2, fixture.documentDao.rows.size)
    }

    @Test
    fun legacyPayloadChangedAfterPrepareIsNotPromoted() = runBlocking {
        val old = fixture.source("content://r37.test/old.pdf")
        fixture.seedLegacyPathA("old", old)
        val incoming = fixture.source("content://r37.test/new.pdf")
        val importer = fixture.importer()
        val prepared = importer.prepare(incoming, "application/pdf") as PrepareOutcome.Success
        fixture.source(old.toString(), "different bytes".toByteArray())
        val result = importer.commitPrepared(prepared.prepared) as CommitOutcome.Success
        assertNotEquals("old", result.documentId)
        assertEquals(null, fixture.documentDao.getById("old")!!.contentSha256)
    }

    @Test
    fun markdownReaderRejectsBytesThatDoNotMatchTheirIdentity() = runBlocking {
        val source = fixture.source("content://r37.test/book.md", "# Original".toByteArray())
        val document = (fixture.repository.importDocument(source) as ImportResult.Success).document
        assertEquals("# Original", fixture.repository.readDocumentText(document.uri, document.contentSha256).getOrThrow())
        File(Uri.parse(document.uri).path!!).writeText("# Changed")
        assertEquals(DocumentReadException.ContentChanged,
            fixture.repository.readDocumentText(document.uri, document.contentSha256).exceptionOrNull())
    }

    private class Fixture(private val context: Context) {
        val documentDao = IdentityDocumentDao()
        val aliasDao = TestDocumentAliasDao()
        val searchDao = TestSearchIndexDao()
        private val sources = linkedMapOf<String, ByteArray>()
        private val staging = File(context.cacheDir, "r37-3-${System.nanoTime()}").apply { mkdirs() }
        private val migrationRoot = File(context.cacheDir, "r57-gate-${System.nanoTime()}").apply { mkdirs() }
        val migrationFiles = SidecarIdentityMigrationFiles(migrationRoot)
        var sourceOpenCount: Int = 0
            private set
        private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        private val resolver = ContentIdentityResolver(
            documentDao = documentDao,
            stagingDirectory = staging,
            openSource = { uri ->
                sourceOpenCount += 1
                sources[uri.toString()]?.let(::ByteArrayInputStream)
            },
        )
        private val sidecarMigration = SidecarIdentityMigrationV1(
            reader = SidecarIdentityV1Reader(),
            files = migrationFiles,
            transaction = SidecarAliasMigrationTransaction { entries, invalid ->
                migrateSidecarAliases(
                    entries = entries,
                    readerInvalidEntryCount = invalid,
                    store = object : SidecarAliasMigrationStore {
                        override suspend fun getDocument(id: String): DocumentEntity? =
                            documentDao.getById(id)

                        override suspend fun getAlias(key: String): DocumentAliasEntity? =
                            aliasDao.getByKey(key)

                        override suspend fun insertAlias(alias: DocumentAliasEntity) {
                            aliasDao.insertAlias(alias)
                        }
                    },
                )
            },
        )
        val repository = DocumentRepository(
            context = context,
            db = db,
            documentDao = documentDao,
            pdfPrintPageAnchorDao = UnusedPdfPrintPageAnchorDao(),
            textbookOutlineNodeDao = UnusedTextbookOutlineNodeDao(),
            searchRepository = testSearchRepository(searchDao),
            documentAliasDao = aliasDao,
            contentIdentityResolver = resolver,
            sidecarIdentityMigration = sidecarMigration,
            importTransactionRunner = ImmediateRepositoryTransactionRunner(),
        )
        private val externalImporter = ExternalDocumentImporter(
            context = context,
            repository = repository,
            contentIdentityResolver = resolver,
        )
        fun importer() = externalImporter

        fun localPdf(text: String): File = File(staging,"${UUID.randomUUID()}.pdf").apply { writeText(text) }

        fun source(value: String, bytes: ByteArray = PDF_BYTES): Uri = Uri.parse(value).also {
            sources[it.toString()] = bytes
        }

        suspend fun seedLegacyPathA(id: String, uri: Uri) {
            val isMarkdown = uri.lastPathSegment?.endsWith(".md", ignoreCase = true) == true
            documentDao.insertDocument(
                DocumentEntity(
                    id = id,
                    displayName = if (isMarkdown) "1.md" else "legacy.pdf",
                    uri = uri.toString(),
                    mimeType = if (isMarkdown) "text/markdown" else "application/pdf",
                    fileSize = PDF_BYTES.size.toLong(),
                    lastOpenedAt = 20,
                    createdAt = 10,
                    lastScrollIndex = 7,
                    lastScrollOffset = 3,
                    readingProgress = 0.5f,
                    title = "Legacy",
                    headingCount = 0,
                    documentType = if (isMarkdown) {
                        DocumentType.MARKDOWN.name
                    } else {
                        DocumentType.PDF.name
                    },
                    pageCount = 10,
                    contentSha256 = null,
                ),
            )
            documentDao.bookmarks += "bookmark-legacy" to id
            documentDao.highlights += "highlight-legacy" to id
            documentDao.notes += "note-legacy" to id
            documentDao.anchors += "anchor-legacy" to id
        }

        fun writeSidecar(json: String) {
            migrationFiles.liveFile.parentFile?.mkdirs()
            migrationFiles.liveFile.writeText(json)
        }

        fun makeMigrationStateInconsistent() {
            writeSidecar("""{"formatVersion":1,"entries":[]}""")
            val retired = File(migrationRoot, "library_index/retired").apply { mkdirs() }
            File(
                retired,
                "identity_index_v1.migrated-v13.${"0".repeat(64)}.json",
            ).writeText("already-retired")
        }

        fun retiredArtifacts(): List<File> =
            File(migrationRoot, "library_index/retired").listFiles()?.toList().orEmpty()

        suspend fun prepareExternal(uri: Uri): PrepareOutcome =
            externalImporter.prepare(uri, "application/pdf")

        suspend fun externalImport(uri: Uri): DocumentEntity {
            val prepared = externalImporter.prepare(uri, "application/pdf")
            assertTrue(prepared is PrepareOutcome.Success)
            val committed = externalImporter.commitPrepared((prepared as PrepareOutcome.Success).prepared)
            assertTrue(committed is CommitOutcome.Success)
            val id = (committed as CommitOutcome.Success).documentId
            return requireNotNull(documentDao.getById(id))
        }

        fun assertNewPdf(document: DocumentEntity, sourceUri: Uri) {
            assertEquals(sourceUri.toString(), document.sourceUri)
            assertEquals(EXPECTED_SHA256, document.sourceSha256)
            assertEquals(EXPECTED_SHA256, document.contentSha256)
            assertEquals(DocumentType.PDF, document.type)
            assertEquals(PDF_BYTES.size.toLong(), document.fileSize)
            assertEquals(1, documentDao.rows.size)
            assertEquals(1, aliasDao.allAliases().size)
            assertEquals(document.id, aliasDao.allAliases().single().documentId)
            assertEquals(0, documentDao.replaceCalls)
            UUID.fromString(document.id)
            assertFalse(document.id.startsWith("c:"))
            assertNotEquals(
                DocumentAliasKeys.legacyRawUriV1(sourceUri.toString()).substringAfter(':'),
                document.id,
            )
        }

        fun close() {
            db.close()
            staging.deleteRecursively()
            migrationRoot.deleteRecursively()
        }
    }

    private class IdentityDocumentDao : DocumentDao {
        val rows = linkedMapOf<String, DocumentEntity>()
        val bookmarks = mutableListOf<Pair<String, String>>()
        val highlights = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<Pair<String, String>>()
        val anchors = mutableListOf<Pair<String, String>>()
        var replaceCalls = 0
        private val observed = MutableStateFlow<List<DocumentEntity>>(emptyList())

        override fun observeRecentDocuments(): Flow<List<DocumentEntity>> = observed
        override suspend fun getById(id: String): DocumentEntity? = rows[id]
        override suspend fun getByContentSha256(contentSha256: String): DocumentEntity? =
            rows.values.singleOrNull { it.contentSha256 == contentSha256 }
        override suspend fun getAll(): List<DocumentEntity> = rows.values.toList()

        override suspend fun upsert(document: DocumentEntity) {
            replaceCalls++
            bookmarks.removeAll { it.second == document.id }
            highlights.removeAll { it.second == document.id }
            notes.removeAll { it.second == document.id }
            anchors.removeAll { it.second == document.id }
            rows[document.id] = document
            publish()
        }

        override suspend fun insertDocument(document: DocumentEntity): Long {
            if (document.id in rows || rows.values.any { it.contentSha256 == document.contentSha256 }) {
                throw SQLiteConstraintException("duplicate document identity")
            }
            rows[document.id] = document
            publish()
            return rows.size.toLong()
        }

        var rejectUpdates = false
        override suspend fun updateDocument(document: DocumentEntity) {
            check(!rejectUpdates) { "Injected database update failure" }
            if (document.id in rows) rows[document.id] = document
            publish()
        }

        override suspend fun assignContentSha256IfNull(
            id: String,
            contentSha256: String,
            lastOpenedAt: Long,
        ): Int {
            val current = rows[id]?.takeIf { it.contentSha256 == null } ?: return 0
            if (rows.values.any { it.id != id && it.contentSha256 == contentSha256 }) {
                throw SQLiteConstraintException("duplicate content hash")
            }
            rows[id] = current.copy(contentSha256 = contentSha256, lastOpenedAt = lastOpenedAt)
            publish()
            return 1
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
            publish()
        }

        override suspend fun updateLastOpenedAt(id: String, time: Long) {
            rows[id]?.let { rows[id] = it.copy(lastOpenedAt = time) }
            publish()
        }

        override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
            rows[id]?.let { rows[id] = it.copy(title = title, headingCount = headingCount) }
            publish()
        }

        override suspend fun updateReadingPosition(
            id: String,
            lastScrollIndex: Int,
            lastScrollOffset: Int,
            readingProgress: Float,
        ) {
            rows[id]?.let {
                rows[id] = it.copy(
                    lastScrollIndex = lastScrollIndex,
                    lastScrollOffset = lastScrollOffset,
                    readingProgress = readingProgress,
                )
            }
            publish()
        }

        override suspend fun updateEpubReadingPosition(
            id: String,
            lastScrollIndex: Int,
            lastScrollOffset: Int,
            readingProgress: Float,
            readingAnchor: String,
        ) = Unit
        override suspend fun updatePdfReadingPosition(
            id: String,
            pageIndex: Int,
            intraPageFractionEncoded: Int,
            pageCount: Int,
            readingProgress: Float,
        ) {
            rows[id]?.let {
                rows[id] = it.copy(
                    lastScrollIndex = pageIndex,
                    lastScrollOffset = intraPageFractionEncoded,
                    pageCount = pageCount,
                    readingProgress = readingProgress,
                )
            }
            publish()
        }

        override suspend fun updatePdfViewportSession(
            id: String,
            version: Int,
            readingMode: String,
            pageIndex: Int,
            pageAnchorXFraction: Float,
            pageAnchorYFraction: Float,
            viewportAnchorXFraction: Float,
            viewportAnchorYFraction: Float,
            scale: Float,
            generation: Long,
        ) = Unit

        override suspend fun deleteAll() {
            rows.clear()
            publish()
        }

        private fun publish() {
            observed.value = rows.values.toList()
        }
    }

    private class UnusedPdfPrintPageAnchorDao : PdfPrintPageAnchorDao {
        override suspend fun listForDocument(documentId: String): List<PdfPrintPageAnchorEntity> =
            error("unused")
        override suspend fun upsert(anchor: PdfPrintPageAnchorEntity) = error("unused")
        override suspend fun delete(documentId: String, pdfPageIndex: Int) = error("unused")
        override suspend fun deleteAllForDocument(documentId: String) = error("unused")
    }

    private class UnusedTextbookOutlineNodeDao : TextbookOutlineNodeDao {
        override suspend fun listForDocument(documentId: String): List<TextbookOutlineNodeEntity> =
            error("unused")
        override suspend fun getById(id: String): TextbookOutlineNodeEntity? = error("unused")
        override suspend fun upsert(node: TextbookOutlineNodeEntity) = error("unused")
        override suspend fun update(node: TextbookOutlineNodeEntity) = error("unused")
        override suspend fun deleteById(id: String) = error("unused")
        override suspend fun deleteAllForDocument(documentId: String) = error("unused")
        override suspend fun listChildren(
            documentId: String,
            parentId: String,
        ): List<TextbookOutlineNodeEntity> = error("unused")
        override suspend fun listRoots(documentId: String): List<TextbookOutlineNodeEntity> =
            error("unused")
    }

    private companion object {
        val PDF_BYTES = "%PDF-1.4\nR37.3 identity fixture\n%%EOF\n".toByteArray()
        const val EXPECTED_SHA256 =
            "36a090fdd2604a8f3d1ab95486adae0159461c03843dd49af800427e82a02bc2"
    }
}
