package com.markleaf.app.ui.viewer.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.markleaf.app.markdown.InlineMarkdownParser
import com.markleaf.app.markdown.InlineMarkdownStyles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TableHeaderParityTest {
    @Test
    fun `resolved header style explicitly fixes every shared typography field`() {
        val body = TextStyle(
            color = Color.Black,
            fontSize = 14.52.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 22.5.sp,
        )
        val resolved = resolveTableHeaderTextStyle(body)
        val threeHeaderCells = List(3) { resolved }

        assertTrue(threeHeaderCells.all { it == resolved })
        assertTrue(threeHeaderCells.all { it.fontSize == 14.52.sp })
        assertTrue(threeHeaderCells.all { it.fontWeight == FontWeight.SemiBold })
        assertTrue(threeHeaderCells.all { it.lineHeight == 22.5.sp })
        assertTrue(threeHeaderCells.all { it.letterSpacing == 0.sp })
        assertTrue(threeHeaderCells.all { it.fontFamily == FontFamily.Default })
        assertTrue(threeHeaderCells.all {
            it.platformStyle == PlatformTextStyle(includeFontPadding = false)
        })
        assertTrue(threeHeaderCells.all { it.textDirection == TextDirection.ContentOrLtr })
        assertTrue(threeHeaderCells.all { it.textAlign == TextAlign.Start })
        threeHeaderCells.forEach { assertSame(resolved, it) }
        assertEquals(FontWeight.Normal, body.fontWeight)
    }

    @Test
    fun `header row has one shared style and geometry with no autosize branch`() {
        val source = tableSource()
        val headerRow = source.substringAfter("private fun TableHeaderRow(")
            .substringBefore("private fun TableRow(")

        assertTrue(headerRow.contains("resolvedHeaderTextStyle: TextStyle"))
        assertTrue(headerRow.contains("style = resolvedHeaderTextStyle"))
        assertFalse(headerRow.contains("resolvedHeaderTextStyle.copy"))
        assertTrue(headerRow.contains("alignByBaseline()"))
        assertFalse(headerRow.contains("height(IntrinsicSize.Min)"))
        assertFalse(headerRow.contains("fillMaxHeight()"))
        assertTrue(headerRow.contains("textAlign = TextAlign.Start"))
        assertTrue(headerRow.contains("softWrap = true"))
        assertTrue(headerRow.contains("maxLines = Int.MAX_VALUE"))
        assertFalse(headerRow.contains("widthIn"))
        assertFalse(headerRow.contains("minimumInteractiveComponentSize"))
        assertFalse(headerRow.contains("scaleX"))
        assertFalse(headerRow.contains("autoSize"))
    }

    @Test
    fun `inline strong changes header weight without changing size or line height`() {
        val styles = InlineMarkdownStyles.themed(Color.Black, Color.Transparent)
        val strong = InlineMarkdownParser.parse("**项目**", styles).spanStyles.single().item

        assertEquals(FontWeight.SemiBold, strong.fontWeight)
        assertEquals(TextUnit.Unspecified, strong.fontSize)
        assertEquals(TextUnit.Unspecified, strong.letterSpacing)
        assertEquals(null, strong.fontFamily)
    }

    private fun tableSource(): String = listOf(
        File("app/src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt"),
        File("src/main/java/com/markleaf/app/ui/viewer/components/TableBlockView.kt"),
    ).first { it.isFile }.readText()
}
