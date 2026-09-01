package com.m1racle.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the production ownership boundaries introduced by R37.3. */
class R37ContentIdentityAuthorityContractTest {

    @Test
    fun `resolver is sole production caller of content hash lookup`() {
        val callers = kotlinSources()
            .filter { it.readText().contains("getByContentSha256(") }
            .map { it.name }
            .sorted()

        assertEquals(listOf("ContentIdentityResolver.kt", "DocumentDao.kt"), callers)
    }

    @Test
    fun `all import entries resolve then use the single commit boundary`() {
        val home = source("data/repository/DocumentRepository.kt")
        val external = source("share/ExternalDocumentImporter.kt")
        val intentAdapter = source("share/ExternalIntentUri.kt")

        assertTrue(home.contains("contentIdentityResolver.resolve("))
        assertTrue(home.contains("commitResolvedImport("))
        assertTrue(external.contains("contentIdentityResolver.resolve("))
        assertTrue(external.contains("repository.commitResolvedImport("))
        assertTrue(intentAdapter.contains("Intent.ACTION_VIEW -> intent.data"))
        assertTrue(intentAdapter.contains("Intent.ACTION_SEND -> extractSend(intent)"))
        assertFalse(external.contains("DocumentIdentityIndex"))
        assertFalse(external.contains("MessageDigest"))
        assertFalse(external.contains("documentDao"))
    }

    @Test
    fun `import commit never uses replace delete or content-derived ids`() {
        val repository = source("data/repository/DocumentRepository.kt")
        val commitStart = repository.indexOf("suspend fun commitResolvedImport(")
        val nextPublicBoundary = repository.indexOf("suspend fun deleteById(", commitStart)
        val identityCommitRegion = repository.substring(commitStart, nextPublicBoundary)

        assertTrue(commitStart >= 0)
        assertTrue(identityCommitRegion.contains("UUID.randomUUID().toString()"))
        assertTrue(identityCommitRegion.contains("documentDao.insertDocument(entity)"))
        assertTrue(identityCommitRegion.contains("documentAliasDao.insertAlias("))
        assertFalse(identityCommitRegion.contains("documentDao.upsert("))
        assertFalse(identityCommitRegion.contains("documentDao.delete"))
        assertFalse(identityCommitRegion.contains("DocumentIdentityIndex"))
        assertFalse(identityCommitRegion.contains("\"c:\""))
        assertEquals(1, occurrences(repository, "UUID.randomUUID().toString()", commitStart, nextPublicBoundary))
    }

    private fun kotlinSources(): List<File> =
        mainSourceRoot().walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "kt" }
            .toList()

    private fun source(relative: String): String = File(mainSourceRoot(), relative).readText()

    private fun mainSourceRoot(): File = listOf(
        File("app/src/main/java/com/m1racle/reader"),
        File("src/main/java/com/m1racle/reader"),
    ).first(File::isDirectory)

    private fun occurrences(text: String, needle: String, start: Int, end: Int): Int {
        var count = 0
        var cursor = start
        while (true) {
            val found = text.indexOf(needle, cursor)
            if (found < 0 || found >= end) return count
            count++
            cursor = found + needle.length
        }
    }
}
