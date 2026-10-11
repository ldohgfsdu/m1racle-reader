package com.m1racle.reader.data.settings

import android.content.Context
import android.graphics.fonts.Font
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ReaderFont(val id: String, val name: String, val file: File)

/** Content-addressed private copies; the source URI is never needed after import. */
class ReaderFontRepository(private val context: Context, scope: CoroutineScope) {
    private val directory = File(context.filesDir, "reader-fonts")
    private val mutex = Mutex()
    private val mutableFonts = MutableStateFlow<List<ReaderFont>?>(null)
    val fonts = mutableFonts.asStateFlow()

    init { scope.launch { mutex.withLock { reload() } } }

    private fun reload() {
        mutableFonts.value = directory.listFiles().orEmpty().filter { it.extension == "json" }
            .mapNotNull { metadata ->
                // A partial or damaged entry does not authorize deleting any existing font.
                runCatching {
                    val entry = JSONObject(AtomicFile(metadata).readFully().decodeToString())
                    val id = metadata.nameWithoutExtension
                    val extension = entry.getString("extension")
                    require(id.matches(Regex("[0-9a-f]{64}")) && extension in listOf("ttf", "otf"))
                    ReaderFont(id, entry.getString("name"), File(directory, "$id.$extension"))
                        .takeIf { it.file.isFile }
                }.getOrNull()
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun import(uri: Uri): ReaderFont = withContext(Dispatchers.IO) { mutex.withLock {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法保存字体")
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringBeforeLast('.')?.filter { !it.isISOControl() }?.take(80)?.takeIf { it.isNotBlank() }
            ?: "导入字体"
        val temporary = File.createTempFile("font-", ".import", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("字体超过 50 MB")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("无法读取字体文件")
            if (temporary.length() < 4) throw IOException("请选择有效的 TTF 或 OTF 字体")
            val signature = java.io.DataInputStream(temporary.inputStream()).use { input ->
                ByteArray(4).also(input::readFully).toList()
            }
            val extension = when (signature) {
                listOf<Byte>(0, 1, 0, 0) -> "ttf"
                "OTTO".toByteArray().toList() -> "otf"
                else -> throw IOException("请选择有效的 TTF 或 OTF 字体")
            }
            try { Font.Builder(temporary).build() } catch (failure: Exception) {
                throw IOException("字体文件损坏或无法使用", failure)
            }
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            reload()
            mutableFonts.value.orEmpty().firstOrNull { it.id == id }?.let { return@withLock it }
            val file = File(directory, "$id.$extension")
            if (!temporary.renameTo(file)) throw IOException("无法保存字体")
            val metadata = AtomicFile(File(directory, "$id.json"))
            val stream = metadata.startWrite()
            try {
                stream.write(JSONObject().put("name", name).put("extension", extension).toString().toByteArray())
                metadata.finishWrite(stream)
            } catch (failure: Exception) {
                metadata.failWrite(stream)
                file.delete()
                throw failure
            }
            reload()
            ReaderFont(id, name, file)
        } finally { temporary.delete() }
    } }

    suspend fun remove(font: ReaderFont, settings: ReaderSettingsRepository) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Clear a selected ID before removing its resource, so reflow has a valid fallback.
            settings.clearImportedFont(font.id)
            if (font.file.exists() && !font.file.delete()) throw IOException("无法移除字体")
            AtomicFile(File(directory, "${font.id}.json")).delete()
            reload()
        }
    }

    companion object { const val MAX_BYTES = 50L * 1024 * 1024 }
}
