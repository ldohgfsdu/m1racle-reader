package com.m1racle.reader.documents

import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

class MarkdownDraftStore(root: File, id: String) {
    data class Draft(val baseHash: String, val text: String)
    private val key = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(root, "markdown-drafts/$key.json"))
    @Synchronized fun read(): Draft? {
        val input = try { file.openRead() } catch (_: java.io.FileNotFoundException) { return null }
        val json = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        return Draft(json.getString("baseHash"), json.getString("text"))
    }
    @Synchronized fun write(draft: Draft) {
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try {
            stream.write(JSONObject().put("baseHash", draft.baseHash).put("text", draft.text).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized fun delete() { file.delete() }
}
