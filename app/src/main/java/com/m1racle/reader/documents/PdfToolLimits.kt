package com.m1racle.reader.documents

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal data class PdfToolLimits(
    val maxInputs: Int = 20,
    val maxSingleInputBytes: Long = 256L * 1024 * 1024,
    val maxTotalInputBytes: Long = 512L * 1024 * 1024,
    val maxPagesPerInput: Int = 5_000,
    val maxTotalPages: Int = 5_000,
) {
    init {
        require(maxInputs > 0)
        require(maxSingleInputBytes > 0)
        require(maxTotalInputBytes > 0)
        require(maxPagesPerInput > 0)
        require(maxTotalPages > 0)
    }
}

/**
 * Copies a provider stream into app-owned staging while enforcing byte limits.
 * Metadata is deliberately not trusted because some providers cannot report size.
 */
internal fun copyPdfInputBounded(
    input: InputStream,
    output: OutputStream,
    limits: PdfToolLimits,
    alreadyStagedBytes: Long = 0L,
    checkpoint: () -> Unit = {},
): Long {
    require(alreadyStagedBytes >= 0L)
    val buffer = ByteArray(64 * 1024)
    var copied = 0L
    while (true) {
        checkpoint()
        val singleRemaining = limits.maxSingleInputBytes - copied
        val totalRemaining = limits.maxTotalInputBytes - alreadyStagedBytes - copied
        if (singleRemaining < 0L) throw inputTooLarge(limits.maxSingleInputBytes)
        if (totalRemaining < 0L) throw totalTooLarge(limits.maxTotalInputBytes)
        val request = minOf(buffer.size.toLong(), singleRemaining + 1L, totalRemaining + 1L).toInt()
        val count = input.read(buffer, 0, request)
        if (count < 0) return copied
        if (count == 0) throw IOException("PDF输入流未返回数据")
        copied += count
        if (copied > limits.maxSingleInputBytes) throw inputTooLarge(limits.maxSingleInputBytes)
        if (alreadyStagedBytes + copied > limits.maxTotalInputBytes) {
            throw totalTooLarge(limits.maxTotalInputBytes)
        }
        checkpoint()
        output.write(buffer, 0, count)
    }
}

private fun inputTooLarge(limit: Long) =
    IOException("单个PDF超过 ${formatPdfBytes(limit)} 上限")

private fun totalTooLarge(limit: Long) =
    IOException("所选PDF总大小超过 ${formatPdfBytes(limit)} 上限")

private fun formatPdfBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)} MiB"
    bytes >= 1024L -> "${bytes / 1024L} KiB"
    else -> "$bytes B"
}
