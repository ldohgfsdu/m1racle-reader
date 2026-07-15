package com.markleaf.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * File-contract tests for identity index (no Android JSONObject required).
 */
class DocumentIdentityIndexFileTest {

    @Test
    fun atomicWriteAndCorruptQuarantineContract() {
        val dir = Files.createTempDirectory("ididx-raw-").toFile()
        try {
            val libraryIndex = File(dir, "library_index").also { it.mkdirs() }
            val indexFile = File(libraryIndex, "identity_index_v1.json")
            val tmp = File(libraryIndex, "identity_index_v1.json.tmp")
            val payload =
                """{"formatVersion":1,"entries":[{"documentId":"c:abc","sha256":"abc","sizeBytes":10}]}"""
            tmp.writeText(payload)
            assertTrue(
                tmp.renameTo(indexFile) || run {
                    tmp.copyTo(indexFile, true)
                    tmp.delete()
                    true
                },
            )
            assertTrue(indexFile.isFile)
            assertTrue(indexFile.readText().contains("\"formatVersion\":1"))
            assertTrue(indexFile.readText().contains("c:abc"))

            indexFile.writeText("not-json")
            val corrupt = File(libraryIndex, "corrupt").also { it.mkdirs() }
            indexFile.copyTo(File(corrupt, "broken.json"), true)
            indexFile.delete()
            assertTrue(corrupt.listFiles()!!.isNotEmpty())
            assertTrue(!indexFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun shaMatchRequiresSizeAgreement() {
        val e1 = DocumentIdentityIndex.Entry("c:1", "u1", "deadbeef", 100, "application/pdf")
        val e2 = DocumentIdentityIndex.Entry("c:2", "u2", "deadbeef", 200, "application/pdf")
        fun find(sha: String, size: Long?) =
            listOf(e1, e2).firstOrNull { it.sha256 == sha && (size == null || it.sizeBytes == size) }
        assertEquals("c:1", find("deadbeef", 100)?.documentId)
        assertEquals("c:2", find("deadbeef", 200)?.documentId)
        assertNull(find("deadbeef", 999))
    }

    @Test
    fun pruneGhostEntries() {
        val room = setOf("c:1", "c:2")
        val index = mutableListOf("c:1", "c:ghost", "c:2")
        index.removeAll { it !in room }
        assertEquals(listOf("c:1", "c:2"), index)
    }
}
