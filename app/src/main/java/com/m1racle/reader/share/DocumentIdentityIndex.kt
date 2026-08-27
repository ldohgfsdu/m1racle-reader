package com.m1racle.reader.share

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Rebuildable acceleration index for external-open identity.
 *
 * Authority: Room rows + private library files.
 * This index is never the sole source of truth and may be discarded/rebuilt.
 */
class DocumentIdentityIndex(
    private val context: Context,
    private val indexFileName: String = "identity_index_v1.json",
) {
    data class Entry(
        val documentId: String,
        val canonicalUriIdentity: String?,
        val sha256: String,
        val sizeBytes: Long,
        val mimeType: String?,
    )

    companion object {
        const val FORMAT_VERSION = 1
    }

    private val dir: File
        get() = File(context.filesDir, "library_index").also { if (!it.exists()) it.mkdirs() }

    private val indexFile: File get() = File(dir, indexFileName)
    private val corruptDir: File
        get() = File(dir, "corrupt").also { if (!it.exists()) it.mkdirs() }

    @Synchronized
    fun findByExternalKey(key: String): Entry? {
        val entries = loadEntries()
        return entries.firstOrNull { it.canonicalUriIdentity == key }
    }

    @Synchronized
    fun findByContentSha(sha256: String, sizeBytes: Long? = null): Entry? {
        val sha = sha256.lowercase(Locale.ROOT)
        return loadEntries().firstOrNull { e ->
            e.sha256 == sha && (sizeBytes == null || e.sizeBytes == sizeBytes)
        }
    }

    @Synchronized
    fun findByDocumentId(documentId: String): Entry? =
        loadEntries().firstOrNull { it.documentId == documentId }

    /**
     * Bind after Room write succeeded. Atomic write; failure does not leave partial durable state
     * that would invent phantom documents (Room remains authority).
     */
    @Synchronized
    fun bind(
        documentId: String,
        canonicalUriIdentity: String?,
        sha256: String,
        sizeBytes: Long,
        mimeType: String?,
    ): Boolean {
        val sha = sha256.lowercase(Locale.ROOT)
        val entries = loadEntries().toMutableList()
        entries.removeAll {
            it.documentId == documentId ||
                (canonicalUriIdentity != null && it.canonicalUriIdentity == canonicalUriIdentity) ||
                (it.sha256 == sha && it.sizeBytes == sizeBytes)
        }
        entries.add(
            Entry(
                documentId = documentId,
                canonicalUriIdentity = canonicalUriIdentity,
                sha256 = sha,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
            ),
        )
        return writeEntriesAtomic(entries)
    }

    @Synchronized
    fun removeByDocumentId(documentId: String): Boolean {
        val entries = loadEntries().toMutableList()
        val changed = entries.removeAll { it.documentId == documentId }
        if (!changed) return true
        return writeEntriesAtomic(entries)
    }

    /**
     * Drop index rows whose documentId is not present in [existingDocumentIds].
     */
    @Synchronized
    fun pruneMissingDocuments(existingDocumentIds: Set<String>): Int {
        val entries = loadEntries()
        val kept = entries.filter { it.documentId in existingDocumentIds }
        val removed = entries.size - kept.size
        if (removed > 0) writeEntriesAtomic(kept)
        return removed
    }

    /**
     * Rebuild entries from Room-backed documents that use content ids (`c:<sha>`).
     */
    @Synchronized
    fun rebuildFromDocuments(
        documents: List<Triple<String, Long?, String?>>,
        /* documentId, sizeBytes, mimeType */
        shaFromId: (String) -> String?,
    ): Int {
        val rebuilt = documents.mapNotNull { (id, size, mime) ->
            val sha = shaFromId(id) ?: return@mapNotNull null
            Entry(
                documentId = id,
                canonicalUriIdentity = null,
                sha256 = sha.lowercase(Locale.ROOT),
                sizeBytes = size ?: -1L,
                mimeType = mime,
            )
        }
        writeEntriesAtomic(rebuilt)
        return rebuilt.size
    }

    @Synchronized
    fun loadEntries(): List<Entry> {
        if (!indexFile.isFile) return emptyList()
        return try {
            val text = indexFile.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            val version = root.optInt("formatVersion", 0)
            if (version != FORMAT_VERSION) {
                quarantineCorrupt("version_mismatch_$version")
                return emptyList()
            }
            val arr = root.optJSONArray("entries") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("documentId").takeIf { it.isNotBlank() } ?: continue
                    val sha = o.optString("sha256").takeIf { it.isNotBlank() } ?: continue
                    add(
                        Entry(
                            documentId = id,
                            canonicalUriIdentity = o.optString("canonicalUriIdentity", null)
                                ?.takeIf { it.isNotBlank() },
                            sha256 = sha.lowercase(Locale.ROOT),
                            sizeBytes = o.optLong("sizeBytes", -1L),
                            mimeType = o.optString("mimeType", null)?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            quarantineCorrupt("parse_error_${System.currentTimeMillis()}")
            emptyList()
        }
    }

    private fun quarantineCorrupt(tag: String) {
        try {
            if (indexFile.isFile) {
                val dest = File(corruptDir, "${tag}_${indexFile.name}")
                indexFile.copyTo(dest, overwrite = true)
                indexFile.delete()
            }
        } catch (_: Exception) {
            try {
                indexFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeEntriesAtomic(entries: List<Entry>): Boolean {
        return try {
            val root = JSONObject()
            root.put("formatVersion", FORMAT_VERSION)
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("documentId", e.documentId)
                        if (e.canonicalUriIdentity != null) {
                            put("canonicalUriIdentity", e.canonicalUriIdentity)
                        } else {
                            put("canonicalUriIdentity", JSONObject.NULL)
                        }
                        put("sha256", e.sha256)
                        put("sizeBytes", e.sizeBytes)
                        if (e.mimeType != null) put("mimeType", e.mimeType) else put("mimeType", JSONObject.NULL)
                    },
                )
            }
            root.put("entries", arr)
            val tmp = File(dir, "$indexFileName.tmp")
            FileOutputStream(tmp).use { fos ->
                fos.write(root.toString().toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            if (indexFile.exists() && !indexFile.delete()) {
                // fall through to rename overwrite attempt
            }
            if (!tmp.renameTo(indexFile)) {
                tmp.copyTo(indexFile, overwrite = true)
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
