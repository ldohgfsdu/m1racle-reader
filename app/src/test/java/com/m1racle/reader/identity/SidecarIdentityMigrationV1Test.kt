package com.m1racle.reader.identity

import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
class SidecarIdentityMigrationV1Test {
    private val root: File = Files.createTempDirectory("r55-sidecar-").toFile()
    private val files = SidecarIdentityMigrationFiles(root)
    private val store = FakeStore()
    private val roomTransaction = FakeRoomTransaction(store)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `missing sidecar completes without transaction or file mutation`() = runTest {
        var transactions = 0
        val migration = migrationWith(
            SidecarAliasMigrationTransaction { _, _ ->
                transactions += 1
                error("must not run")
            },
        )

        assertEquals(SidecarMigrationOutcome.NoInput, migration.ensureCompleted())
        assertEquals(SidecarMigrationOutcome.NoInput, migration.ensureCompleted())
        assertEquals(0, transactions)
        assertFalse(File(root, "library_index").exists())
    }

    @Test
    fun `valid empty sidecar retires after zero-write transaction`() = runTest {
        writeSidecar("""{"formatVersion":1,"entries":[]}""")

        val outcome = migrationWith(roomTransaction).ensureCompleted()

        assertTrue(outcome is SidecarMigrationOutcome.Migrated)
        outcome as SidecarMigrationOutcome.Migrated
        assertEquals(0, outcome.stats?.insertedAliases)
        assertFalse(files.liveFile.exists())
        assertTrue(outcome.artifact.isFile)
    }

    @Test
    fun `corrupt json is quarantined without database transaction`() = runTest {
        writeSidecar("{not-json")
        var transactions = 0
        val migration = migrationWith(
            SidecarAliasMigrationTransaction { _, _ ->
                transactions += 1
                error("must not run")
            },
        )

        val outcome = migration.ensureCompleted()

        assertTrue(outcome is SidecarMigrationOutcome.Quarantined)
        assertEquals(0, transactions)
        assertFalse(files.liveFile.exists())
        assertTrue((outcome as SidecarMigrationOutcome.Quarantined).artifact.isFile)
    }

    @Test
    fun `unknown version is quarantined without interpreting entries`() = runTest {
        writeSidecar(
            """{"formatVersion":2,"entries":[{"documentId":"$PATH_A_ID","canonicalUriIdentity":"$CANONICAL","sha256":"wrong"}]}""",
        )
        var transactions = 0
        val migration = migrationWith(
            SidecarAliasMigrationTransaction { _, _ ->
                transactions += 1
                error("must not run")
            },
        )

        val outcome = migration.ensureCompleted()

        assertTrue(outcome is SidecarMigrationOutcome.Quarantined)
        assertTrue((outcome as SidecarMigrationOutcome.Quarantined).reason.contains("unknown_version_2"))
        assertEquals(0, transactions)
        assertNull(store.aliases[DocumentAliasKeys.uriV1(CANONICAL)])
    }

