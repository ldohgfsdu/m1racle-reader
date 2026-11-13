package com.m1racle.reader.pdfsplit.split

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSplitCleanupTest {

    @Test
    fun `reports complete cleanup when every created output is deleted`() {
        val result = cleanCreatedOutputs(
            outputs = listOf(
                CreatedOutput("first.pdf", "first"),
                CreatedOutput("second.pdf", "second"),
            ),
            deleteDocument = { true },
        )

        assertEquals(2, result.attempted)
        assertEquals(2, result.deleted)
        assertTrue(result.isComplete)
        assertEquals(emptyList<String>(), result.residualNames)
        assertEquals("写入中的文件已清理", result.userMessage())
    }

    @Test
    fun `reports the output name when the provider returns false`() {
        val result = cleanCreatedOutputs(
            outputs = listOf(
                CreatedOutput("deleted.pdf", "deleted"),
                CreatedOutput("residual.pdf", "residual"),
            ),
            deleteDocument = { reference -> reference == "deleted" },
        )

        assertEquals(1, result.deleted)
        assertFalse(result.isComplete)
        assertEquals(listOf("residual.pdf"), result.residualNames)
        assertTrue(result.userMessage().contains("residual.pdf（系统未删除）"))
    }

    @Test
    fun `reports the output name and reason when deletion throws`() {
        val result = cleanCreatedOutputs(
            outputs = listOf(
                CreatedOutput("locked.pdf", "locked"),
                CreatedOutput("deleted.pdf", "deleted"),
            ),
            deleteDocument = { reference ->
                if (reference == "locked") throw SecurityException("provider denied")
                true
            },
        )

        assertEquals(1, result.deleted)
        assertEquals(listOf("locked.pdf"), result.residualNames)
        assertTrue(result.userMessage().contains("locked.pdf（删除异常：provider denied）"))
    }

    @Test
    fun `counts failures separately for same-name outputs with different uris`() {
        val result = cleanCreatedOutputs(
            outputs = listOf(
                CreatedOutput("same.pdf", "uri-one"),
                CreatedOutput("same.pdf", "uri-two"),
            ),
            deleteDocument = { false },
        )

        assertEquals(2, result.attempted)
        assertEquals(0, result.deleted)
        assertEquals(2, result.failures.size)
        assertEquals(listOf("same.pdf", "same.pdf"), result.residualNames)
        assertEquals(
            "已删除 0/2 个未完成文件；仍残留：" +
                "same.pdf（系统未删除）、same.pdf（系统未删除）",
            result.userMessage(),
        )
    }

    @Test
    fun `cancelled task still executes cleanup and returns its result`() = runBlocking {
        var result: SplitCleanupResult? = null
        val started = CompletableDeferred<Unit>()
        val job = launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                result = cleanCreatedOutputsNonCancellable(
                    outputs = listOf(CreatedOutput("partial.pdf", "partial")),
                    deleteDocument = { true },
                )
            }
        }

        started.await()
        job.cancelAndJoin()

        val cleanup = requireNotNull(result)
        assertEquals(1, cleanup.attempted)
        assertEquals(1, cleanup.deleted)
        assertTrue(cleanup.isComplete)
    }
}
