package com.m1racle.reader.documents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

object DocumentDiscovery {
    suspend fun scan(root: File): List<File> = withContext(Dispatchers.IO) {
        val base = root.canonicalFile
        val found = mutableListOf<File>()
        val visited = mutableSetOf<String>()
        val formats = setOf("pdf", "md", "markdown", "txt", "epub")
        base.walkTopDown().onEnter { dir ->
            val canonical = dir.canonicalFile
            canonical.toPath().startsWith(base.toPath()) && dir.name != "Android" && !dir.name.startsWith('.') && visited.add(canonical.path)
        }.forEach { file ->
            coroutineContext.ensureActive()
            if (file.isFile && file.extension.lowercase() in formats &&
                file.canonicalFile.toPath().startsWith(base.toPath())) found += file
        }
        found.sortedBy { it.name.lowercase() }
    }
}
