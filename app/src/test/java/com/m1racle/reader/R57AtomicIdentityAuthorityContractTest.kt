package com.m1racle.reader

import com.m1racle.reader.testing.ProductionKotlinRoots
import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R57 executable contract: one Room identity authority, no live sidecar runtime. */
class R57AtomicIdentityAuthorityContractTest {

    @Test
    fun `historical sidecar runtime is absent and v1 file is migration-only`() {
        assertFalse(ProductionSource.resolve("com/m1racle/reader/share/DocumentIdentityIndex.kt").exists())
        assertEquals(
            emptyList<String>(),
            sourcesContaining("DocumentIdentityIndex"),
        )
        assertEquals(
            listOf("identity/SidecarIdentityMigrationV1.kt"),
            sourcesContaining("identity_index_v1.json"),
        )
    }

    @Test
    fun `application owns one migration gate and injects it into repository`() {
        val application = source("M1racleReaderApplication.kt")
        assertEquals(1, application.countText("SidecarIdentityMigrationV1("))
        assertTrue(application.contains("sidecarIdentityMigration.ensureCompleted()"))
        assertTrue(application.contains("sidecarIdentityMigration = sidecarIdentityMigration"))
        assertTrue(application.contains("RoomSidecarAliasMigrationTransaction("))
    }

    @Test
    fun `Home VIEW and SEND resolve only after the shared gate`() {
        val repository = source("data/repository/DocumentRepository.kt")
        val external = source("share/ExternalDocumentImporter.kt")
        val intent = source("share/ExternalIntentUri.kt")

        assertTrue(repository.contains("ensureIdentityRuntimeReady()"))
        assertTrue(repository.contains("contentIdentityResolver.resolve("))
        assertTrue(external.contains("repository.ensureIdentityRuntimeReady()"))
        assertTrue(external.contains("contentIdentityResolver.resolve("))
        assertTrue(intent.contains("Intent.ACTION_VIEW -> intent.data"))
        assertTrue(intent.contains("Intent.ACTION_SEND -> extractSend(intent)"))
        assertEquals(
            listOf(
                "data/repository/DocumentRepository.kt",
                "share/ExternalDocumentImporter.kt",
            ),
            sourcesContaining("contentIdentityResolver.resolve(").sorted(),
        )
    }

    @Test
    fun `identity commit has no replace delete or derived document id`() {
        val repository = source("data/repository/DocumentRepository.kt")
        val start = repository.indexOf("suspend fun commitResolvedImport(")
        val end = repository.indexOf("suspend fun deleteById(", start)
        val region = repository.substring(start, end)

        assertTrue(region.contains("UUID.randomUUID().toString()"))
        assertTrue(region.contains("documentDao.insertDocument(entity)"))
        assertTrue(region.contains("DocumentAliasKeys.uriV1("))
        assertFalse(region.contains("documentDao.upsert("))
        assertFalse(region.contains("documentDao.delete"))
        assertFalse(region.contains("stableUriId"))
        assertFalse(region.contains("\"c:"))
        assertFalse(repository.contains("DocumentIdentityIndex"))
        assertFalse(repository.contains("MessageDigest"))
    }

    @Test
    fun `existing document navigation remains direct by Room id`() {
        val navigation = source("ui/navigation/NavGraph.kt")
        assertTrue(navigation.contains("repository.getDocumentById(result.documentId)"))
        assertTrue(navigation.contains("repository.getDocumentById(bookmark.documentId)"))
        assertFalse(navigation.contains("ContentIdentityResolver"))
        assertFalse(navigation.contains("documentAlias"))
        assertFalse(navigation.contains("SidecarIdentity"))
    }

    private fun sourcesContaining(needle: String): List<String> =
        ProductionKotlinRoots.files()
            .filter { it.readText().contains(needle) }
            .map { file ->
                ProductionKotlinRoots.relativePath(file).removePrefix("com/m1racle/reader/")
            }
            .sorted()
            .toList()

    private fun source(relative: String): String =
        ProductionSource.text("com/m1racle/reader/$relative")


    private fun String.countText(needle: String): Int = windowed(needle.length).count { it == needle }
}
