package com.m1racle.reader.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.m1racle.reader.ui.theme.M1racleReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SettingsBrandHeaderTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var textMeasurer: TextMeasurer

    @Test
    fun expandedBrandIsCenteredOnTheSingleScrollPage() {
        showSettings()
        val brand = brand()
        assertExpanded(brand)
        rule.onAllNodesWithTag(HEADER).assertCountEquals(1)
        rule.onAllNodesWithTag(LOGO).assertCountEquals(1)
        rule.onAllNodesWithTag(NAME).assertCountEquals(1)
        rule.onAllNodesWithTag(SCROLL).assertCountEquals(1)
        rule.onNodeWithTag(SCROLL).assert(hasScrollAction())
        assertEquals(0f, scrollOffset(), 0f)
        assertTrue(scrollMax() > 0f)
        listOf("阅读字体", "阅读显示", "文件与 PDF 工具", "数据与隐私", "关于 Reader").forEach {
            rule.onAllNodesWithText(it).assertCountEquals(1)
        }
    }

    @Test
    fun pageSwipesMoveTheLogoUpLeftAndRestoreItContinuously() {
        showSettings()
        val expanded = brand()
        val expandedText = nameText()
        assertExpanded(expanded)
        val max = scrollMax()

        swipe(up = true, from = 0.62f, to = 0.57f)
        val halfwayOffset = scrollOffset()
        val halfway = brand()
        val halfwayText = nameText()
        assertTrue(halfwayOffset > 0f && halfwayOffset < max)
        assertBetween(expanded, halfway, expandedText, halfwayText)

        swipe(up = true, from = 0.90f, to = 0.10f)
        val collapsedOffset = scrollOffset()
        val collapsed = brand()
        val collapsedText = nameText()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(collapsedOffset > halfwayOffset)
        assertInside(root, clippedPx(HEADER))
        assertInside(root, clippedPx(LOGO))
        assertTrue(
            "collapsed logo must move up, not down",
            collapsed.logo.top < halfway.logo.top && collapsed.logo.top < expanded.logo.top,
        )
        assertTrue(collapsed.logo.left < halfway.logo.left && collapsed.logo.left < expanded.logo.left)
        assertTrue(collapsed.logo.center.x < root.center.x)
        assertTrue(collapsed.logo.width < halfway.logo.width && collapsed.logo.height < halfway.logo.height)
        assertTrue(collapsedText.fontSize < halfwayText.fontSize)
        assertTrue(collapsedText.glyphWidth < halfwayText.glyphWidth)
        assertTrue(collapsed.name.top < halfway.name.top)

        repeat(4) { swipe(up = false, from = 0.12f, to = 0.90f) }
        assertEquals(0f, scrollOffset(), 0f)
        val restored = brand()
        val restoredText = nameText()
        assertEquals(expanded.logo, restored.logo)
        assertEquals(expanded.name, restored.name)
        assertEquals(expandedText.snapshot, restoredText.snapshot)
        assertExpanded(restored)
    }

    @Test
    fun largeTextInANarrowPageDoesNotClipTheBrand() {
        showSettings(fontScale = 2f, narrow = true)
        val brand = brand()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertExpanded(brand)
        assertInside(root, brand.header)
        assertInside(root, brand.logo)
        assertInside(brand.header, brand.logo)
        listOf(HEADER, LOGO, NAME).forEach { tag ->
            val node = rule.onNodeWithTag(tag)
            assertEquals(node.getUnclippedBoundsInRoot(), node.getBoundsInRoot())
        }
        val text = nameText()
        assertFalse(text.layout.hasVisualOverflow)
        assertFalse(text.layout.didOverflowWidth)
        assertFalse(text.layout.didOverflowHeight)
        assertTrue(text.glyphWidth > 0f)
    }

    @Test
    fun collapsedBrandHoldsStillWhenTheClockAdvances() {
        showSettings()
        val expanded = brand()
        swipe(up = true, from = 0.90f, to = 0.10f)
        val collapsed = brand()
        val collapsedText = nameText()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertInside(root, collapsed.logo)
        assertTrue(collapsed.logo.top < expanded.logo.top)
        assertTrue(collapsed.logo.left < expanded.logo.left)

        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1_000)
        val first = brand()
        val firstText = nameText()
        rule.mainClock.advanceTimeBy(1_000)
        rule.mainClock.autoAdvance = true

        assertEquals(collapsed.logo, first.logo)
        assertEquals(collapsed.name, first.name)
        assertEquals(collapsedText.snapshot, firstText.snapshot)
        assertEquals(first.logo, brand().logo)
        assertEquals(first.name, brand().name)
        assertEquals(firstText.snapshot, nameText().snapshot)
    }

    @Test
    fun brandNameIsReaderWithoutTheApplicationName() {
        showSettings()
        rule.onNodeWithTag(NAME).assertTextEquals("Reader")
        assertEquals("Reader", nameText().layout.layoutInput.text.text)
        assertEquals(0, rule.onAllNodes(hasText("M1racle Reader", substring = true)).fetchSemanticsNodes().size)
    }

    private fun showSettings(fontScale: Float = 1f, narrow: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                M1racleReaderTheme {
                    textMeasurer = rememberTextMeasurer()
                    if (narrow) {
                        Box(Modifier.size(320.dp, 640.dp)) {
                            HubSettingsScreen(onNavigateHub = {}, onOpenDocumentTools = {})
                        }
                    } else {
                        HubSettingsScreen(onNavigateHub = {}, onOpenDocumentTools = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun brand() = Brand(
        header = unclippedPx(HEADER),
        logo = unclippedPx(LOGO),
        name = unclippedPx(NAME),
    )

    private fun clippedPx(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun unclippedPx(tag: String): Rect {
        val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return with(rule.density) {
            Rect(
                left = bounds.left.toPx(),
                top = bounds.top.toPx(),
                right = bounds.right.toPx(),
                bottom = bounds.bottom.toPx(),
            )
        }
    }

    private fun nameText(): NameText {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(NAME).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        val font = layout.layoutInput.style.fontSize
        assertTrue(font.isSp)
        var glyphs = 0f
        for (line in 0 until layout.lineCount) {
            glyphs = maxOf(glyphs, layout.getLineRight(line) - layout.getLineLeft(line))
        }
        val measured = textMeasurer.measure(
            text = layout.layoutInput.text,
            style = layout.layoutInput.style,
            overflow = layout.layoutInput.overflow,
            softWrap = layout.layoutInput.softWrap,
            maxLines = layout.layoutInput.maxLines,
            constraints = layout.layoutInput.constraints,
        )
        val lineBounds = List(measured.lineCount) { line ->
            Rect(
                left = measured.getLineLeft(line),
                top = measured.multiParagraph.getLineTop(line),
                right = measured.getLineRight(line),
                bottom = measured.multiParagraph.getLineBottom(line),
            )
        }
        return NameText(
            layout = layout,
            snapshot = VisibleTextSnapshot(
                fontSize = font.value,
                glyphWidth = glyphs,
                size = measured.size,
                lineCount = measured.lineCount,
                lineBounds = lineBounds,
            ),
        )
    }

    private fun scrollOffset(): Float = rule.onNodeWithTag(SCROLL).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value.invoke()

    private fun scrollMax(): Float = rule.onNodeWithTag(SCROLL).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].maxValue.invoke()

    private fun swipe(up: Boolean, from: Float, to: Float) {
        rule.onNodeWithTag(SCROLL).performTouchInput {
            if (up) swipeUp(startY = height * from, endY = height * to, durationMillis = 300)
            else swipeDown(startY = height * from, endY = height * to, durationMillis = 300)
        }
        rule.waitForIdle()
    }

    private fun assertExpanded(brand: Brand) {
        assertEquals(brand.header.center.x, brand.logo.center.x, 0.5f)
        assertEquals(brand.logo.center.x, brand.name.center.x, 0.5f)
        assertTrue(brand.name.top >= brand.logo.bottom)
        val text = nameText()
        assertTrue(text.glyphWidth > 0f)
        for (line in 0 until text.layout.lineCount) {
            val glyphCenter = (text.layout.getLineLeft(line) + text.layout.getLineRight(line)) / 2f
            assertEquals(brand.name.width / 2f, glyphCenter, 0.5f)
        }
    }

    private fun assertBetween(expanded: Brand, halfway: Brand, expandedText: NameText, halfwayText: NameText) {
        assertTrue(halfway.logo.center.x < expanded.logo.center.x)
        assertTrue(halfway.logo.left < expanded.logo.left)
        assertTrue(halfway.logo.top < expanded.logo.top)
        assertTrue(halfway.logo.width < expanded.logo.width && halfway.logo.height < expanded.logo.height)
        assertTrue(halfway.name.top < expanded.name.top)
        assertTrue(halfwayText.fontSize < expandedText.fontSize)
        assertTrue(halfwayText.glyphWidth < expandedText.glyphWidth)
    }

    private fun assertInside(outer: Rect, inner: Rect) {
        assertTrue(inner.width > 0f && inner.height > 0f)
        assertTrue(inner.left >= outer.left && inner.top >= outer.top)
        assertTrue(inner.right <= outer.right && inner.bottom <= outer.bottom)
    }

    private data class Brand(val header: Rect, val logo: Rect, val name: Rect)

    private data class NameText(val layout: TextLayoutResult, val snapshot: VisibleTextSnapshot) {
        val fontSize: Float get() = snapshot.fontSize
        val glyphWidth: Float get() = snapshot.glyphWidth
    }

    private data class VisibleTextSnapshot(
        val fontSize: Float,
        val glyphWidth: Float,
        val size: IntSize,
        val lineCount: Int,
        val lineBounds: List<Rect>,
    )

    private companion object {
        const val HEADER = "settings-brand-header"
        const val LOGO = "settings-brand-logo"
        const val NAME = "settings-brand-name"
        const val SCROLL = "settings-brand-scroll"
    }
}
