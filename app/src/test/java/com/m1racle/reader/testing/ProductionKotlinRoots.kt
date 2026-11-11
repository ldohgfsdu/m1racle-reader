package com.m1racle.reader.testing

import java.io.File

/**
 * Single list of production Kotlin roots for scans.
 * App is included, and so is every core module, so a moved file stays visible.
 */
object ProductionKotlinRoots {
    fun directories(): List<File> {
        val roots = listOf(
            ProductionSource.javaRoot(),
            File(ProductionSource.repositoryRoot(), "core/model/src/main/java"),
            File(ProductionSource.repositoryRoot(), "core/database/src/main/java"),
            File(ProductionSource.repositoryRoot(), "core/pdf/src/main/java"),
        )
        val missing = roots.filterNot(File::isDirectory)
        check(missing.isEmpty()) { "Missing production Kotlin roots: $missing" }
        return roots.distinctBy { it.canonicalPath }
    }

    fun files(): Sequence<File> = directories()
        .asSequence()
        .flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension == "kt" }
        }
        .distinctBy { it.canonicalPath }

    fun owningRoot(file: File): File {
        val canonical = file.canonicalFile
        return directories().first { directory ->
            val base = directory.canonicalFile
            canonical.path == base.path || canonical.path.startsWith(base.path + File.separator)
        }
    }

    fun relativePath(file: File): String =
        file.relativeTo(owningRoot(file)).invariantSeparatorsPath
}
