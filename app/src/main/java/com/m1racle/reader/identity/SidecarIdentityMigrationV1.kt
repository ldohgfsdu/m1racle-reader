package com.m1racle.reader.identity

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.m1racle.reader.data.local.DocumentAliasDao
import com.m1racle.reader.data.local.DocumentAliasEntity
import com.m1racle.reader.data.local.DocumentDao
import com.m1racle.reader.data.local.DocumentEntity
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Pure key formatting shared by the one-time migration and the v13 import path. */
object DocumentAliasKeys {
    const val URI_V1_KIND: String = "uri-v1"
    const val LEGACY_RAW_URI_V1_KIND: String = "legacy-raw-uri-v1"

    fun uriV1(canonicalExternalKey: String): String =
        "$URI_V1_KIND:${sha256(canonicalExternalKey.toByteArray(Charsets.UTF_8))}"

    fun legacyRawUriV1(rawUri: String): String =
        "$LEGACY_RAW_URI_V1_KIND:${sha256(rawUri.toByteArray(Charsets.UTF_8))}"

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

data class SidecarIdentityEntryV1(
    val documentId: String,
    val canonicalUriIdentity: String?,
    val sha256: String?,
)

sealed interface SidecarIdentityReadResult {
    data object Missing : SidecarIdentityReadResult

    data class Valid(
        val sourceSha256: String,
        val entries: List<SidecarIdentityEntryV1>,
        val invalidEntryCount: Int,
    ) : SidecarIdentityReadResult

    data class Corrupt(
        val sourceSha256: String,
        val reason: String,
    ) : SidecarIdentityReadResult

