package com.m1racle.reader.pdfsplit.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCleanupMessageTest {

    @Test
    fun `reports complete cleanup only when every output was deleted`() {
        assertEquals(
            "写入中的文件已清理",
            formatSplitCleanupMessage(attempted = 2, deleted = 2, failures = emptyList()),
        )
        assertEquals(
            "未生成需要清理的文件",
            formatSplitCleanupMessage(attempted = 0, deleted = 0, failures = emptyList()),
        )
    }

    @Test
    fun `reports residual output when provider deletion returns false`() {
        val message = formatSplitCleanupMessage(
            attempted = 2,
            deleted = 1,
            failures = listOf(
                SplitCleanupFailure("residual.pdf", "系统未删除"),
            ),
        )

        assertTrue(message.startsWith("已删除 1/2 个未完成文件；仍残留："))
        assertTrue(message.contains("residual.pdf（系统未删除）"))
    }

    @Test
    fun `reports residual output when provider deletion throws`() {
        val message = formatSplitCleanupMessage(
            attempted = 1,
            deleted = 0,
            failures = listOf(
                SplitCleanupFailure("locked.pdf", "删除异常：provider denied"),
            ),
        )

        assertTrue(message.contains("locked.pdf（删除异常：provider denied）"))
    }

    @Test
    fun `limits residual names while retaining the full count`() {
        val failures = (1..5).map {
            SplitCleanupFailure("part-$it.pdf", "系统未删除")
        }

        val message = formatSplitCleanupMessage(
            attempted = 5,
            deleted = 0,
            failures = failures,
            maxFailuresShown = 3,
        )

        assertTrue(message.contains("part-1.pdf"))
        assertTrue(message.contains("part-3.pdf"))
        assertFalse(message.contains("part-4.pdf"))
        assertTrue(message.endsWith("，另有 2 个"))
    }
}
