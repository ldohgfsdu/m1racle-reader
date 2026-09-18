package com.m1racle.reader.ui.annotation

import com.m1racle.reader.data.anchor.DocumentAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R25 选择 → 锚点转换契约：多尺寸 PDF 恢复一致（缩放/密度无关）、
 * MD 字符区间恢复、非法选区拒绝。
 */
class SelectionAnchorConversionTest {

    @Test
    fun `pdf selection converts to normalized page anchor`() {
        val selection = TextSelection.PdfSelection(page = 12, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((100f) / (1000f), (200f) / (1414f), ((400f) - (100f)).coerceAtLeast(0f) / (1000f), ((320f) - (200f)).coerceAtLeast(0f) / (1414f))))
        val anchor = selection.toAnchor() as DocumentAnchor.PdfPage
        assertEquals(12, anchor.page)
        assertEquals(0.1f, anchor.rects.single().x, 1e-6f)
        assertEquals(200f / 1414f, anchor.rects.single().y, 1e-6f)
        assertEquals(0.3f, anchor.rects.single().width, 1e-6f)
        assertEquals((320f - 200f) / 1414f, anchor.rects.single().height, 1e-6f)
    }

    @Test
    fun `same visual region at multiple pdf sizes yields identical anchor`() {
        // A4 视觉区域在 1×/3× 渲染尺寸下捕获：归一化锚点必须完全一致。
        val small = TextSelection.PdfSelection(page = 5, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((60f) / (595f), (90f) / (842f), ((260f) - (60f)).coerceAtLeast(0f) / (595f), ((150f) - (90f)).coerceAtLeast(0f) / (842f))))
        val large = TextSelection.PdfSelection(page = 5, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((180f) / (1785f), (270f) / (2526f), ((780f) - (180f)).coerceAtLeast(0f) / (1785f), ((450f) - (270f)).coerceAtLeast(0f) / (2526f))))
        assertEquals(small.toAnchor(), large.toAnchor())
    }

    @Test
    fun `markdown selection keeps block index and char range`() {
        val selection = TextSelection.MarkdownSelection(
            blockIndex = 96, charStart = 7, charEnd = 33,
        )
        val anchor = selection.toAnchor() as DocumentAnchor.MarkdownBlock
        assertEquals(96, anchor.blockIndex)
        assertEquals(7, anchor.charStart)
        assertEquals(33, anchor.charEnd)
    }

    @Test
    fun `invalid selections are rejected`() {
        // MD：空区间 / 倒序。
        assertNull(TextSelection.MarkdownSelection(0, 10, 10).toAnchor())
        assertNull(TextSelection.MarkdownSelection(0, 20, 5).toAnchor())
        // PDF：零面积 / 倒序矩形。
        assertNull(
            TextSelection.PdfSelection(page = 0, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((50f) / (1000f), (50f) / (1414f), ((50f) - (50f)).coerceAtLeast(0f) / (1000f), ((80f) - (50f)).coerceAtLeast(0f) / (1414f)))).toAnchor(),
        )
        assertNull(
            TextSelection.PdfSelection(page = 0, rects = listOf(com.m1racle.reader.data.anchor.NormalizedRect((300f) / (1000f), (100f) / (1414f), ((100f) - (300f)).coerceAtLeast(0f) / (1000f), ((200f) - (100f)).coerceAtLeast(0f) / (1414f)))).toAnchor(),
        )
    }

    @Test
    fun `transient holder is memory only and clearable`() {
        val holder = TransientSelectionHolder()
        assertNull(holder.current)
        val selection = TextSelection.MarkdownSelection(3, 0, 9)
        holder.set(selection)
        assertEquals(selection, holder.current)
        holder.clear()
        assertNull(holder.current) // 清空即消失——无任何持久化路径
    }
}
