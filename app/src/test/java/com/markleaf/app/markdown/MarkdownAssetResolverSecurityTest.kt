package com.markleaf.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * Security / encoding matrix for R8 V1 — independent of implementation-report claims.
 */
class MarkdownAssetResolverSecurityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun png(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    }

    private fun fileBase(root: File, entry: File): MarkdownDocumentBase.FileDirectory =
        MarkdownDocumentBase.FileDirectory(
            documentUri = "file://${entry.absolutePath}",
            entryFile = entry.canonicalFile,
            rootDir = root.canonicalFile,
        )

    private fun pathOf(result: MarkdownAssetResult): String? =
        when (result) {
            is MarkdownAssetResult.Resolved ->
                MarkdownAssetResolver.filePathFromDocumentUri(result.uri)
            else -> null
        }

    // --- Encoding / decode once ---

    @Test
    fun percentDecodeOnce_doesNotConvertPlusToSpace() {
        assertEquals("a+b.png", MarkdownAssetResolver.percentDecodeOnce("a+b.png"))
        assertEquals("a b.png", MarkdownAssetResolver.percentDecodeOnce("a%20b.png"))
    }

    @Test
    fun percentDecodeOnce_singlePass_doubleEncodedDotDot() {
        // %252e%252e → %2e%2e (literal), not ..
        assertEquals("%2e%2e", MarkdownAssetResolver.percentDecodeOnce("%252e%252e"))
        assertEquals("..", MarkdownAssetResolver.percentDecodeOnce("%2e%2e"))
        assertEquals("..", MarkdownAssetResolver.percentDecodeOnce("%2E%2E"))
    }

    @Test
    fun encoded_dot_dot_still_sandbox_checked() {
        val root = tmp.newFolder("enc_dot")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val outside = File(tmp.root, "escape.png").also { png(it) }
        val base = fileBase(root, entry)
        // %2e%2e → .. after one decode → outside root
        val r = MarkdownAssetResolver.resolve(base, "%2e%2e/escape.png")
        assertTrue("$r", r is MarkdownAssetResult.Invalid)
        assertEquals(MarkdownAssetFailure.OUTSIDE_ROOT, (r as MarkdownAssetResult.Invalid).reason)
        assertTrue(outside.isFile)
    }

    @Test
    fun double_encoded_dot_dot_is_literal_filename_not_traversal() {
        val root = tmp.newFolder("dbl_enc")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        // File named literally %2e%2e.png after one decode of %252e%252e.png
        png(File(root, "%2e%2e.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "%252e%252e.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "%2e%2e.png").canonicalPath, pathOf(r))
    }

    @Test
    fun encoded_slash_and_backslash_become_separators_after_one_decode() {
        val root = tmp.newFolder("enc_sep")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        png(File(root, "images/child.png"))
        val base = fileBase(root, entry)
        val slash = MarkdownAssetResolver.resolve(base, "images%2Fchild.png")
        assertTrue("$slash", slash is MarkdownAssetResult.Resolved)
        val back = MarkdownAssetResolver.resolve(base, "images%5Cchild.png")
        assertTrue("$back", back is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "images/child.png").canonicalPath, pathOf(slash))
        assertEquals(File(root, "images/child.png").canonicalPath, pathOf(back))
    }

    @Test
    fun plus_in_filename_preserved() {
        val root = tmp.newFolder("plus")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        png(File(root, "a+b.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "a+b.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "a+b.png").canonicalPath, pathOf(r))
    }

    @Test
    fun query_and_fragment_stripped_from_relative_path() {
        val root = tmp.newFolder("qf")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        png(File(root, "image.png"))
        val base = fileBase(root, entry)
        val q = MarkdownAssetResolver.resolve(base, "image.png?raw=1")
        val f = MarkdownAssetResolver.resolve(base, "image.png#frag")
        val both = MarkdownAssetResolver.resolve(base, "image.png?x=1#y")
        assertTrue(q is MarkdownAssetResult.Resolved)
        assertTrue(f is MarkdownAssetResult.Resolved)
        assertTrue(both is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "image.png").canonicalPath, pathOf(q))
    }

    @Test
    fun query_or_fragment_only_invalid() {
        val root = tmp.newFolder("qf_only")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val base = fileBase(root, entry)
        assertTrue(MarkdownAssetResolver.resolve(base, "?only") is MarkdownAssetResult.Invalid)
        assertTrue(MarkdownAssetResolver.resolve(base, "#only") is MarkdownAssetResult.Invalid)
        assertTrue(MarkdownAssetResolver.resolve(base, "?") is MarkdownAssetResult.Invalid)
        assertTrue(MarkdownAssetResolver.resolve(base, "#") is MarkdownAssetResult.Invalid)
    }

    @Test
    fun continuous_slashes_and_dot_segments() {
        val root = tmp.newFolder("slashy")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        png(File(root, "images/image.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "images//./image.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
        assertEquals(File(root, "images/image.png").canonicalPath, pathOf(r))
    }

    @Test
    fun backslash_normalized() {
        val root = tmp.newFolder("bslash")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        png(File(root, "images/image.png"))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "images\\image.png")
        assertTrue("$r", r is MarkdownAssetResult.Resolved)
    }

    // --- Schemes ---

    @Test
    fun absolute_schemes_case_insensitive_passthrough() {
        val base = MarkdownDocumentBase.Unknown
        listOf(
            "HTTP://Example.COM/a.png",
            "Https://example.com/a.png",
            "FILE:///tmp/a.png",
            "Content://authority/id",
            "DATA:image/png;base64,xx",
        ).forEach { ref ->
            val r = MarkdownAssetResolver.resolve(base, ref)
            assertTrue("$ref -> $r", r is MarkdownAssetResult.Absolute)
            assertEquals(ref, (r as MarkdownAssetResult.Absolute).uri)
        }
    }

    @Test
    fun protocol_relative_rejected() {
        val root = tmp.newFolder("proto")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "//evil.example/a.png")
        assertTrue("$r", r is MarkdownAssetResult.Invalid)
        assertEquals(
            MarkdownAssetFailure.UNSUPPORTED_SCHEME,
            (r as MarkdownAssetResult.Invalid).reason,
        )
    }

    @Test
    fun unknown_and_dangerous_schemes_invalid() {
        val root = tmp.newFolder("scheme")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val base = fileBase(root, entry)
        listOf(
            "javascript:alert(1)",
            "ftp://host/a.png",
            "blob:https://x",
            "vbscript:msg",
            "custom-scheme://x/y",
        ).forEach { ref ->
            val r = MarkdownAssetResolver.resolve(base, ref)
            assertTrue("$ref -> $r", r is MarkdownAssetResult.Invalid)
            assertEquals(
                MarkdownAssetFailure.UNSUPPORTED_SCHEME,
                (r as MarkdownAssetResult.Invalid).reason,
            )
        }
    }

    // --- Root containment / types ---

    @Test
    fun mixed_dot_dot_cannot_escape_root() {
        val root = tmp.newFolder("mix")
        val sub = File(root, "a/b").also { it.mkdirs() }
        val entry = File(sub, "doc.md").also { it.writeText("#") }
        png(File(root, "ok.png"))
        File(tmp.root, "outside.png").also { png(it) }
        val base = fileBase(root, entry)
        val ok = MarkdownAssetResolver.resolve(base, "../../ok.png")
        assertTrue("$ok", ok is MarkdownAssetResult.Resolved)
        val bad = MarkdownAssetResolver.resolve(base, "../../../outside.png")
        assertTrue("$bad", bad is MarkdownAssetResult.Invalid)
        assertEquals(MarkdownAssetFailure.OUTSIDE_ROOT, (bad as MarkdownAssetResult.Invalid).reason)
    }

    @Test
    fun symlink_escape_rejected_when_supported() {
        val root = tmp.newFolder("sym_root")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val outside = File(tmp.root, "secret_target.png").also { png(it) }
        val link = File(root, "link.png")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (_: Exception) {
            assumeTrue("symlink not supported in this environment", false)
        }
        assumeTrue(Files.isSymbolicLink(link.toPath()))
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "link.png")
        // canonical path points outside root → OUTSIDE_ROOT (or inaccessible)
        assertFalse("must not resolve symlink outside root: $r", r is MarkdownAssetResult.Resolved)
        assertTrue(
            "$r",
            r is MarkdownAssetResult.Invalid &&
                (r as MarkdownAssetResult.Invalid).reason == MarkdownAssetFailure.OUTSIDE_ROOT,
        )
    }

    @Test
    fun missing_file_does_not_throw() {
        val root = tmp.newFolder("miss")
        val entry = File(root, "doc.md").also { it.writeText("#") }
        val base = fileBase(root, entry)
        val r = MarkdownAssetResolver.resolve(base, "nope.png")
        assertTrue(r is MarkdownAssetResult.Inaccessible)
        assertEquals(MarkdownAssetFailure.NOT_FOUND, (r as MarkdownAssetResult.Inaccessible).reason)
    }

    @Test
    fun absolute_content_not_rewritten_on_single_content() {
        val base = MarkdownDocumentBase.SingleContentDocument("content://auth/doc/1")
        val abs = "content://auth/doc/99"
        val r = MarkdownAssetResolver.resolve(base, abs)
        assertTrue(r is MarkdownAssetResult.Absolute)
        assertEquals(abs, (r as MarkdownAssetResult.Absolute).uri)
    }

    @Test
    fun normalize_null_for_absolute_and_empty() {
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments("https://x/a.png"))
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments("//host/a.png"))
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments("javascript:x"))
        assertNull(MarkdownAssetResolver.normalizeRelativeSegments(""))
        assertNotNull(MarkdownAssetResolver.normalizeRelativeSegments("a/./b/../c.png"))
        assertEquals(
            listOf("a", "c.png"),
            MarkdownAssetResolver.normalizeRelativeSegments("a/./b/../c.png"),
        )
    }
}
