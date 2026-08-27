package com.m1racle.reader.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Host policy for package extract trees: after document delete, `packages/{id}`
 * must be wipeable. Mirrors [DocumentRepository.deleteById] filesystem cleanup
 * without Room/Android.
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

        // Same path construction as DocumentRepository.deleteById
        val target = File(filesDir, "packages/$id")
        if (target.exists()) {
            target.deleteRecursively()
        }
        assertFalse(target.exists())
        assertFalse(File(packageDir, "index.md").exists())
    }

    @Test
    fun missingPackageDir_isNoop() {
        val filesDir = tmp.newFolder("files2")
        val target = File(filesDir, "packages/never-created")
        if (target.exists()) target.deleteRecursively()
        assertFalse(target.exists())
    }
}
