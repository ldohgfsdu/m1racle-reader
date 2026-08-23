package com.markleaf.app.pdf.export

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

/**
 * P0-2 regression: blocking export phases (zip assembly, destination copy)
 * must observe cancellation per chunk, rethrow CancellationException, and
 * never keep writing the user-selected destination after cancel.
 */
class ExportCancellationIoTest {

    private class CancellingAfterReads(
        private val data: ByteArray,
        private val cancelOnRead: Int,
        private val token: ExportCancellationToken,
    ) : InputStream() {
        private var reads = 0
        private var pos = 0

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            reads += 1
            if (reads >= cancelOnRead) token.cancel()
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos)
            System.arraycopy(data, pos, b, off, n)
            pos += n
            return n
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    private fun newDest(name: String): File =
        File(createTempDirectory().toFile(), name)

    @Test
    fun `copy stops right after the chunk whose read cancelled the token`() {
        val token = ExportCancellationToken()
        val total = 200_000
        val src = CancellingAfterReads(ByteArray(total) { (it % 251).toByte() }, cancelOnRead = 2, token)
        val dest = newDest("dest.bin")

        try {
            FileOutputStream(dest).use { dst ->
                copyWithExportCancellation(src, dst, token)
                fail("expected CancellationException")
            }
        } catch (_: CancellationException) {
            // expected
        }

        // Checkpoints run per chunk BEFORE each read: chunk 1 wrote cleanly,
        // chunk 2's read fired the token and still completed its write, and
        // chunk 3's checkpoint threw. Partial output only — never complete.
        assertEquals(EXPORT_COPY_CHUNK_BYTES.toLong() * 2, dest.length())
    }

    @Test
    fun `pre-cancelled token writes nothing`() {
        val token = ExportCancellationToken().apply { cancel() }
        val dest = newDest("dest.bin")

        try {
            FileOutputStream(dest).use { dst ->
                copyWithExportCancellation(ByteArrayInputStream(ByteArray(1000)), dst, token)
                fail("expected CancellationException")
            }
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(0L, dest.length())
    }

    @Test
    fun `job-inactive counts as cancelled even with a fresh token`() {
        val dest = newDest("dest.bin")
        try {
            FileOutputStream(dest).use { dst ->
                copyWithExportCancellation(
                    ByteArrayInputStream(ByteArray(1000)),
                    dst,
                    ExportCancellationToken(),
                    jobActive = { false },
                )
                fail("expected CancellationException")
            }
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(0L, dest.length())
    }

    @Test
    fun `uncancelled copy completes byte-for-byte`() {
        val payload = ByteArray(150_000) { (it % 249).toByte() }
        val dest = newDest("dest.bin")
        FileOutputStream(dest).use { dst ->
            copyWithExportCancellation(ByteArrayInputStream(payload), dst, ExportCancellationToken())
        }
        assertTrue(payload.contentEquals(FileInputStream(dest).use { it.readBytes() }))
    }

    @Test
    fun `zipFiles throws on pre-cancelled token and leaves no complete archive`() {
        val dir = createTempDirectory().toFile()
        val zipTmp = File(dir, "package.zip")
        val source = File(dir, "a.bin").apply { writeBytes(ByteArray(50_000)) }

        try {
            ChapterPackageExporter.zipFiles(
                zipTmp,
                listOf(source to "source.pdf"),
                ExportCancellationToken().apply { cancel() },
            ) { true }
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        val entriesOrError = runCatching {
            ZipFile(zipTmp).use { it.entries().asSequence().count() }
        }.getOrDefault(-1)
        assertTrue("archive must be absent or incomplete", entriesOrError <= 0)
    }

    @Test
    fun `zipFiles stops at a checkpoint once the job goes inactive mid-run`() {
        val dir = createTempDirectory().toFile()
        val zipTmp = File(dir, "package.zip")
        val entryA = File(dir, "a.bin").apply { writeBytes(ByteArray(80_000)) }
        val entryB = File(dir, "b.bin").apply { writeBytes(ByteArray(80_000)) }
        val token = ExportCancellationToken()

        var checkpoints = 0
        val jobActiveAfterTwo = { ++checkpoints <= 2 }

        try {
            ChapterPackageExporter.zipFiles(
                zipTmp,
                listOf(entryA to "a.bin", entryB to "b.bin"),
                token,
                jobActiveAfterTwo,
            )
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }

        val entriesOrError = runCatching {
            ZipFile(zipTmp).use { it.entries().asSequence().count() }
        }.getOrDefault(-1)
        assertTrue(
            "second entry must never have been written (entries=$entriesOrError)",
            entriesOrError < 2,
        )
    }
}