    data class UnknownVersion(
        val sourceSha256: String,
        val version: Int?,
    ) : SidecarIdentityReadResult
}

/**
 * Strict, read-only parser for the historical v1 sidecar.
 *
 * Unlike the historical mutable loader, this reader never renames,
 * quarantines, rewrites, or deletes the source file.
 */
class SidecarIdentityV1Reader(
    private val maximumBytes: Int = DEFAULT_MAXIMUM_BYTES,
) {
    init {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
    }

    fun read(source: File): SidecarIdentityReadResult {
        if (!source.isFile) return SidecarIdentityReadResult.Missing
        val length = source.length()
        if (length > maximumBytes) {
            return SidecarIdentityReadResult.Corrupt(
                sourceSha256 = digestFile(source),
                reason = "over_limit",
            )
        }

        val bytes = source.readBytes()
        val digest = DocumentAliasKeys.sha256(bytes)
        if (bytes.isEmpty() || bytes.all { it.toInt().toChar().isWhitespace() }) {
            return SidecarIdentityReadResult.Corrupt(digest, "empty_input")
        }

        return try {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val version = root.opt("formatVersion")
            val versionNumber = (version as? Number)?.toInt()
            if (versionNumber != FORMAT_VERSION) {
                SidecarIdentityReadResult.UnknownVersion(digest, versionNumber)
            } else {
                val entriesValue = root.opt("entries")
                if (entriesValue !is JSONArray) {
                    SidecarIdentityReadResult.Corrupt(digest, "entries_not_array")
                } else {
                    parseEntries(entriesValue, digest)
                }
            }
        } catch (_: Exception) {
            SidecarIdentityReadResult.Corrupt(digest, "parse_error")
        }
    }

    private fun parseEntries(
        array: JSONArray,
        digest: String,
    ): SidecarIdentityReadResult.Valid {
        val entries = ArrayList<SidecarIdentityEntryV1>(array.length())
        var invalid = 0
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            if (item !is JSONObject) {
                invalid += 1
                continue
            }
            val documentId = item.stringOrNull("documentId")
            if (documentId.isNullOrBlank()) {
                invalid += 1
                continue
            }
            entries += SidecarIdentityEntryV1(
                documentId = documentId,
                canonicalUriIdentity = item.stringOrNull("canonicalUriIdentity"),
                sha256 = item.stringOrNull("sha256")?.lowercase(Locale.ROOT),
            )
        }
        return SidecarIdentityReadResult.Valid(
            sourceSha256 = digest,
            entries = entries,
            invalidEntryCount = invalid,
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getString(key) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun digestFile(source: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
        const val DEFAULT_MAXIMUM_BYTES: Int = 16 * 1024 * 1024
    }
}

data class SidecarAliasMigrationStats(
    val readEntries: Int,
    val insertedAliases: Int,
    val sameDocumentAliases: Int,
    val conflicts: Int,
    val orphanDocuments: Int,
    val invalidEntries: Int,
)

fun interface SidecarAliasMigrationTransaction {
    suspend fun migrate(
        entries: List<SidecarIdentityEntryV1>,
        readerInvalidEntryCount: Int,
    ): SidecarAliasMigrationStats
}

/** Room-only transaction. It has no filesystem or content resolver dependency. */
class RoomSidecarAliasMigrationTransaction(
    private val database: RoomDatabase,
    private val documentDao: DocumentDao,
    private val aliasDao: DocumentAliasDao,
) : SidecarAliasMigrationTransaction {
    override suspend fun migrate(
        entries: List<SidecarIdentityEntryV1>,
        readerInvalidEntryCount: Int,
    ): SidecarAliasMigrationStats = database.withTransaction {
        migrateSidecarAliases(
            entries = entries,
            readerInvalidEntryCount = readerInvalidEntryCount,
            store = object : SidecarAliasMigrationStore {
                override suspend fun getDocument(id: String): DocumentEntity? = documentDao.getById(id)
                override suspend fun getAlias(key: String): DocumentAliasEntity? = aliasDao.getByKey(key)
                override suspend fun insertAlias(alias: DocumentAliasEntity) {
                    aliasDao.insertAlias(alias)
                }
            },
        )
    }
}

/** Minimal transaction-scoped store; production adapts Room DAOs, tests use a fake. */
interface SidecarAliasMigrationStore {
    suspend fun getDocument(id: String): DocumentEntity?
    suspend fun getAlias(key: String): DocumentAliasEntity?
    suspend fun insertAlias(alias: DocumentAliasEntity)
}

internal suspend fun migrateSidecarAliases(
    entries: List<SidecarIdentityEntryV1>,
    readerInvalidEntryCount: Int,
    store: SidecarAliasMigrationStore,
): SidecarAliasMigrationStats {
    var inserted = 0
    var sameDocument = 0
    var conflicts = 0
    var orphans = 0
    var invalid = readerInvalidEntryCount

    for (entry in entries) {
        val canonical = entry.canonicalUriIdentity
        if (canonical.isNullOrBlank()) {
            invalid += 1
            continue
        }
        val document = store.getDocument(entry.documentId)
        if (document == null) {
            orphans += 1
            continue
        }

        val pathA = PATH_A_ID.matches(entry.documentId)
        val pathBSuffix = PATH_B_ID.matchEntire(entry.documentId)?.groupValues?.get(1)
        val eligible = when {
            pathA -> true // Historical Path A sidecar SHA is not content evidence.
            pathBSuffix != null -> entry.sha256 == pathBSuffix
            else -> false
        }
        if (!eligible) {
            invalid += 1
            continue
        }

        val aliasKey = DocumentAliasKeys.uriV1(canonical)
        val existing = store.getAlias(aliasKey)
        when {
            existing == null -> {
                store.insertAlias(
                    DocumentAliasEntity(
                        aliasKey = aliasKey,
                        documentId = document.id,
                        kind = DocumentAliasKeys.URI_V1_KIND,
                        createdAt = document.createdAt,
                        lastSeenAt = maxOf(document.createdAt, document.lastOpenedAt),
                    ),
                )
                inserted += 1
            }
            existing.documentId == document.id -> sameDocument += 1
            else -> conflicts += 1
        }
    }

    return SidecarAliasMigrationStats(
        readEntries = entries.size + readerInvalidEntryCount,
        insertedAliases = inserted,
        sameDocumentAliases = sameDocument,
        conflicts = conflicts,
        orphanDocuments = orphans,
        invalidEntries = invalid,
    )
}

private val PATH_A_ID = Regex("^[0-9a-f]{64}$")
private val PATH_B_ID = Regex("^c:([0-9a-f]{64})$")

sealed interface SidecarMigrationMarkerState {
    data object None : SidecarMigrationMarkerState
    data class Migrated(val artifact: File) : SidecarMigrationMarkerState
    data class Quarantined(val artifact: File) : SidecarMigrationMarkerState
    data class Inconsistent(val artifacts: List<File>) : SidecarMigrationMarkerState
}

/** Fixed paths and atomic lifecycle transitions for the one-time migration. */
class SidecarIdentityMigrationFiles(
    private val filesDir: File,
) {
    val liveFile: File
        get() = File(filesDir, "library_index/identity_index_v1.json")

    fun markerState(): SidecarMigrationMarkerState {
        val migrated = artifacts(retiredDir, RETIRED_PREFIX)
        val quarantined = artifacts(corruptDir, QUARANTINE_PREFIX)
        val markers = migrated + quarantined
        if (liveFile.exists() && markers.isNotEmpty()) {
            return SidecarMigrationMarkerState.Inconsistent(markers)
        }
        if (markers.size > 1) return SidecarMigrationMarkerState.Inconsistent(markers)
        return when {
            migrated.size == 1 -> SidecarMigrationMarkerState.Migrated(migrated.single())
            quarantined.size == 1 -> SidecarMigrationMarkerState.Quarantined(quarantined.single())
            else -> SidecarMigrationMarkerState.None
        }
    }

    fun retire(sourceSha256: String): File = moveLiveTo(
        directory = retiredDir,
        name = "$RETIRED_PREFIX$sourceSha256.json",
    )

    fun quarantine(sourceSha256: String, reason: String): File = moveLiveTo(
        directory = corruptDir,
        name = "$QUARANTINE_PREFIX${sanitizeReason(reason)}.$sourceSha256.json",
    )

    private fun moveLiveTo(directory: File, name: String): File {
        val source = liveFile
        check(source.isFile) { "sidecar source is missing" }
        check(directory.mkdirs() || directory.isDirectory) { "cannot create sidecar destination" }
        val destination = File(directory, name)
        check(!destination.exists()) { "sidecar destination already exists" }
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            if (!source.renameTo(destination)) {
                throw IOException("atomic sidecar retirement is unavailable")
            }
        }
        check(!source.exists() && destination.isFile) { "sidecar move did not complete" }
        return destination
    }

    private fun artifacts(directory: File, prefix: String): List<File> =
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun sanitizeReason(reason: String): String =
        reason.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "_").take(48)

