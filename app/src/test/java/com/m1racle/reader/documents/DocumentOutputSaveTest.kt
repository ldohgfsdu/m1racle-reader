package com.m1racle.reader.documents

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentOutputSaveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = ByteArray(192 * 1024) { (it % 251).toByte() }
    private fun source() = temporary.newFile().apply { writeBytes(bytes) }

    @Test fun successCommitsOnlyAfterClosingAndVerifyingDestination() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        var closed = false
        var deleted = false
        val save = DocumentOutputSave({ source.inputStream() }, {
            object : FilterOutputStream(target.outputStream()) {
                override fun close() { super.close(); closed = true }
            }
        }, {
            assertTrue(closed)
            target.inputStream()
        }, { deleted = true; target.delete() })
        save.write()
        assertTrue(save.committed)
        assertFalse(deleted)
        assertArrayEquals(bytes, target.readBytes())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun cancellationStopsAtChunkBoundaryRemovesNewOutputAndKeepsSource() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        var writes = 0
        var verifications = 0
        lateinit var save: DocumentOutputSave
        val job = launch {
            val ownJob = currentCoroutineContext()[Job]!!
            save = DocumentOutputSave({ source.inputStream() }, {
                object : FilterOutputStream(target.outputStream()) {
                    override fun write(buffer: ByteArray, offset: Int, count: Int) {
                        out.write(buffer, offset, count)
                        writes++
                        ownJob.cancel()
                    }
                }
            }, { verifications++; target.inputStream() }, { target.delete() })
            save.write()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, writes)
        assertEquals(0, verifications)
        assertFalse(save.committed)
        assertFalse(save.partialOutputRemains)
        assertFalse(target.exists())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun failedWriteRemovesPartialDestinationAndPreservesRetrySource() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        val save = DocumentOutputSave({ source.inputStream() }, {
            object : FilterOutputStream(target.outputStream()) {
                override fun write(buffer: ByteArray, offset: Int, count: Int) {
                    out.write(buffer, offset, 3)
                    throw IOException("provider write failure")
                }
            }
        }, { target.inputStream() }, { target.delete() })
        assertTrue(runCatching { save.write() }.exceptionOrNull() is IOException)
        assertFalse(save.committed)
        assertFalse(target.exists())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun mismatchedReadbackNeverCommitsAndRemovesOutput() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        val save = DocumentOutputSave({ source.inputStream() }, { target.outputStream() }, {
            ByteArrayInputStream(bytes.copyOf().apply { this[0] = 99 })
        }, { target.delete() })
        assertTrue(runCatching { save.write() }.isFailure)
        assertFalse(save.committed)
        assertFalse(target.exists())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun oversizedVerificationStreamIsRejectedWithoutReadingUnboundedData() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        val save = DocumentOutputSave({ source.inputStream() }, { target.outputStream() }, {
            ByteArrayInputStream(bytes + byteArrayOf(1))
        }, { target.delete() })
        assertTrue(runCatching { save.write() }.isFailure)
        assertFalse(save.committed)
        assertFalse(target.exists())
    }

    @Test fun changingSourceCannotPassItsInitialSnapshotHash() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        val save = DocumentOutputSave({ source.inputStream() }, { target.outputStream() },
            { target.inputStream() }, { target.delete() }, expectedSha256 = "0".repeat(64))
        assertTrue(runCatching { save.write() }.isFailure)
        assertFalse(save.committed)
        assertFalse(target.exists())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun providerRefusingCleanupIsRecordedInsteadOfClaimingCleanCancellation() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        lateinit var save: DocumentOutputSave
        val job = launch {
            val ownJob = currentCoroutineContext()[Job]!!
            save = DocumentOutputSave({ source.inputStream() }, {
                object : FilterOutputStream(target.outputStream()) {
                    override fun write(buffer: ByteArray, offset: Int, count: Int) {
                        out.write(buffer, offset, count); ownJob.cancel()
                    }
                }
            }, { target.inputStream() }, { false })
            save.write()
        }
        job.join()
        assertFalse(save.committed)
        assertTrue(save.partialOutputRemains)
        assertTrue(target.exists())
        assertTrue(target.length() < source.length())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun completedSaveRemainsCommittedWhenItsCallerIsCancelled() = runBlocking {
        val source = source()
        val target = temporary.newFile()
        var deleteCalls = 0
        val save = DocumentOutputSave({ source.inputStream() }, { target.outputStream() },
            { target.inputStream() }, { deleteCalls++; target.delete() })
        val job = launch { save.write(); currentCoroutineContext().cancel() }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(save.committed)
        assertEquals(0, deleteCalls)
        assertArrayEquals(bytes, target.readBytes())
    }

    @Test fun cancellationBeforeIoStartsStillRemovesOnlyTheNewDestinationOnce() {
        val source = source()
        val target = temporary.newFile()
        var deleteCalls = 0
        val save = DocumentOutputSave({ error("must not read") }, { error("must not write") },
            { error("must not verify") }, { deleteCalls++; target.delete() })
        save.discardUncommitted()
        save.discardUncommitted()
        assertEquals(1, deleteCalls)
        assertFalse(target.exists())
        assertFalse(save.committed)
        assertFalse(save.partialOutputRemains)
        assertArrayEquals(bytes, source.readBytes())
    }
}
