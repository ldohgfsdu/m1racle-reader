package com.m1racle.reader.ui.epub

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.m1racle.reader.data.anchor.DocumentAnchor
import com.m1racle.reader.data.anchor.EpubTextAnchorResolver
import com.m1racle.reader.data.settings.ContentPaddingLevel
import com.m1racle.reader.data.settings.ReaderSettings
import com.m1racle.reader.epub.EpubBlock
import com.m1racle.reader.epub.EpubBook
import com.m1racle.reader.epub.EpubChapter
import com.m1racle.reader.epub.EpubInline
import com.m1racle.reader.epub.EpubMetadata
import com.m1racle.reader.epub.EpubSpineItem
import com.m1racle.reader.epub.EpubReadingAnchors
import com.m1racle.reader.epub.EpubRestoreTier
import com.m1racle.reader.epub.EpubSavedPosition
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.m1racle.reader.epub.EpubRestoreSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * Anchors against real measured layouts: font family, font size, line spacing
 * and margins change the pages, never the anchor or the text it lands on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class EpubAnchorReflowHostTest {
    @get:Rule val rule = createComposeRule()

    private fun words(from: Int, count: Int) = (from until from + count).joinToString(" ") { "word" + it.toString().padStart(4, '0') }

    private val book = EpubBook(
        EpubMetadata("测试书", emptyList(), "zh", null, "3.0", false, false),
        listOf(EpubSpineItem("one", "one.xhtml", true, 0), EpubSpineItem("two", "two.xhtml", true, 1)),
        emptyList(),
        listOf(
            EpubChapter(0, "one.xhtml", "第一章", listOf(
                EpubBlock.Paragraph(listOf(EpubInline(words(0, 1_200)))), // split into measurement rows
                EpubBlock.ListBlock(false, List(8) { listOf(EpubInline(words(2_000 + it * 40, 40))) }),
                EpubBlock.Paragraph(listOf(EpubInline(words(3_000, 300)))),
            ), emptyMap()),
            EpubChapter(1, "two.xhtml", "第二章", List(30) {
                EpubBlock.Paragraph(listOf(EpubInline(words(5_000 + it * 30, 30))))
            }, emptyMap()),
        ),
    )

    private data class Typography(val settings: ReaderSettings, val family: FontFamily?, val width: Int)

    private val variants = listOf(
        Typography(ReaderSettings(), null, 1080),
        Typography(ReaderSettings(fontScale = ReaderSettings.FONT_SMALL), null, 1080),
        Typography(ReaderSettings(fontScale = ReaderSettings.FONT_LARGE), null, 1080),
        Typography(ReaderSettings(lineHeightScale = ReaderSettings.LINE_COMPACT), null, 1080),
        Typography(ReaderSettings(lineHeightScale = ReaderSettings.MAX_LINE_HEIGHT_SCALE), null, 1080),
        // Margins only change the text width the reader measures with.
        Typography(ReaderSettings(contentPaddingLevel = ContentPaddingLevel.Compact), null, 1080 - 2 * 42),
        Typography(ReaderSettings(contentPaddingLevel = ContentPaddingLevel.Relaxed), null, 1080 - 2 * 84),
        Typography(ReaderSettings(), FontFamily.Serif, 1080),
        Typography(ReaderSettings(), FontFamily.Monospace, 1080),
        Typography(ReaderSettings(ReaderSettings.FONT_LARGE, ReaderSettings.LINE_RELAXED, ContentPaddingLevel.Relaxed),
            FontFamily.Monospace, 1080 - 2 * 84),
    )

    private fun measureAll(): List<EpubTextLayout> {
        val results = arrayOfNulls<EpubTextLayout>(variants.size)
        rule.setContent {
            M1racleReaderTheme {
                val measurer = rememberTextMeasurer(cacheSize = 0)
                val body = MaterialTheme.typography.bodyLarge
                variants.forEachIndexed { i, variant ->
                    LaunchedEffect(i) {
                        val style = if (variant.family == null) body else body.copy(fontFamily = variant.family)
                        val rows = epubTextRows(book, variant.settings, style)
                        results[i] = measureEpub(rows, measurer, variant.width, 1400, 24f, 24)
                    }
                }
            }
        }
        rule.waitForIdle()
        return results.map { requireNotNull(it) { "measureEpub never completed" } }
    }

    private fun EpubTextLayout.pageContains(page: Int, sourceRow: Int, offset: Int): Boolean =
        pages[page].slices.any { slice ->
            val row = rows[slice.row]
            row.sourceRow == sourceRow &&
                offset >= row.sourceTextStart + slice.start &&
                offset < row.sourceTextStart + maxOf(slice.end, slice.start + 1)
        }

    @Test fun `the same anchor lands on the page containing its character under every typography`() {
        val layouts = measureAll()
        val reference = EpubRowIndex(book, layouts.first().rows)
        // Long split paragraph (start, middle of a continuation), list item N, later chapter.
        val positions = listOf(0 to 0, 0 to 3_100, 0 to 7_777, 1 to (2 * 41 * 9 + 5), 3 to 120, 4 to 30, 30 to 3)
        val anchors = positions.map { (s, o) -> requireNotNull(reference.anchorAt(s, o)) }
        val bookmarks = anchors.mapIndexed { i, anchor ->
            EpubTextAnchorResolver.projectInBook(anchor, if (positions[i].first >= 3) 1 else 0)
        }
        val pageCounts = layouts.map { it.pages.size }
        assertTrue("typography must really reflow: $pageCounts", pageCounts.toSet().size >= 4)
        layouts.forEachIndexed { v, layout ->
            val index = EpubRowIndex(book, layout.rows)
            anchors.forEachIndexed { i, anchor ->
                val (source, offset) = positions[i]
                // Identity is unchanged by typography.
                assertEquals("${variants[v]}", anchor, index.anchorAt(source, offset))
                val restored = restoreEpubText(book, index, layout, EpubSavedPosition(anchor, 0, 0, 0f))
                assertEquals(EpubRestoredText(source, offset, EpubRestoreTier.Exact), restored)
                val page = layout.pageForText(restored.sourceRow, restored.offset)
                assertTrue("${variants[v]} anchor $anchor page $page", layout.pageContains(page, source, offset))
                // A bookmark stored under any typography resolves to the same place.
                val bookmark = EpubTextAnchorResolver.resolve(bookmarks[i].locationKind, bookmarks[i].location,
                    bookmarks[i].encoded) as DocumentAnchor.EpubText
                assertEquals(restored, restoreEpubText(book, index, layout, EpubSavedPosition(bookmark, 0, 0, 0f)))
                // Re-anchoring the restored page start never jumps before the anchored page.
                val first = layout.pages[page].slices.first()
                val start = layout.positionAt(first)
                assertEquals(page, layout.pageForText(start.first, start.third))
            }
        }
    }

    private fun pageLabel(): String = rule.onNodeWithTag("epub-page-number")
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun waitForPages() {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("epub-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { pageLabel().startsWith("第 ") }
        rule.waitForIdle()
    }

    @Test fun `cold start after typography changes reopens on the same text with an unchanged anchor`() {
        val shown = mutableStateOf(true)
        val settings = mutableStateOf(ReaderSettings(fontScale = ReaderSettings.FONT_LARGE))
        val family = mutableStateOf<FontFamily?>(null)
        val width = mutableStateOf(360)
        var saved = EpubSavedPosition.Start
        val emitted = mutableListOf<EpubPosition>()
        rule.setContent {
            M1racleReaderTheme {
                val base = MaterialTheme.typography
                val typography = remember(base, family.value) {
                    family.value?.let { base.copy(bodyLarge = base.bodyLarge.copy(fontFamily = it)) } ?: base
                }
                MaterialTheme(typography = typography) {
                    if (shown.value) EpubReaderContent(book, settings.value, "测试书", saved,
                        onPosition = { saved = it.saved(); emitted += it }, onFlush = {}, onBack = {}, onSettings = {},
                        modifier = Modifier.size(width.value.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
                }
            }
        }
        waitForPages()
        repeat(4) {
            rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.88f, 0.5f)) }
            rule.waitForIdle()
        }
        val anchor = requireNotNull(saved.anchor)
        assertTrue("must have left the first page: $anchor", anchor.charStart > 0)
        val token = Regex("word\\d{4}").find(anchor.quote)!!.value
        val firstLabel = pageLabel()
        // Each cold start: kill the reader, change one typography parameter, reopen.
        val changes = listOf<() -> Unit>(
            { settings.value = settings.value.copy(fontScale = ReaderSettings.FONT_SMALL) },
            { settings.value = settings.value.copy(lineHeightScale = ReaderSettings.MAX_LINE_HEIGHT_SCALE) },
            { settings.value = settings.value.copy(contentPaddingLevel = ContentPaddingLevel.Relaxed); width.value = 320 },
            { family.value = FontFamily.Serif },
            { settings.value = ReaderSettings(fontScale = ReaderSettings.FONT_LARGE); family.value = null; width.value = 360 },
        )
        changes.forEachIndexed { step, change ->
            rule.runOnIdle { shown.value = false }
            rule.runOnIdle { change(); emitted.clear(); shown.value = true }
            waitForPages()
            rule.onNodeWithText(token, substring = true).assertIsDisplayed()
            val reopened = requireNotNull(saved.anchor)
            assertEquals("step $step", anchor.spineHref, reopened.spineHref)
            assertEquals("step $step", anchor.blockIndex, reopened.blockIndex)
            assertEquals("step $step", anchor.charStart, reopened.charStart)
            assertTrue(emitted.isNotEmpty())
        }
        // Back at the original typography the original page comes back too.
        assertEquals(firstLabel, pageLabel())
    }

    @Test fun `a pre anchor record migrates once on open and later opens use the anchor`() {
        val shown = mutableStateOf(true)
        val legacy = EpubSavedPosition(anchor = null, lastScrollIndex = 20, lastScrollOffset = 0, readingProgress = 0.5f)
        var saved = legacy
        val emitted = mutableListOf<EpubPosition>()
        rule.setContent {
            M1racleReaderTheme {
                if (shown.value) EpubReaderContent(book, ReaderSettings(), "测试书", saved,
                    onPosition = { emitted += it }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0))
            }
        }
        waitForPages()
        // Source row 20 is chapter two, block 17 (chapter one has three blocks).
        val firstWord = "word" + (5_000 + (20 - 3) * 30).toString().padStart(4, '0')
        rule.onNodeWithText(firstWord, substring = true).assertIsDisplayed()
        val migrated = emitted.first()
        assertEquals("two.xhtml", migrated.anchor.spineHref)
        val label = pageLabel()
        // The first save writes the anchor; the reopened record is no longer a legacy one.
        saved = migrated.saved()
        assertNotEquals(legacy, saved)
        assertEquals(EpubRestoreTier.Exact, EpubReadingAnchors.restore(book, saved).tier)
        rule.runOnIdle { shown.value = false }
        rule.runOnIdle { emitted.clear(); shown.value = true }
        waitForPages()
        assertEquals(label, pageLabel())
        rule.onNodeWithText(firstWord, substring = true).assertIsDisplayed()
        assertEquals(migrated.anchor, emitted.first().anchor)
    }

    @Test fun `fallback keeps original anchor through font changes until an explicit page turn`() {
        val original = DocumentAnchor.EpubText("one.xhtml", 999, 0, parserVersion = 0,
            quote = "这段文字已经消失并且不会匹配到书中内容")
        val initial = EpubSavedPosition(original, 0, 0, 0f)
        val session = EpubRestoreSession(EpubReadingAnchors.restore(book, initial))
        val settings = mutableStateOf(ReaderSettings())
        val emitted = mutableListOf<EpubPosition>()
        var persisted = original
        rule.setContent {
            M1racleReaderTheme {
                EpubReaderContent(book, settings.value, "测试书", initial,
                    onPosition = {
                        emitted += it
                        if (it.canSaveAnchor) persisted = it.anchor
                    }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0),
                    restoreSession = session)
            }
        }
        waitForPages()
        rule.onNodeWithText("没能精确回到上次的位置，已跳到附近").assertIsDisplayed()
        assertTrue(emitted.isNotEmpty())
        assertTrue(emitted.all { !it.canSaveAnchor })
        assertEquals(original, persisted)
        val beforeReflow = emitted.size
        rule.runOnIdle { settings.value = ReaderSettings(fontScale = ReaderSettings.FONT_LARGE) }
        rule.waitUntil(15_000) { emitted.size > beforeReflow }
        waitForPages()
        assertTrue(session.preservesOriginalAnchor)
        assertTrue(emitted.all { !it.canSaveAnchor })
        assertEquals(original, persisted)
        rule.onNodeWithTag("epub-pager").performTouchInput { click(percentOffset(0.88f, 0.5f)) }
        rule.waitForIdle()
        assertFalse(session.preservesOriginalAnchor)
        assertTrue(emitted.last().canSaveAnchor)
        assertNotEquals(original, persisted)
    }

    @Test fun `prefix restore stays silent and a user swipe releases anchor protection`() {
        val original = requireNotNull(EpubReadingAnchors.anchorAt(book,
            com.m1racle.reader.epub.EpubLocation(0, 0, 0))).let {
            it.copy(quote = it.quote.take(12) + "old suffix")
        }
        val initial = EpubSavedPosition(original, 0, 0, 0f)
        val session = EpubRestoreSession(EpubReadingAnchors.restore(book, initial))
        assertEquals(EpubRestoreTier.QuotePrefix, session.result.tier)
        val emitted = mutableListOf<EpubPosition>()
        rule.setContent {
            M1racleReaderTheme {
                EpubReaderContent(book, ReaderSettings(), "测试书", initial,
                    onPosition = { emitted += it }, onFlush = {}, onBack = {}, onSettings = {},
                    modifier = Modifier.size(360.dp, 560.dp), insets = WindowInsets(0, 0, 0, 0),
                    restoreSession = session)
            }
        }
        waitForPages()
        assertTrue(emitted.isNotEmpty())
        assertTrue(emitted.all { !it.canSaveAnchor })
        rule.onNodeWithText("没能精确回到上次的位置，已跳到附近").assertDoesNotExist()
        rule.onNodeWithTag("epub-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertFalse(session.preservesOriginalAnchor)
        assertTrue(emitted.last().canSaveAnchor)
    }
}
