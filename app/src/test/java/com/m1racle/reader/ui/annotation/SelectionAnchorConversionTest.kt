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
        val selection = TextSelection.PdfSelection(
            page = 12,
            leftPx = 100f, topPx = 200f, rightPx = 400f, bottomPx = 320f,
            pageWidthPx = 1000f, pageHeightPx = 1414f,
        )
        val anchor = selection.toAnchor() as DocumentAnchor.PdfPage
        assertEquals(12, anchor.page)
        assertEquals(0.1f, anchor.rect!!.x, 1e-6f)
        assertEquals(200f / 1414f, anchor.rect!!.y, 1e-6f)
        assertEquals(0.3f, anchor.rect!!.width, 1e-6f)
        assertEquals((320f - 200f) / 1414f, anchor.rect!!.height, 1e-6f)
    }

    @Test
    fun `same visual region at multiple pdf sizes yields identical anchor`() {
        // A4 视觉区域在 1×/3× 渲染尺寸下捕获：归一化锚点必须完全一致。
        val small = TextSelection.PdfSelection(
            page = 5,
            leftPx = 60f, topPx = 90f, rightPx = 260f, bottomPx = 150f,
            pageWidthPx = 595f, pageHeightPx = 842f,
        )
        val large = TextSelection.PdfSelection(
            page = 5,
            leftPx = 180f, topPx = 270f, rightPx = 780f, bottomPx = 450f,
            pageWidthPx = 1785f, pageHeightPx = 2526f,
        )
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
            TextSelection.PdfSelection(
                0, 50f, 50f, 50f, 80f, 1000f, 1414f,
            ).toAnchor(),
        )
        assertNull(
            TextSelection.PdfSelection(
                0, 300f, 100f, 100f, 200f, 1000f, 1414f,
            ).toAnchor(),
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
