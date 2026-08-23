package com.markleaf.app.share

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden lock on the import-side staged-file name sanitizer
 * ([ExternalDocumentImporter.sanitizeFileName]) — the single surviving
 * implementation after the A3-3a merge (the ExternalFileIntake twin lived only
 * inside the dead `importSharedUri` path and was deleted, not merged).
 *
 * These assertions pin the CURRENT behavior before/after any refactor:
 * whitespace runs are preserved (`\s` in the keep-class) then outer-trimmed;
 * CJK and full-width parentheses are preserved; illegal ASCII runs collapse
 * to one `_`; 120-char cap; extension appended when no dot survives.
 */
class ExternalDocumentImporterSanitizeGoldenTest {

    private fun sanitize(name: String, ext: String = "bin"): String =
        ExternalDocumentImporter.sanitizeFileName(name, ext)

    @Test
    fun cjkDisplayNamePassesThrough() {
        assertEquals("甲号教材.pdf", sanitize("甲号教材.pdf", "pdf"))
    }

    @Test
    fun interiorWhitespaceIsPreserved() {
        assertEquals("我的 教材.pdf", sanitize("我的 教材.pdf", "pdf"))
        assertEquals("a\tb.md", sanitize("a\tb.md", "md"))
    }

    @Test
    fun leadingAndTrailingWhitespaceTrimmed() {
        assertEquals("lead.md", sanitize("  lead.md"))
        assertEquals("trail.pdf", sanitize("trail.pdf  ", "pdf"))
        // trim runs BEFORE the dot check, so a trimmed leading blank is gone.
        assertEquals(".hidden", sanitize(" .hidden"))
    }

    @Test
    fun pathSeparatorsStrippedBeforeSanitize() {
        assertEquals("file.txt", sanitize("dir/sub\\file.txt"))
        assertEquals("b.txt", sanitize("a///b.txt"))
    }

    @Test
    fun illegalAsciiRunsCollapseToSingleUnderscore() {
        assertEquals("bad_name_here_.pdf", sanitize("bad:name*here?.pdf", "pdf"))
        assertEquals("a_b_c.txt", sanitize("a:<>*|b\"c.txt"))
    }

    @Test
    fun fullWidthParensAndCjkPunctuationKept() {
        assertEquals("教材（第二版）v2.pdf", sanitize("教材（第二版）v2.pdf", "pdf"))
    }

    @Test
    fun missingDotAppendsFallbackExtension() {
        assertEquals("报告.pdf", sanitize("报告", "pdf"))
        assertEquals("x.md", sanitize("x", "md"))
    }

    @Test
    fun blankFallsBackToFileWithExtension() {
        assertEquals("file.pdf", sanitize("", "pdf"))
        assertEquals("file.bin", sanitize("", "bin"))
        assertEquals("file.pdf", sanitize("   ", "pdf"))
    }

    @Test
    fun loneDotStillCountsAsDot() {
        assertEquals("x.", sanitize("x.", "pdf"))
        assertEquals("name..md", sanitize("name..md", "md"))
    }

    @Test
    fun capAt120CharsThenAppendExtensionWhenDotLost() {
        val long = "a".repeat(200) + ".pdf"
        val out = sanitize(long, "pdf")
        assertEquals("a".repeat(120) + ".pdf", out)

        // Dot inside the first 120 chars survives; the tail is cut mid-run.
        val longWithEarlyDot = "a".repeat(60) + "." + "b".repeat(200)
        assertEquals(("a".repeat(60) + "." + "b".repeat(200)).take(120), sanitize(longWithEarlyDot))
    }
}
