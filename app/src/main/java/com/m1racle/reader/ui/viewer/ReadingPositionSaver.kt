package com.m1racle.reader.ui.viewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Markdown LazyColumn position plus reading progress (0f–1f). */
data class ScrollPosition(val lastScrollIndex: Int, val lastScrollOffset: Int, val readingProgress: Float)

/**
 * Serial, latest-wins position writes; only a successful write counts as persisted.
 * [P] is the reader's complete position value, so every field of one save is
 * written together (EPUB carries its text anchor alongside the legacy fields).
 */
class ReadingPositionSaver<P : Any>(
    private val scope: CoroutineScope,
    private val write: suspend (position: P) -> Unit,
) {
    private val stateLock = Any()
    private val writer = Mutex()
    private var saveJob: Job? = null
    private var lastKnown: P? = null
    private var version = 0L
    private var persistedVersion = -1L
    private var flushingVersion: Long? = null

    fun save(position: P) {
        synchronized(stateLock) {
            lastKnown = position
            val requestedVersion = ++version
            saveJob?.cancel()
            saveJob = scope.launch { persist(requestedVersion) }
        }
    }

    private suspend fun persist(requestedVersion: Long) {
        try {
            writer.withLock {
                val position = synchronized(stateLock) {
                    lastKnown?.takeIf { requestedVersion == version && persistedVersion != requestedVersion }
                } ?: return@withLock
                write(position)
                synchronized(stateLock) { persistedVersion = requestedVersion }
            }
        } catch (_: Exception) {
            // The value remains dirty so final flush can retry a failed save.
        }
    }

    /** Coalesce lifecycle/disposal calls, and serialize final writes with ordinary saves. */
    fun flushFinal(flushScope: CoroutineScope = CoroutineScope(NonCancellable + Dispatchers.IO)) {
        synchronized(stateLock) {
            if (lastKnown == null || persistedVersion == version || flushingVersion == version) return
            val requestedVersion = version
            flushingVersion = requestedVersion
            flushScope.launch {
                try {
                    persist(requestedVersion)
                } finally {
                    synchronized(stateLock) {
                        if (flushingVersion == requestedVersion) flushingVersion = null
                    }
                }
            }
        }
    }
}
