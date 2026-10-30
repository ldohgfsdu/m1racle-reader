package com.m1racle.reader.documents

import com.m1racle.reader.pdf.export.ExportCancellationToken
import com.m1racle.reader.pdf.export.copyWithExportCancellation
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Owns only the new destination returned by CreateDocument, never an input document. */
internal class DocumentOutputSave(
    private val openSource: () -> InputStream?,
    private val openOutput: () -> OutputStream?,
    private val openVerification: () -> InputStream?,
    private val deleteDestination: () -> Boolean,
    private val expectedSha256: String? = null,
) {
    @Volatile var committed = false
        private set
    @Volatile var partialOutputRemains = false
        private set
    private var cleanupAttempted = false

    /** Also covers cancellation before the IO dispatcher starts [write]. */
    fun discardUncommitted() {
        if (committed || cleanupAttempted) return
        cleanupAttempted = true
        partialOutputRemains = !runCatching(deleteDestination).getOrDefault(false)
    }

    suspend fun write() {
        val context = currentCoroutineContext()
        try {
            context.ensureActive()
            var length = 0L
            val expected = MessageDigest.getInstance("SHA-256")
            openSource()?.use { input ->
                openOutput()?.use { output ->
                    val checked = object : OutputStream() {
                        override fun write(value: Int) {
                            output.write(value); expected.update(value.toByte()); length++
                        }
                        override fun write(bytes: ByteArray, offset: Int, count: Int) {
                            context.ensureActive()
                            output.write(bytes, offset, count)
                            expected.update(bytes, offset, count); length += count
                        }
                    }
                    copyWithExportCancellation(input, checked, ExportCancellationToken()) { context.isActive }
                } ?: throw IOException("无法写入目标文件")
            } ?: throw IOException("无法读取文件")
            context.ensureActive()
            val copiedHash = expected.digest()
            check(expectedSha256 == null || copiedHash.joinToString("") { "%02x".format(it) } == expectedSha256) {
                "源文件在导出时发生变化，未完成导出"
            }
            val actual = MessageDigest.getInstance("SHA-256")
            openVerification()?.use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                while (true) {
                    context.ensureActive()
                    val read = input.read(buffer)
                    context.ensureActive()
                    if (read < 0) break
                    bytes += read
                    check(bytes <= length) { "保存核验失败" }
                    actual.update(buffer, 0, read)
                }
            } ?: throw IOException("无法核验目标文件")
            check(copiedHash.contentEquals(actual.digest())) { "保存核验失败" }
            context.ensureActive()
            // This flag survives cancellation while withContext dispatches its result back to UI.
            committed = true
        } catch (failure: Exception) {
            discardUncommitted()
            throw failure
        }
    }
}
