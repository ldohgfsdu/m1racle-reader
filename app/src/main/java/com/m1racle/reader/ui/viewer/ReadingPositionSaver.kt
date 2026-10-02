package com.m1racle.reader.ui.viewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serial, latest-wins position writes; only a successful write counts as persisted. */
class ReadingPositionSaver(
    private val scope: CoroutineScope,
    private val write: suspend (lastScrollIndex: Int, lastScrollOffset: Int, readingProgress: Float) -> Unit,
) {
    private val stateLock = Any()
    private val writer = Mutex()
    private var saveJob: Job? = null
    private var lastKnown: Triple<Int, Int, Float>? = null
    private var version = 0L
    private var persistedVersion = -1L
    private var flushingVersion: Long? = null

    fun save(lastScrollIndex: Int, lastScrollOffset: Int, readingProgress: Float) {
        synchronized(stateLock) {
            lastKnown = Triple(lastScrollIndex, lastScrollOffset, readingProgress)
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
                write(position.first, position.second, position.third)
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