    private val retiredDir: File
        get() = File(filesDir, "library_index/retired")
    private val corruptDir: File
        get() = File(filesDir, "library_index/corrupt")

    companion object {
        private const val RETIRED_PREFIX = "identity_index_v1.migrated-v13."
        private const val QUARANTINE_PREFIX = "identity_index_v1."
    }
}

sealed interface SidecarMigrationOutcome {
    data object NoInput : SidecarMigrationOutcome
    data class Migrated(
        val artifact: File,
        val stats: SidecarAliasMigrationStats?,
    ) : SidecarMigrationOutcome
    data class Quarantined(
        val artifact: File,
        val reason: String,
    ) : SidecarMigrationOutcome
}

/**
 * One-time v1 sidecar migration. Only terminal outcomes are cached; failures
 * leave the live file as the retry trigger and are allowed to throw.
 */
class SidecarIdentityMigrationV1(
    private val reader: SidecarIdentityV1Reader,
    private val files: SidecarIdentityMigrationFiles,
    private val transaction: SidecarAliasMigrationTransaction,
) {
    private val mutex = Mutex()

    @Volatile
    private var completed: SidecarMigrationOutcome? = null

    suspend fun ensureCompleted(): SidecarMigrationOutcome = withContext(Dispatchers.IO) {
        completed?.let { return@withContext it }
        mutex.withLock {
            completed?.let { return@withLock it }
            val outcome = runMigration()
            completed = outcome
            outcome
        }
    }

    private suspend fun runMigration(): SidecarMigrationOutcome {
        when (val marker = files.markerState()) {
            is SidecarMigrationMarkerState.Migrated ->
                return SidecarMigrationOutcome.Migrated(marker.artifact, stats = null)
            is SidecarMigrationMarkerState.Quarantined ->
                return SidecarMigrationOutcome.Quarantined(marker.artifact, "previously_quarantined")
            is SidecarMigrationMarkerState.Inconsistent ->
                throw IllegalStateException("inconsistent sidecar migration markers")
            SidecarMigrationMarkerState.None -> Unit
        }

        return when (val read = reader.read(files.liveFile)) {
            SidecarIdentityReadResult.Missing -> SidecarMigrationOutcome.NoInput
            is SidecarIdentityReadResult.Corrupt -> {
                val artifact = files.quarantine(read.sourceSha256, read.reason)
                SidecarMigrationOutcome.Quarantined(artifact, read.reason)
            }
            is SidecarIdentityReadResult.UnknownVersion -> {
                val reason = "unknown_version_${read.version ?: "missing"}"
                val artifact = files.quarantine(read.sourceSha256, reason)
                SidecarMigrationOutcome.Quarantined(artifact, reason)
            }
            is SidecarIdentityReadResult.Valid -> {
                val stats = transaction.migrate(read.entries, read.invalidEntryCount)
                val artifact = files.retire(read.sourceSha256)
                SidecarMigrationOutcome.Migrated(artifact, stats)
            }
        }
    }
}
