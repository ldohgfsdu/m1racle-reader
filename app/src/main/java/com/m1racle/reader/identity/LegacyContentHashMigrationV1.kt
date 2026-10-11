package com.m1racle.reader.identity

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import com.m1racle.reader.data.local.DocumentAliasDao
import com.m1racle.reader.data.local.DocumentDao
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One-time migration that retires the legacy null-hash runtime paths.
 *
 * v12 Path-A rows were migrated with a null content hash and used to be
 * reclaimed at import time by re-hashing every null-hash row on each miss
 * (a permanent runtime bridge, now removed). This migration runs once:
 *
 * - every null-hash row whose stored URI is still readable is promoted to a
 *   full content identity in place (id, reading position, and lastOpenedAt
 *   are preserved — promotion never reorders the library);
 * - rows whose source is unreadable, or whose computed hash is already owned
 *   by another row, keep their null hash. They are frozen historical rows:
 *   the runtime no longer treats a null hash as a reuse candidate, so they
 *   can never claim incoming bytes;
 * - all `legacy-raw-uri-v1` aliases (created by the v13 schema migration as a
 *   lookup bridge) are deleted; new imports only ever bind `uri-v1` aliases.
 *
 * A terminal marker file makes the migration run exactly once. Any failure
 * leaves no marker, and a later launch retries; every step is idempotent.
 */
class LegacyContentHashMigrationV1(
    private val documentDao: DocumentDao,
    private val aliasDao: DocumentAliasDao,
    private val files: LegacyContentHashMigrationFiles,
    private val openSource: (Uri) -> InputStream?,
) {
    private val mutex = Mutex()

    @Volatile
    private var completed: LegacyContentHashMigrationStats? = null

    suspend fun ensureCompleted(): LegacyContentHashMigrationStats = withContext(Dispatchers.IO) {
        completed?.let { return@withContext it }
        mutex.withLock {
            completed?.let { return@withLock it }
            val stats = runMigration()
            completed = stats
            stats
        }
    }

    private suspend fun runMigration(): LegacyContentHashMigrationStats {
        files.readMarker()?.let { return it }

        var promoted = 0
        var unreadable = 0
        var conflicts = 0
        for (document in documentDao.getAll()) {
            if (document.contentSha256 != null) continue
            val hash = hashSource(document.uri)
            if (hash == null) {
                unreadable += 1
                continue
            }
            try {
                // lastOpenedAt is passed through unchanged: promotion is an
                // identity repair, not a "recently opened" event.
                if (documentDao.assignContentSha256IfNull(document.id, hash, document.lastOpenedAt) == 1) {
                    promoted += 1
                }
            } catch (_: SQLiteConstraintException) {
                // The hash is already owned by another row; merging the two
                // library entries is out of scope, so this row stays frozen.
                conflicts += 1
            }
        }
        val removedAliases = aliasDao.deleteByKind(DocumentAliasKeys.LEGACY_RAW_URI_V1_KIND)

        return LegacyContentHashMigrationStats(
            promoted = promoted,
            unreadable = unreadable,
            conflicts = conflicts,
            removedLegacyAliases = removedAliases,
            completedAt = System.currentTimeMillis(),
        ).also(files::writeMarker)
    }

    private fun hashSource(uriString: String): String? {
        return try {
            val input = openSource(Uri.parse(uriString)) ?: return null
            val digest = MessageDigest.getInstance(SHA_256)
            input.use { source ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("legacy content hash migration interrupted")
                    }
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val SHA_256 = "SHA-256"
        private const val BUFFER_BYTES = 64 * 1024
    }
}

data class LegacyContentHashMigrationStats(
    val promoted: Int,
    val unreadable: Int,
    val conflicts: Int,
    val removedLegacyAliases: Int,
    val completedAt: Long,
)

/** Fixed marker path and atomic terminal-state writes for the one-time migration. */
class LegacyContentHashMigrationFiles(
    filesDir: File,
) {
    private val markerFile: File = File(filesDir, MARKER_RELATIVE_PATH)

    fun hasMarker(): Boolean = markerFile.isFile

    fun readMarker(): LegacyContentHashMigrationStats? {
        if (!markerFile.isFile) return null
        return try {
            val root = JSONObject(markerFile.readText())
            LegacyContentHashMigrationStats(
                promoted = root.getInt("promoted"),
                unreadable = root.getInt("unreadable"),
                conflicts = root.getInt("conflicts"),
                removedLegacyAliases = root.getInt("removedLegacyAliases"),
                completedAt = root.getLong("completedAt"),
            )
        } catch (_: Exception) {
            // A corrupt marker is not a terminal state: rerun the idempotent
            // migration and overwrite it.
            null
        }
    }

    fun writeMarker(stats: LegacyContentHashMigrationStats) {
        val parent = markerFile.parentFile
        check(parent != null && (parent.mkdirs() || parent.isDirectory)) {
            "cannot create legacy content hash marker directory"
        }
        val payload = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("promoted", stats.promoted)
            .put("unreadable", stats.unreadable)
            .put("conflicts", stats.conflicts)
            .put("removedLegacyAliases", stats.removedLegacyAliases)
            .put("completedAt", stats.completedAt)
            .toString()
        val temp = File(parent, "${markerFile.name}.tmp")
        try {
            temp.writeText(payload)
            try {
                Files.move(
                    temp.toPath(),
                    markerFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                if (!temp.renameTo(markerFile)) {
                    throw IOException("atomic legacy content hash marker write is unavailable")
                }
            }
        } finally {
            temp.delete()
        }
        check(markerFile.isFile) { "legacy content hash marker write did not complete" }
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val MARKER_RELATIVE_PATH = "library_index/legacy_content_hash.migrated-v1.json"
    }
}
