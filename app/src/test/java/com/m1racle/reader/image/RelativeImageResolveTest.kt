package com.m1racle.reader.image

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regression: LocalImageLoader → MarkdownAssetResolver bridge for file:// documents.
 */
class RelativeImageResolveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun resolveRelative_fromFileDocumentUri() {
        val dir = tmp.newFolder("pkg")
        val md = File(dir, "index.md").also { it.writeText("# x") }
        File(dir, "images").mkdirs()
        File(dir, "images/fig.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50))
        val uri = "file://${md.absolutePath}"
        val f = LocalImageLoader.resolveRelativeImage(uri, "images/fig.png")
        assertNotNull(f)
        assertTrue(f!!.isFile)
    }

    @Test
    fun resolveRelative_allowsParentWithinPackageRoot() {
        val dir = tmp.newFolder("pkg3")
        val ch = File(dir, "ch").also { it.mkdirs() }
        val md = File(ch, "index.md").also { it.writeText("# x") }
        File(dir, "images").mkdirs()
        File(dir, "images/fig.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50))
        // findPackageRoot without manifest → entry parent = ch; ../images escapes ch → null
        // with package-manifest at dir → root = dir → ../images/fig.png ok
        File(dir, "package-manifest.json").writeText("{}")
        val uri = "file://${md.absolutePath}"
        val f = LocalImageLoader.resolveRelativeImage(uri, "../images/fig.png")
        assertNotNull(f)
        assertTrue(f!!.isFile)
    }

    @Test
    fun resolveRelative_rejectsTraversal() {
        val dir = tmp.newFolder("pkg2")
        val md = File(dir, "index.md").also { it.writeText("# x") }
        val uri = "file://${md.absolutePath}"
        assertNull(LocalImageLoader.resolveRelativeImage(uri, "../etc/passwd"))
        assertNull(LocalImageLoader.resolveRelativeImage(uri, "a/../../x.png"))
    }

    @Test
    fun resolveRelative_requiresFileScheme() {
        assertNull(LocalImageLoader.resolveRelativeImage("content://media/1", "a.png"))
        assertNull(LocalImageLoader.filePathFromUri("content://x"))
    }
}
