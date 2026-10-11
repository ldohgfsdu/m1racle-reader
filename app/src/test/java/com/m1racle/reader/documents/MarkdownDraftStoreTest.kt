package com.m1racle.reader.documents

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
// Windows File.renameTo lacks Android/Linux replacement semantics; adapt only this OS primitive.
// The same assertions also run against the real AtomicFile on API36.
@org.robolectric.annotation.Config(shadows = [AndroidAtomicRenameShadow::class])
class MarkdownDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun reopenRecoversUnicodeNewlinesAndOriginalBaseHash() {
        val original = MarkdownDraftStore.Draft("original-sha", "# 标题🌟\r\n\r\n```kt\n\"字\"\n```\n")
        MarkdownDraftStore(folder.root, "../book").write(original)
        assertEquals(original, MarkdownDraftStore(folder.root, "../book").read())
        assertNull(MarkdownDraftStore(folder.root, "other").read())
        assertEquals(1, File(folder.root, "markdown-drafts").listFiles()!!.size)
    }
    @Test fun replacementAndDiscardAreDocumentScoped() {
        val first = MarkdownDraftStore(folder.root, "first")
        val second = MarkdownDraftStore(folder.root, "second")
        first.write(MarkdownDraftStore.Draft("old-hash", "draft one"))
        second.write(MarkdownDraftStore.Draft("second-hash", "draft two"))
        first.write(MarkdownDraftStore.Draft("old-hash", "edited again"))
        assertEquals("old-hash", first.read()!!.baseHash)
        assertEquals("edited again", first.read()!!.text)
        first.delete()
        assertNull(first.read())
        assertEquals("draft two", second.read()!!.text)
    }
    @Test fun interruptedAtomicWriteRestoresLastCompleteDraft() {
        val store = MarkdownDraftStore(folder.root, "book")
        val old = MarkdownDraftStore.Draft("hash", "complete draft")
        store.write(old)
        val base = File(folder.root, "markdown-drafts").listFiles()!!.single()
        android.util.AtomicFile(base).let { file ->
            val stream = file.startWrite()
            stream.write("broken json".toByteArray())
            file.failWrite(stream)
        }
        assertEquals(old, MarkdownDraftStore(folder.root, "book").read())
    }
}

@org.robolectric.annotation.Implements(android.util.AtomicFile::class)
class AndroidAtomicRenameShadow {
    companion object {
        @JvmStatic @org.robolectric.annotation.Implementation
        fun rename(source: File, target: File) {
            java.nio.file.Files.move(source.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
