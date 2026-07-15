package com.markleaf.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure resolution matrix for R8 Markdown relative assets V1.
 * No android.net.Uri / BitmapFactory — filesystem + sealed results only.
 */
class MarkdownAssetResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun png(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    }

    private fun fileBase(root: File, entry: File): MarkdownDocumentBase.FileDirectory {
        return MarkdownDocumentBase.FileDirectory(
            documentUri = "file://${entry.absolutePath}",
            entryFile = entry.canonicalFile,
            rootDir = root.canonicalFile,
        )
    }

    private fun appBase(root: File, entry: File): MarkdownDocumentBase.AppOwnedDirectory {
        return MarkdownDocumentBase.AppOwnedDirectory(
            documentUri = "file://${entry.absolutePath}",
            entryFile = entry.canonicalFile,
            rootDir = root.canonicalFile,
        )
    }

    private fun resolvedPath(result: MarkdownAssetResult): String? {
        return when (result) {
            is MarkdownAssetResult.Resolved ->
                MarkdownAssetResolver.filePathFromDocumentUri(result.uri)
            else -> null
        }
    }

    // --- Pure path classification / absolute passthrough ---

    @Test
    fun absolute_http_https_data_file_content_passthrough() {
        val base = MarkdownDocumentBase.Unknown
        listOf(
            "http://example.com/a.png",
            "https://example.com/a.png",
            "data:image/png;base64,abc",
            "file:///tmp/a.png",
            "content://com.example/doc/1",
            "android.resource://com.markleaf.app/drawable/ic",
        ).forEach { ref ->
            val r = MarkdownAssetResolver.resolve(base, ref)
            assertTrue("expected Absolute for $ref, got $r", r is MarkdownAssetResult.Absolute)
            assertEquals(ref, (r as MarkdownAssetResult.Absolute).uri)
        }
    }

    @Test
    fun absolute_not_rewritten_under_single_content_document() {
        val base = MarkdownDocumentBase.SingleContentDocument(
            "content://com.android.providers.downloads/document/42",
        )
        val abs = "content://com.android.providers.downloads/document/99"
        val r = MarkdownAssetResolver.resolve(base, abs)
        assertTrue(r is MarkdownAssetResult.Absolute)
        assertEquals(abs, (r as MarkdownAssetResult.Absolute).uri)
    }

    @Test
    fun empty_and_invalid() {
        val base = fileBase(tmp.root, tmp.newFile("e.md"))
        assertTrue(
            MarkdownAssetResolver.resolve(base, "") is MarkdownAssetResult.Invalid,
        )
        assertTrue(
            MarkdownAssetResolver.resolve(base, "   ") is MarkdownAssetResult.Invalid,
        )
        val bad = MarkdownAssetResolver.resolve(base, "javascript:alert(1)")
        assertTrue(bad is MarkdownAssetResult.Invalid)
        assertEquals(
            MarkdownAssetFailure.UNSUPPORTED_SCHEME,
            (bad as MarkdownAssetResult.Invalid).reason,
        )
    }

    // --- Relative pure parse (FileDirectory sandbox) ---

    @Test
    fun relative_same_dir_and_dot_slash() {
        val root = tmp.newFolder("pkg_same")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        png(File(root, "image.png"))
        val base = fileBase(root, entry)

        val a = MarkdownAssetResolver.resolve(base, "image.png")
        val b = MarkdownAssetResolver.resolve(base, "./image.png")
        assertTrue(a is MarkdownAssetResult.Resolved)
        assertTrue(b is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "image.png").canonicalPath, resolvedPath(a))
        assertEquals(File(root, "image.png").canonicalPath, resolvedPath(b))
    }

    @Test
    fun relative_subdir() {
        val root = tmp.newFolder("pkg_sub")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        png(File(root, "images/image.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "images/image.png")
        assertTrue(r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "images/image.png").canonicalPath, resolvedPath(r))
    }

    @Test
    fun relative_parent_within_root() {
        val root = tmp.newFolder("pkg_parent")
        val chapter = File(root, "ch1").also { it.mkdirs() }
        val entry = File(chapter, "doc.md").also { it.writeText("# t") }
        png(File(root, "images/image.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "../images/image.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "images/image.png").canonicalPath, resolvedPath(r))
    }

    @Test
    fun relative_space_and_percent20() {
        val root = tmp.newFolder("pkg_space")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        png(File(root, "my image.png"))
        val base = fileBase(root, entry)
        val space = MarkdownAssetResolver.resolve(base, "my image.png")
        val enc = MarkdownAssetResolver.resolve(base, "my%20image.png")
        assertTrue(space is MarkdownAssetResult.Resolved)
        assertTrue(enc is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "my image.png").canonicalPath, resolvedPath(space))
        assertEquals(File(root, "my image.png").canonicalPath, resolvedPath(enc))
    }

    @Test
    fun relative_chinese_filename() {
        val root = tmp.newFolder("pkg_zh")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        png(File(root, "示意图.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "示意图.png")
        assertTrue(r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "示意图.png").canonicalPath, resolvedPath(r))
    }

    @Test
    fun relative_dot_dot_normalization_inside_root() {
        val root = tmp.newFolder("pkg_norm")
        val sub = File(root, "a/b").also { it.mkdirs() }
        val entry = File(sub, "doc.md").also { it.writeText("# t") }
        png(File(root, "a/x.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "../x.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "a/x.png").canonicalPath, resolvedPath(r))
    }

    @Test
    fun relative_outside_root_rejected() {
        val root = tmp.newFolder("pkg_out")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        val outside = File(tmp.root, "secret.png").also { png(it) }
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "../secret.png")
        assertTrue("$r", r is MarkdownAssetResult.Invalid)
        assertEquals(
            MarkdownAssetFailure.OUTSIDE_ROOT,
            (r as MarkdownAssetResult.Invalid).reason,
        )
        assertNull(resolvedPath(r))
        assertTrue(outside.isFile)
    }

    @Test
    fun relative_not_found() {
        val root = tmp.newFolder("pkg_miss")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "missing.png")
        assertTrue(r is MarkdownAssetResult.Inaccessible)
        assertEquals(
            MarkdownAssetFailure.NOT_FOUND,
            (r as MarkdownAssetResult.Inaccessible).reason,
        )
    }

    @Test
    fun relative_not_an_image() {
        val root = tmp.newFolder("pkg_txt")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        File(root, "notes.txt").writeText("x")
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "notes.txt")
        assertTrue(r is MarkdownAssetResult.Invalid)
        assertEquals(
            MarkdownAssetFailure.NOT_AN_IMAGE,
            (r as MarkdownAssetResult.Invalid).reason,
        )
    }

    @Test
    fun relative_directory_target_rejected() {
        val root = tmp.newFolder("pkg_dir")
        val entry = File(root, "doc.md").also { it.writeText("# t") }
        File(root, "images").mkdirs()
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "images")
        assertTrue(r is MarkdownAssetResult.Invalid)
        assertEquals(
            MarkdownAssetFailure.NOT_A_FILE,
            (r as MarkdownAssetResult.Invalid).reason,
        )
    }

    @Test
    fun normalize_segments_basic() {
        assertEquals(listOf("image.png"), MarkdownAssetResolver.normalizeRelativeSegments("image.png"))
        assertEquals(listOf("image.png"), MarkdownAssetResolver.normalizeRelativeSegments("./image.png"))
        assertEquals(
            listOf("images", "image.png"),
            MarkdownAssetResolver.normalizeRelativeSegments("images/image.png"),
        )
        assertEquals(
            listOf("..", "images", "image.png"),
            MarkdownAssetResolver.normalizeRelativeSegments("../images/image.png"),
        )
        assertEquals(
            listOf("my image.png"),
            MarkdownAssetResolver.normalizeRelativeSegments("my%20image.png"),
        )
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments(""))
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments("http://x/a.png"))
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments("/abs.png"))
    }

    // --- Capability tiers ---

    @Test
    fun capability_appOwned_success() {
        val root = tmp.newFolder("app_owned")
        val entry = File(root, "index.md").also { it.writeText("# t") }
        png(File(root, "fig.png"))
        val base = appBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "fig.png")
        assertTrue(r is MarkdownAssetResult.Resolved)
    }

    @Test
    fun capability_fileDirectory_success() {
        val root = tmp.newFolder("file_dir")
        val entry = File(root, "index.md").also { it.writeText("# t") }
        png(File(root, "fig.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "./fig.png")
        assertTrue(r is MarkdownAssetResult.Resolved)
    }

    @Test
    fun capability_safTreeDirectory_success() {
        val root = tmp.newFolder("saf_tree")
        png(File(root, "pic.png"))
        val base = MarkdownDocumentBase.SafTreeDirectory(
            documentUri =
                "content://com.android.externalstorage.documents/tree/primary%3ADocs/document/primary%3ADocs%2Fnote.md",
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADocs",
            rootDir = root.canonicalFile,
        )
        val r = MarkdownAssetResolver.resolve(base, "pic.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "pic.png").canonicalPath, resolvedPath(r))
    }

    @Test
    fun capability_singleContentDocument_relative_inaccessible() {
        val base = MarkdownDocumentBase.SingleContentDocument(
            "content://com.android.providers.downloads/document/7",
        )
        val r = MarkdownAssetResolver.resolve(base, "images/a.png")
        assertTrue(r is MarkdownAssetResult.Inaccessible)
        assertEquals(
            MarkdownAssetFailure.SINGLE_FILE_GRANT,
            (r as MarkdownAssetResult.Inaccessible).reason,
        )
        val msg = MarkdownAssetResolver.userMessage(r)
        assertTrue(msg.contains("无法访问相对图片"))
        assertTrue(msg.contains("当前仅授权了 Markdown 文件"))
    }

    @Test
    fun capability_unknown_controlled_failure() {
        val r = MarkdownAssetResolver.resolve(MarkdownDocumentBase.Unknown, "a.png")
        assertTrue(r is MarkdownAssetResult.Inaccessible)
        assertEquals(
            MarkdownAssetFailure.UNKNOWN_BASE,
            (r as MarkdownAssetResult.Inaccessible).reason,
        )
        assertEquals("无法访问相对图片", MarkdownAssetResolver.userMessage(r))
    }

    @Test
    fun no_sibling_uri_guessing_for_content() {
        val base = MarkdownDocumentBase.SingleContentDocument(
            "content://com.example.provider/document/note.md",
        )
        val r = MarkdownAssetResolver.resolve(base, "sibling.png")
        assertTrue(r is MarkdownAssetResult.Inaccessible)
        assertTrue(r !is MarkdownAssetResult.Resolved)
        assertTrue(r !is MarkdownAssetResult.Absolute)
    }

    // --- LocalImageLoader bridge (file:// only, no Context) ---

    @Test
    fun localImageLoader_bridge_resolves_and_rejects() {
        val root = tmp.newFolder("bridge")
        val entry = File(root, "index.md").also { it.writeText("# t") }
        File(root, "images").mkdirs()
        png(File(root, "images/fig.png"))
        val uri = "file://${entry.absolutePath}"

        val ok = com.markleaf.app.image.LocalImageLoader.resolveRelativeImage(uri, "images/fig.png")
        assertNotNull(ok)
        assertTrue(ok!!.isFile)

        assertNull(
            com.markleaf.app.image.LocalImageLoader.resolveRelativeImage(uri, "../etc/passwd"),
        )
        assertNull(
            com.markleaf.app.image.LocalImageLoader.resolveRelativeImage(
                "content://media/1",
                "a.png",
            ),
        )
    }

    @Test
    fun parent_escape_via_double_dot_rejected() {
        val root = tmp.newFolder("escape")
        val entry = File(root, "index.md").also { it.writeText("# t") }
        val uri = "file://${entry.absolutePath}"
        assertNull(
            com.markleaf.app.image.LocalImageLoader.resolveRelativeImage(uri, "a/../../x.png"),
        )
    }
}
