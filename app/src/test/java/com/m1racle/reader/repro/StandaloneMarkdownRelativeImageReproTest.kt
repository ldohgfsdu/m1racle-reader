package com.m1racle.reader.repro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.markdown.MarkdownAssetResolver
import com.m1racle.reader.markdown.MarkdownAssetResult
import com.m1racle.reader.testing.ProductionSource
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REPRO 20261031-md-repro F2：独立 Markdown 相对图片在受管副本下找不到。
 * ViewerScreen 以 state.document.uri（书库副本）作为图片解析基准。
 * 预期：在修复前失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandaloneMarkdownRelativeImageReproTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun relativeImageNextToOriginalResolvesWhenReadingManagedCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalDir = tmp.newFolder("notes")
        File(originalDir, "note.md").writeText("![](img/a.png)\n")
        File(originalDir, "img").mkdirs()
        File(originalDir, "img/a.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val managed = File(context.filesDir, "library/abc123/uuid-1/note.md")
        managed.parentFile!!.mkdirs(); managed.writeText("![](img/a.png)\n")

        // 与修复后 ViewerScreen.kt MarkdownBlockView(documentUri = imageBaseUriFor(state.document)) → LocalImageLoader.load 同一调用
        val doc = DocumentEntity(
            id = "abc123", displayName = "note.md", uri = "file://${managed.absolutePath}", mimeType = "text/markdown",
            fileSize = null, lastOpenedAt = 0, createdAt = 0, lastScrollIndex = 0, lastScrollOffset = 0,
            readingProgress = 0f, title = null, headingCount = 0,
            sourceUri = "file://${File(originalDir, "note.md").absolutePath}",
        )
        val base = MarkdownAssetResolver.fromDocumentUri(context, MarkdownAssetResolver.imageBaseUriFor(doc), DocumentType.MARKDOWN)
        val result = MarkdownAssetResolver.resolve(base, "img/a.png")
        assertTrue("相对图片应解析到原目录（F2），实际：$result", result is MarkdownAssetResult.Resolved)
    }

    @Test
    fun viewerDoesNotUseManagedCopyAsImageBase() {
        val src = ProductionSource.text("com/m1racle/reader/ui/viewer/ViewerScreen.kt")
        assertFalse(
            "ViewerScreen 仍以书库副本 state.document.uri 作为 Markdown 图片基准（F2）",
            Regex("""MarkdownBlockView\(\s*documentUri\s*=\s*state\.document\.uri""").containsMatchIn(src),
        )
    }
}
