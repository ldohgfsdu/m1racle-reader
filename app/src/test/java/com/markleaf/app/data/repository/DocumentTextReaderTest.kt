package com.markleaf.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DocumentTextReaderTest {

    @Test
    fun `document larger than legacy 200 KiB is returned in full`() {
        val expected = buildString {
            append("# 长文\n")
            repeat(120_000) { append("墨阅正文") }
            append("\n完整结尾")
        }

        val actual = readUtf8TextFully(
            ByteArrayInputStream(expected.toByteArray(Charsets.UTF_8)),
        )

        assertEquals(expected, actual)
        assertTrue(actual.endsWith("完整结尾"))
    }

    @Test
    fun `oversized document fails instead of returning a truncated prefix`() {
        val bytes = "一二三四五六".toByteArray(Charsets.UTF_8)

        val failure = runCatching {
            readUtf8TextFully(ByteArrayInputStream(bytes), maxBytes = bytes.size - 1)
        }.exceptionOrNull()

        assertTrue(failure is DocumentReadException.TooLarge)
        assertEquals(bytes.size - 1, (failure as DocumentReadException.TooLarge).maxBytes)
    }
}
