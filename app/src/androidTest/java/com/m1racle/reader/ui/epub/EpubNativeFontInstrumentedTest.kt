package com.m1racle.reader.ui.epub

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m1racle.reader.M1racleReaderApplication
import com.m1racle.reader.data.settings.EpubFontFamily
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.data.anchor.AnchorCodec
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.local.DocumentEntity
import com.m1racle.reader.data.local.DocumentType
import com.m1racle.reader.epub.*
import com.m1racle.reader.ui.components.ReaderFontContent
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubNativeFontInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<EpubQaHostActivity>()
    private val app = ApplicationProvider.getApplicationContext<M1racleReaderApplication>()
    private lateinit var original: ReaderSettings
    private lateinit var book: EpubBook
    private val opening = mutableStateOf(EpubSavedPosition.Start)
    private val incarnation = mutableIntStateOf(0)
    @Volatile private var saved: EpubPosition? = null
    @Volatile private var observed: ReaderSettings? = null

    @Before fun openOriginalBook() {
        assertEquals("com.m1racle.reader.qa", app.packageName)
        original = runBlocking { app.readerSettingsRepository.settingsFlow.first() }
        runBlocking { app.readerSettingsRepository.resetToDefaults() }
        val source = File(app.cacheDir, "restore43-original.epub")
        InstrumentationRegistry.getInstrumentation().context.assets.open("epub/HowToLiveBetter.epub").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals("61d26f6077d9804c0a6d86a178ac3746d8ba556563c3f023eabaae338f344847", hash)
        book = EpubPackageImporter.openCached(source, File(app.cacheDir, "restore43-original"), hash)
        val chapter = book.readingOrder.first { chapter -> chapter.blocks.any { it.readingText().length > 200 } }
        val block = chapter.blocks.indexOfFirst { it.readingText().length > 200 }
        opening.value = EpubSavedPosition(EpubReadingAnchors.anchorAt(book,
            EpubLocation(chapter.spineIndex, block, 0)), 0, 0, 0f)
        rule.setContent {
            val settings by app.readerSettingsRepository.settingsFlow.collectAsState(initial = ReaderSettings())
            // Read in this composition group so the observer itself reruns when
            // settings change; the nested reader group observes its own reads.
            val currentSettings = settings
            SideEffect { observed = currentSettings }
            M1racleReaderTheme {
                ReaderFontContent {
                    key(incarnation.intValue) {
                        EpubReaderContent(book, currentSettings, "高性价比人生指南", opening.value,
                            onPosition = { saved = it }, onFlush = {}, onBack = {}, onSettings = {},
                            insets = WindowInsets(0, 0, 0, 0))
                    }
                }
            }
        }
        awaitPage()
    }

    @After fun restorePreferences() = runBlocking {
        app.readerSettingsRepository.updateFontScale(original.fontScale)
        app.readerSettingsRepository.updateLineHeightScale(original.lineHeightScale)
        app.readerSettingsRepository.updateContentPaddingLevel(original.contentPaddingLevel)
        app.readerSettingsRepository.updateEpubFontFamily(original.epubFontFamily)
        app.readerSettingsRepository.updateImportedFont(original.importedFontId)
    }

    @Test fun originalBookUses43LightDragVerticalHoldCancelAndOnePageNavigation() {
        val before = pageNumber()
        val beforeAnchor = requireNotNull(saved).anchor
        repeat(3) {
            rule.onNodeWithTag("epub-pager").performTouchInput {
                down(percentOffset(.62f, .5f))
                moveTo(percentOffset(.5f, .5f), 350)
                moveTo(percentOffset(.38f, .5f), 350)
                advanceEventTime(200); up()
            }
            rule.waitForIdle()
            assertEquals(before, pageNumber())
            assertEquals(beforeAnchor, saved!!.anchor)
        }
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeUp(durationMillis = 500) }
        rule.waitForIdle()
        assertEquals(before, pageNumber())
        rule.onNodeWithTag("epub-pager").performTouchInput {
            down(percentOffset(.8f, .5f)); moveTo(percentOffset(.2f, .5f), 700)
        }
        Thread.sleep(1_400)
        rule.waitForIdle()
        assertEquals(before, pageNumber())
        assertEquals(beforeAnchor, saved!!.anchor)
        rule.onNodeWithTag("epub-pager").performTouchInput {
            moveTo(percentOffset(.78f, .5f), 700)
            advanceEventTime(200); cancel()
        }
        rule.waitForIdle()
        assertEquals(before, pageNumber())
        assertEquals(beforeAnchor, saved!!.anchor)
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft(durationMillis = 600) }
        rule.waitForIdle()
        assertEquals(before + 1, pageNumber())
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeRight(durationMillis = 600) }
        rule.waitForIdle()
        assertEquals(before, pageNumber())
    }

    @Test fun originalBookSdkRootPathRestoresTheNativeSourceQuote() {
        val chapter = book.readingOrder.first { it.blocks.any { block -> block.readingText().length > 200 } }
        val block = chapter.blocks.indexOfFirst { it.readingText().length > 200 }
        val quote = chapter.blocks[block].readingText().substring(5)
        val locator = "epub-locator:" + org.json.JSONObject().put("href", "OEBPS/${chapter.href}")
            .put("locations", org.json.JSONObject().put("progression", .5).put("totalProgression", .3))
            .put("text", org.json.JSONObject().put("highlight", quote)).toString()
        val record = DocumentEntity("rollback-original", "原书", "file:///original.epub", "application/epub+zip",
            741028, 1, 1, 73, 0, .3f, null, 0, DocumentType.EPUB.name, readingAnchor = locator)
        val after = rollbackEpubPosition(book, record)
        val anchor = AnchorCodec.decode(after.readingAnchor) as DocumentAnchor.EpubText
        assertEquals(chapter.href, anchor.spineHref)
        assertEquals(block, anchor.blockIndex)
        assertEquals(5, anchor.charStart)
        assertEquals(EpubRestoreTier.Exact, EpubReadingAnchors.restore(book, after).tier)
    }

    @Test fun importedPrivateFontChangesNativePixelsAndPreservesReflowAndReopenAnchor() {
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft(durationMillis = 600) }
        rule.waitForIdle()
        val before = requireNotNull(saved).anchor
        val image = rule.onNodeWithTag("epub-reading-area").captureToImage().asAndroidBitmap()
        val source = File(app.cacheDir, "restore43-font.otf")
        app.assets.open("fonts/NotoSerifCJKsc-Regular.otf").use { input -> source.outputStream().use(input::copyTo) }
        val font = runBlocking { app.readerFontRepository.import(Uri.fromFile(source)) }
        try {
            source.delete()
            runBlocking { app.readerSettingsRepository.updateImportedFont(font.id) }
            rule.waitForIdle()
            android.util.Log.i("EpubNativeFontQA", "stored=${runBlocking { app.readerSettingsRepository.settingsFlow.first() }.importedFontId} observed=${observed?.importedFontId} wanted=${font.id}")
            rule.waitUntil(30_000) { observed?.importedFontId == font.id }
            awaitPage()
            android.util.Log.i("EpubNativeFontQA", "before=$before after=${saved?.anchor} visible=${visibleText().take(200)}")
            rule.waitUntil(30_000) {
                saved?.anchor == before && normalizedVisibleText().contains(before.quote.take(12))
            }
            rule.waitForIdle()
            val rendered = rule.onNodeWithTag("epub-reading-area").captureToImage().asAndroidBitmap()
            assertFalse("Imported font must change actual native glyph rendering", image.sameAs(rendered))
            image.recycle(); rendered.recycle()
            val durable = requireNotNull(saved).saved()
            rule.runOnIdle { opening.value = durable; incarnation.intValue++ }
            awaitPage()
            assertEquals(before, saved!!.anchor)
            assertTrue(normalizedVisibleText().contains(before.quote.take(12)))
            runBlocking { app.readerFontRepository.remove(font, app.readerSettingsRepository) }
            rule.waitForIdle()
            rule.waitUntil(30_000) { observed?.importedFontId == null }
            awaitPage()
            assertNull(runBlocking { app.readerSettingsRepository.settingsFlow.first() }.importedFontId)
            assertEquals(before, saved!!.anchor)
            runBlocking { app.readerSettingsRepository.updateEpubFontFamily(EpubFontFamily.Serif) }
            rule.waitForIdle()
            rule.waitUntil(30_000) { observed?.epubFontFamily == EpubFontFamily.Serif }
            awaitPage()
            assertEquals(before, saved!!.anchor)
        } finally { source.delete() }
    }

    private fun awaitPage() {
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.waitUntil(30_000) { saved != null }
    }
    private fun pageNumber() = rule.onNodeWithTag("epub-page-number").fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text.substringAfter("第 ").substringBefore(" /").toInt()
    private fun visibleText(): String {
        val area = rule.onNodeWithTag("epub-reading-area").fetchSemanticsNode().boundsInRoot
        return rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().filter { it.boundsInRoot.overlaps(area) }
            .flatMap { it.config[SemanticsProperties.Text] }.joinToString { it.text }
    }
    private fun normalizedVisibleText() = visibleText().map { if (it.isWhitespace()) ' ' else it }
        .joinToString("").replace(Regex(" +"), " ")
}
