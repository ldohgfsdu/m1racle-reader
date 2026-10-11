package com.m1racle.reader.identity

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.m1racle.reader.data.local.DocumentAliasDao
import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class LegacyContentHashMigrationV1Test {

    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = Files.createTempDirectory("m1racle-reader-legacy-hash-").toFile()
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun readableNullHashRows_arePromotedInPlace() = runBlocking {
        val bytes = "promote me".toByteArray()
        val legacyUri = Uri.parse("content://provider/legacy/old.pdf")
        val dao = LegacyHashDocumentDao(listOf(document("legacy", null, legacyUri)))
        val migration = migration(dao, sources = mapOf(legacyUri.toString() to bytes))

        val stats = migration.ensureCompleted()

        val promoted = requireNotNull(dao.getById("legacy"))
        assertEquals(1, stats.promoted)
        assertEquals(sha256(bytes), promoted.contentSha256)
        assertEquals(7, promoted.lastScrollIndex)
        assertEquals(20L, promoted.lastOpenedAt)
    }

    @Test
    fun unreadableSource_keepsTheRowFrozen() = runBlocking {
        val legacyUri = Uri.parse("content://provider/legacy/gone.pdf")
        val dao = LegacyHashDocumentDao(listOf(document("legacy", null, legacyUri)))
        val migration = migration(dao, sources = emptyMap())

        val stats = migration.ensureCompleted()

        assertEquals(1, stats.unreadable)
        assertEquals(0, stats.promoted)
        assertNull(dao.getById("legacy")!!.contentSha256)
    }

    @Test
    fun hashAlreadyOwnedByAnotherRow_leavesTheRowFrozenInsteadOfMerging() = runBlocking {
        val bytes = "shared bytes".toByteArray()
        val legacyUri = Uri.parse("content://provider/legacy/duplicate.pdf")
        val owner = document("owner", sha256(bytes), Uri.parse("content://provider/owner.pdf"))
        val dao = LegacyHashDocumentDao(
            listOf(owner, document("legacy", null, legacyUri)),
        )
        val migration = migration(dao, sources = mapOf(legacyUri.toString() to bytes))

        val stats = migration.ensureCompleted()

        assertEquals(1, stats.conflicts)
        assertNull(dao.getById("legacy")!!.contentSha256)
        assertEquals("owner", dao.getByContentSha256(sha256(bytes))!!.id)
    }

    @Test
    fun legacyAliases_areDeletedOnce() = runBlocking {
        val aliasDao = LegacyHashDocumentAliasDao(
            listOf(
                alias(DocumentAliasKeys.legacyRawUriV1("content://provider/old/42.pdf"), "legacy"),
                alias(DocumentAliasKeys.uriV1("doc:provider/42.pdf"), "legacy"),
            ),
        )
        val dao = LegacyHashDocumentDao(emptyList())
        val migration = migration(dao, aliasDao = aliasDao, sources = emptyMap())

        val stats = migration.ensureCompleted()

        assertEquals(1, stats.removedLegacyAliases)
        assertFalse(aliasDao.allAliases().any { it.kind == DocumentAliasKeys.LEGACY_RAW_URI_V1_KIND })
        assertTrue(aliasDao.allAliases().any { it.kind == DocumentAliasKeys.URI_V1_KIND })
    }

    @Test
    fun subsequentRuns_areNoOpsAndDoNotRehashSources() = runBlocking {
        val bytes = "promote once".toByteArray()
        val legacyUri = Uri.parse("content://provider/legacy/old.pdf")
        val dao = LegacyHashDocumentDao(listOf(document("legacy", null, legacyUri)))
        var sourceOpens = 0
        val migration = migration(
            dao,
            sources = mapOf(legacyUri.toString() to bytes),
            openSource = { uri ->
                sourceOpens += 1
                ByteArrayInputStream(bytes).takeIf { uri == legacyUri }
            },
        )

        val first = migration.ensureCompleted()
        val opensAfterFirstRun = sourceOpens
        val second = migration.ensureCompleted()

        assertEquals(1, first.promoted)
        assertEquals(sha256(bytes), dao.getById("legacy")!!.contentSha256)
        assertEquals(opensAfterFirstRun, sourceOpens)
        assertEquals(first, second)
        assertTrue(LegacyContentHashMigrationFiles(filesDir).hasMarker())
    }

    @Test
    fun failedRunLeavesNoMarker_soTheMigrationIsRetried() = runBlocking {
        val bytes = "retry me".toByteArray()
        val legacyUri = Uri.parse("content://provider/legacy/old.pdf")
        val dao = ThrowingOnceDocumentDao(
            LegacyHashDocumentDao(listOf(document("legacy", null, legacyUri))),
        )

        val firstOutcome = runCatching { migration(dao).ensureCompleted() }

        assertTrue(firstOutcome.isFailure)
        assertFalse(LegacyContentHashMigrationFiles(filesDir).hasMarker())
        assertNull(dao.getById("legacy")!!.contentSha256)

        val retry = migration(dao, sources = mapOf(legacyUri.toString() to bytes)).ensureCompleted()

        assertEquals(1, retry.promoted)
        assertEquals(sha256(bytes), dao.getById("legacy")!!.contentSha256)
    }

    private fun migration(
        dao: DocumentDao,
        sources: Map<String, ByteArray> = emptyMap(),
        aliasDao: DocumentAliasDao = LegacyHashDocumentAliasDao(),
        openSource: ((Uri) -> InputStream?)? = null,
    ): LegacyContentHashMigrationV1 = LegacyContentHashMigrationV1(
        documentDao = dao,
        aliasDao = aliasDao,
        files = LegacyContentHashMigrationFiles(filesDir),
        openSource = openSource ?: { uri -> sources[uri.toString()]?.let(::ByteArrayInputStream) },
    )

    private fun document(id: String, contentSha256: String?, uri: Uri): DocumentEntity =
        DocumentEntity(
            id = id,
            displayName = "$id.pdf",
            uri = uri.toString(),
            mimeType = "application/pdf",
            fileSize = 100L,
            lastOpenedAt = 20L,
            createdAt = 10L,
            lastScrollIndex = 7,
            lastScrollOffset = 3,
            readingProgress = 0.5f,
            title = "Legacy",
            headingCount = 0,
            contentSha256 = contentSha256,
        )

    private fun alias(aliasKey: String, documentId: String): DocumentAliasEntity =
        DocumentAliasEntity(
            aliasKey = aliasKey,
            documentId = documentId,
            kind = aliasKey.substringBefore(':'),
            createdAt = 10L,
            lastSeenAt = 20L,
        )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

private class LegacyHashDocumentDao(
    initial: List<DocumentEntity> = emptyList(),
) : DocumentDao {
    private val rows = initial.associateByTo(linkedMapOf()) { it.id }
    private val observed = MutableStateFlow(initial)

    override fun observeRecentDocuments(): Flow<List<DocumentEntity>> = observed
    override suspend fun getById(id: String): DocumentEntity? = rows[id]
    override suspend fun getByContentSha256(contentSha256: String): DocumentEntity? =
        rows.values.singleOrNull { it.contentSha256 == contentSha256 }
    override suspend fun getAll(): List<DocumentEntity> = rows.values.toList()
    override suspend fun upsert(document: DocumentEntity) {
        rows[document.id] = document
        observed.value = rows.values.toList()
    }
    override suspend fun insertDocument(document: DocumentEntity): Long {
        require(document.id !in rows) { "duplicate document id" }
        rows[document.id] = document
        observed.value = rows.values.toList()
        return rows.size.toLong()
    }
    override suspend fun updateDocument(document: DocumentEntity) {
        if (document.id in rows) rows[document.id] = document
        observed.value = rows.values.toList()
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
        observed.value = rows.values.toList()
        return 1
    }
    override suspend fun deleteById(id: String) {
        rows.remove(id)
        observed.value = rows.values.toList()
    }
    override suspend fun updateLastOpenedAt(id: String, time: Long) {
        rows[id]?.let { rows[id] = it.copy(lastOpenedAt = time) }
        observed.value = rows.values.toList()
    }
    override suspend fun updateParseMetadata(id: String, title: String?, headingCount: Int) {
        rows[id]?.let { rows[id] = it.copy(title = title, headingCount = headingCount) }
        observed.value = rows.values.toList()
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
        observed.value = rows.values.toList()
    }
    override suspend fun updateEpubReadingPosition(
        id: String,
        lastScrollIndex: Int,
        lastScrollOffset: Int,
        readingProgress: Float,
        readingAnchor: String?,
    ) = Unit
    override suspend fun updatePdfReadingPosition(
        id: String,
        pageIndex: Int,
        intraPageFractionEncoded: Int,
        pageCount: Int,
        readingProgress: Float,
    ) = Unit
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
        observed.value = emptyList()
    }
}

