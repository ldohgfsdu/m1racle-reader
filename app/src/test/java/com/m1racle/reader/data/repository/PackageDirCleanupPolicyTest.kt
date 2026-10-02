package com.m1racle.reader.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the production cleanup used by [DocumentRepository.deleteById]
 * for both Markdown and EPUB extract trees, without Room/Android.
 */
class PackageDirCleanupPolicyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun deletePackageDir_removesTreeBestEffort() {
        val filesDir = tmp.newFolder("files")
        val id = "doc-abc"
        val packageDir = File(filesDir, "packages/$id")
        packageDir.mkdirs()
        File(packageDir, "index.md").writeText("# package")
        File(packageDir, "images").mkdirs()
        File(packageDir, "images/a.png").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(packageDir.isDirectory)
        assertTrue(File(packageDir, "index.md").isFile)

        val target = File(filesDir, "packages/$id")
        deleteDocumentExtracts(filesDir, id)
        assertFalse(target.exists())
        assertFalse(File(packageDir, "index.md").exists())
    }

    @Test
    fun missingPackageDir_isNoop() {
        val filesDir = tmp.newFolder("files2")
        val target = File(filesDir, "packages/never-created")
        deleteDocumentExtracts(filesDir, "never-created")
        assertFalse(target.exists())
    }

    @Test
    fun deletedEpubResourcesAreRemovedWithoutTouchingOtherBooksOrSource() {
        val filesDir = tmp.newFolder("epub-files")
        val extract = File(filesDir, "epubs/book").apply { mkdirs() }
        File(extract, "chapter.xhtml").writeText("chapter")
        val other = File(filesDir, "epubs/other").apply { mkdirs() }
        val source = tmp.newFile("user.epub")
        deleteDocumentExtracts(filesDir, "book")
        assertFalse(extract.exists())
        assertTrue(other.exists())
        assertTrue(source.exists())
    }
}