    @Test
    fun `Path A imports canonical bridge but never trusts sidecar sha`() = runTest {
        val before = document(PATH_A_ID, contentSha256 = null)
        store.documents[before.id] = before
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "not-a-content-hash"))

        val outcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        val alias = store.aliases[DocumentAliasKeys.uriV1(CANONICAL)]
        assertEquals(PATH_A_ID, alias?.documentId)
        assertEquals(before.createdAt, alias?.createdAt)
        assertEquals(before.lastOpenedAt, alias?.lastSeenAt)
        assertEquals(before, store.documents[PATH_A_ID])
        assertNull(store.documents[PATH_A_ID]?.contentSha256)
        assertEquals(1, outcome.stats?.insertedAliases)
    }

    @Test
    fun `Path A U1 K U2 bridge is independent of raw uri hash`() = runTest {
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        val rawU1 = "content://provider/tree/a/document/42"
        val rawU2 = "content://provider/document/42"
        assertFalse(rawU1 == rawU2)
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = PATH_A_ID))

        migrationWith(roomTransaction).ensureCompleted()

        val alias = store.aliases[DocumentAliasKeys.uriV1(CANONICAL)]
        assertEquals(PATH_A_ID, alias?.documentId)
        assertNull(store.documents[PATH_A_ID]?.contentSha256)
    }

    @Test
    fun `Path B requires matching id suffix and does not rewrite content hash`() = runTest {
        store.documents[PATH_B_ID] = document(PATH_B_ID, contentSha256 = PATH_B_SHA)
        writeSidecar(entryJson(PATH_B_ID, CANONICAL, sha256 = PATH_B_SHA.uppercase()))

        val outcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        assertEquals(
            PATH_B_ID,
            store.aliases[DocumentAliasKeys.uriV1(CANONICAL)]?.documentId,
        )
        assertEquals(PATH_B_SHA, store.documents[PATH_B_ID]?.contentSha256)
        assertEquals(1, outcome.stats?.insertedAliases)
    }

    @Test
    fun `Path B sha mismatch is invalid and inserts nothing`() = runTest {
        store.documents[PATH_B_ID] = document(PATH_B_ID, contentSha256 = PATH_B_SHA)
        writeSidecar(entryJson(PATH_B_ID, CANONICAL, sha256 = "c".repeat(64)))

        val outcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        assertNull(store.aliases[DocumentAliasKeys.uriV1(CANONICAL)])
        assertEquals(1, outcome.stats?.invalidEntries)
        assertEquals(PATH_B_SHA, store.documents[PATH_B_ID]?.contentSha256)
    }

    @Test
    fun `orphan sidecar entry is skipped without recreating document`() = runTest {
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "ignored"))

        val outcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        assertEquals(1, outcome.stats?.orphanDocuments)
        assertNull(store.documents[PATH_A_ID])
        assertNull(store.aliases[DocumentAliasKeys.uriV1(CANONICAL)])
    }

    @Test
    fun `alias conflict preserves existing alias and both documents`() = runTest {
        val existingId = "d".repeat(64)
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        store.documents[existingId] = document(existingId, contentSha256 = null)
        val aliasKey = DocumentAliasKeys.uriV1(CANONICAL)
        store.aliases[aliasKey] =
            DocumentAliasEntity(aliasKey, existingId, DocumentAliasKeys.URI_V1_KIND, 1, 2)
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "ignored"))

        val outcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        assertEquals(existingId, store.aliases[aliasKey]?.documentId)
        assertTrue(store.documents[PATH_A_ID] != null)
        assertTrue(store.documents[existingId] != null)
        assertEquals(1, outcome.stats?.conflicts)
        assertEquals(0, outcome.stats?.insertedAliases)
    }

    @Test
    fun `repeated run is idempotent and retired marker completes new executor`() = runTest {
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "ignored"))
        val first = migrationWith(roomTransaction)

        val firstOutcome = first.ensureCompleted() as SidecarMigrationOutcome.Migrated
        val cachedOutcome = first.ensureCompleted() as SidecarMigrationOutcome.Migrated
        val recoveredOutcome = migrationWith(roomTransaction).ensureCompleted() as SidecarMigrationOutcome.Migrated

        assertEquals(firstOutcome.artifact, cachedOutcome.artifact)
        assertEquals(firstOutcome.artifact, recoveredOutcome.artifact)
        assertNull(recoveredOutcome.stats)
        assertEquals(1, store.aliases.values.count { it.documentId == PATH_A_ID })
    }

    @Test
    fun `database failure rolls back and leaves live sidecar for retry`() = runTest {
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "ignored"))
        var first = true
        val failingOnce = SidecarAliasMigrationTransaction { entries, invalid ->
            if (first) {
                first = false
                throw IllegalStateException("injected-before-transaction")
            }
            roomTransaction.migrate(entries, invalid)
        }
        val migration = migrationWith(failingOnce)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { migration.ensureCompleted() }
        }
        assertTrue(files.liveFile.isFile)
        assertNull(store.aliases[DocumentAliasKeys.uriV1(CANONICAL)])

        val recovered = migration.ensureCompleted() as SidecarMigrationOutcome.Migrated
        assertEquals(1, recovered.stats?.insertedAliases)
        assertFalse(files.liveFile.exists())
    }

    @Test
    fun `mid-transaction alias failure rolls back earlier inserts and retries`() = runTest {
        val secondId = "d".repeat(64)
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        store.documents[secondId] = document(secondId, contentSha256 = null)
        writeSidecar(
            """{"formatVersion":1,"entries":[
                {"documentId":"$PATH_A_ID","canonicalUriIdentity":"$CANONICAL","sha256":"ignored"},
                {"documentId":"$secondId","canonicalUriIdentity":"doc:provider/43","sha256":"ignored"}
            ]}""".trimIndent(),
        )
        store.failInsertAt = 2
        val migration = migrationWith(roomTransaction)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { migration.ensureCompleted() }
        }
        assertTrue(files.liveFile.isFile)
        assertTrue(store.aliases.isEmpty())

        store.failInsertAt = null
        val recovered = migration.ensureCompleted() as SidecarMigrationOutcome.Migrated
        assertEquals(2, recovered.stats?.insertedAliases)
        assertEquals(2, store.aliases.size)
        assertFalse(files.liveFile.exists())
    }

    @Test
    fun `commit before rename crash retries as same-document no-op`() = runTest {
        store.documents[PATH_A_ID] = document(PATH_A_ID, contentSha256 = null)
        writeSidecar(entryJson(PATH_A_ID, CANONICAL, sha256 = "ignored"))
        var first = true
        val crashAfterCommitOnce = SidecarAliasMigrationTransaction { entries, invalid ->
            val committed = roomTransaction.migrate(entries, invalid)
            if (first) {
                first = false
                throw IllegalStateException("injected-after-commit-before-rename")
            }
            committed
        }
        val migration = migrationWith(crashAfterCommitOnce)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { migration.ensureCompleted() }
        }
        assertTrue(files.liveFile.isFile)
        assertEquals(
            PATH_A_ID,
            store.aliases[DocumentAliasKeys.uriV1(CANONICAL)]?.documentId,
        )

        val recovered = migration.ensureCompleted() as SidecarMigrationOutcome.Migrated
        assertEquals(0, recovered.stats?.insertedAliases)
        assertEquals(1, recovered.stats?.sameDocumentAliases)
        assertFalse(files.liveFile.exists())
        assertTrue(recovered.artifact.isFile)
    }

    private fun migrationWith(transaction: SidecarAliasMigrationTransaction) =
        SidecarIdentityMigrationV1(
            reader = SidecarIdentityV1Reader(),
            files = files,
            transaction = transaction,
        )

    private fun writeSidecar(json: String) {
        files.liveFile.parentFile?.mkdirs()
        files.liveFile.writeText(json)
    }

    private fun entryJson(documentId: String, canonical: String, sha256: String): String =
        """{"formatVersion":1,"entries":[{"documentId":"$documentId","canonicalUriIdentity":"$canonical","sha256":"$sha256","sizeBytes":10,"mimeType":"application/pdf"}]}"""

    private fun document(id: String, contentSha256: String?): DocumentEntity =
        DocumentEntity(
            id = id,
            displayName = "fixture.pdf",
            uri = "file:///fixture.pdf",
            mimeType = "application/pdf",
            fileSize = 10,
            lastOpenedAt = 20,
            createdAt = 10,
            lastScrollIndex = 7,
            lastScrollOffset = 3,
            readingProgress = 0.5f,
            title = "Fixture",
            headingCount = 0,
            documentType = DocumentType.PDF.name,
            pageCount = 10,
            contentSha256 = contentSha256,
        )

    private class FakeStore : SidecarAliasMigrationStore {
        val documents = linkedMapOf<String, DocumentEntity>()
        val aliases = linkedMapOf<String, DocumentAliasEntity>()
        var failInsertAt: Int? = null
        private var insertAttempts: Int = 0

        override suspend fun getDocument(id: String): DocumentEntity? = documents[id]
        override suspend fun getAlias(key: String): DocumentAliasEntity? = aliases[key]
        override suspend fun insertAlias(alias: DocumentAliasEntity) {
            insertAttempts += 1
            check(insertAttempts != failInsertAt) { "injected alias insert failure" }
            check(alias.aliasKey !in aliases) { "ABORT alias conflict" }
            aliases[alias.aliasKey] = alias
        }
    }

    private class FakeRoomTransaction(
        private val store: FakeStore,
    ) : SidecarAliasMigrationTransaction {
        override suspend fun migrate(
            entries: List<SidecarIdentityEntryV1>,
            readerInvalidEntryCount: Int,
        ): SidecarAliasMigrationStats {
            val before = LinkedHashMap(store.aliases)
            return try {
                migrateSidecarAliases(entries, readerInvalidEntryCount, store)
            } catch (error: Throwable) {
                store.aliases.clear()
                store.aliases.putAll(before)
                throw error
            }
        }
    }

    companion object {
        private val PATH_A_ID = "a".repeat(64)
        private val PATH_B_SHA = "b".repeat(64)
        private val PATH_B_ID = "c:$PATH_B_SHA"
        private const val CANONICAL = "doc:provider/42"
    }
}