/** Fails the first read so the no-marker retry path can be exercised. */
private class ThrowingOnceDocumentDao(
    private val delegate: DocumentDao,
) : DocumentDao by delegate {
    private var throwNext = true

    override suspend fun getAll(): List<DocumentEntity> {
        if (throwNext) {
            throwNext = false
            throw IllegalStateException("simulated migration read failure")
        }
        return delegate.getAll()
    }
}

private class LegacyHashDocumentAliasDao(
    initial: List<DocumentAliasEntity> = emptyList(),
) : DocumentAliasDao {
    private val aliases = initial.associateByTo(linkedMapOf()) { it.aliasKey }

    override suspend fun getByKey(aliasKey: String): DocumentAliasEntity? = aliases[aliasKey]
    override suspend fun listForDocument(documentId: String): List<DocumentAliasEntity> =
        aliases.values.filter { it.documentId == documentId }
    override suspend fun insertAlias(alias: DocumentAliasEntity): Long {
        require(alias.aliasKey !in aliases) { "duplicate alias key" }
        aliases[alias.aliasKey] = alias
        return aliases.size.toLong()
    }
    override suspend fun updateAlias(alias: DocumentAliasEntity) {
        if (alias.aliasKey in aliases) aliases[alias.aliasKey] = alias
    }
    override suspend fun deleteByKind(kind: String): Int {
        val removed = aliases.values.count { it.kind == kind }
        aliases.entries.removeAll { it.value.kind == kind }
        return removed
    }
    fun allAliases(): List<DocumentAliasEntity> = aliases.values.toList()
}
