package com.m1racle.reader.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPrivateDocumentCleanupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `managed library copy and empty content directory are deleted`() {
        val filesDir = tmp.newFolder("files")
        val contentDir = File(filesDir, "library/sha256").apply { mkdirs() }
        val copy = File(contentDir, "book.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertTrue(deleteAppPrivateLibraryCopy(filesDir, copy.toURI().toString()))

        assertFalse(copy.exists())
        assertFalse(contentDir.exists())
        assertTrue(File(filesDir, "library").isDirectory)
    }

    @Test
    fun `user-owned file outside library is never deleted`() {
        val filesDir = tmp.newFolder("files-external")
        val source = tmp.newFile("user-owned.md").apply { writeText("# Keep") }

        assertFalse(deleteAppPrivateLibraryCopy(filesDir, source.toURI().toString()))

        assertTrue(source.isFile)
    }

    @Test
    fun `content uri is never a deletion candidate`() {
        val filesDir = tmp.newFolder("files-content")

        assertFalse(
            deleteAppPrivateLibraryCopy(
                filesDir,
                "content://documents/document/primary%3ABook.pdf",
            ),
        )
    }
}
