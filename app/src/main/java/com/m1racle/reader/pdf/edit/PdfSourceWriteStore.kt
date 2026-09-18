package com.m1racle.reader.pdf.edit

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

/** Durable rollback records for providers that cannot atomically replace a document. */
internal class PdfSourceWriteStore(
    private val root: File,
    private val read: (String) -> InputStream,
    private val write: (String) -> OutputStream,
) {
    data class Ticket(val directory: File, val documentId: String, val uri: String, val before: String, val after: String, val candidate: File) {
        val backup: File get() = File(directory, "original.pdf")
    }

    fun prepare(documentId: String, uri: String, expected: String, candidate: File): Ticket {
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs()) { "无法创建源文件备份" }
        try {
            val backup = File(directory,"original.pdf")
            read(uri).use { input -> FileOutputStream(backup).use { out ->
                copyBounded(input,out); out.fd.sync()
            } }
            if (sha256(backup) != expected) throw IOException("源文件已被修改，或选择的不是原文件；未覆盖。请重新打开源文件后编辑。")
            val ticket = Ticket(directory,documentId,uri,expected,sha256(candidate),candidate)
            val json = JSONObject().put("documentId",documentId).put("uri",uri).put("before",expected)
                .put("after",ticket.after).put("candidate",candidate.absolutePath)
            val atomic = AtomicFile(File(directory,"record.json"))
            val out = atomic.startWrite()
            try { out.write(json.toString().toByteArray()); atomic.finishWrite(out) }
            catch (e: Exception) { atomic.failWrite(out); throw e }
            return ticket
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }

    fun replace(ticket: Ticket, onWriting: () -> Unit) {
        // Check again immediately before truncating the external document.
        if (read(ticket.uri).use(::sha256) != ticket.before) throw IOException("源文件在保存前发生变化，未覆盖")
        onWriting()
        write(ticket.uri).use { output -> ticket.candidate.inputStream().use { it.copyTo(output) } }
        if (read(ticket.uri).use(::sha256) != ticket.after) throw IOException("源文件写入核验失败")
    }

    /** Used after our own failed write. Backup stays durable unless restore is verified. */
    fun restore(ticket: Ticket) {
        write(ticket.uri).use { output -> ticket.backup.inputStream().use { it.copyTo(output) } }
        if (read(ticket.uri).use(::sha256) != ticket.before) throw IOException("源文件恢复失败，备份已保留")
    }

    fun pending(): List<Ticket> = root.listFiles().orEmpty().mapNotNull { directory ->
        val record = File(directory,"record.json")
        if (!record.exists()) return@mapNotNull null
        val json = JSONObject(AtomicFile(record).readFully().toString(Charsets.UTF_8))
        Ticket(directory,json.getString("documentId"),json.getString("uri"),json.getString("before"),json.getString("after"),File(json.getString("candidate")))
    }

    /** Do not overwrite unrelated changes made while this process was absent. */
    fun recover(ticket: Ticket, committed: Boolean) {
        if (!committed) {
            val current = read(ticket.uri).use(::sha256)
            if (current != ticket.before) {
                if (current != ticket.after && !isTruncatedCandidate(ticket)) {
                    throw IOException("未完成保存的源文件又发生变化，未自动覆盖；可导出保留的原文件备份")
                }
                restore(ticket)
            }
        }
        finish(ticket)
    }

    private fun isTruncatedCandidate(ticket: Ticket): Boolean {
        if (!ticket.candidate.isFile) return false
        return read(ticket.uri).buffered().use { actual -> ticket.candidate.inputStream().buffered().use expectedUse@ { expected ->
            var count = 0L
            while (true) {
                val a = actual.read()
                if (a < 0) return@expectedUse count < ticket.candidate.length()
                if (a != expected.read()) return@expectedUse false
                count++
            }
            @Suppress("UNREACHABLE_CODE") false
        } }
    }

    fun finish(ticket: Ticket) { ticket.directory.deleteRecursively() }

    companion object {
        fun sha256(file: File): String = file.inputStream().use(::sha256)
        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val count=input.read(buffer); if(count<0) break; digest.update(buffer,0,count) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun copyBounded(input: InputStream, output: OutputStream) {
            val buffer=ByteArray(8192); var total=0L
            while(true) {
                val count=input.read(buffer); if(count<0) break
                total+=count; if(total>512L*1024*1024) throw IOException("源文件过大，未覆盖")
                output.write(buffer,0,count)
            }
        }
    }
}
