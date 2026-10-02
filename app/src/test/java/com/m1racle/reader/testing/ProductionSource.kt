package com.m1racle.reader.testing

import java.io.File

/**
 * Single entry point for tests that assert on production source text or project files.
 *
 * Roots are resolved by walking up from the working directory, so the same
 * assertions hold whether Gradle runs from the repository root or from the app
 * module directory, and no machine-specific absolute path is involved.
 *
 * Prefer [compact] for assertions about a call's shape: it removes whitespace
 * only, so reformatting or line wrapping cannot turn a green contract red, while
 * a changed argument, role, or receiver still fails.
 */
object ProductionSource {

    private val ancestors: List<File> by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { it.canonicalFile }
            .toList()
    }

    /** Directory that owns `app/` — the repository root. */
    fun repositoryRoot(): File = ancestors.firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: ancestors.firstOrNull { File(it, "src/main/java").isDirectory }
        ?: error("Cannot locate app/src/main/java from ${File("").absolutePath}")

    /** Gradle module that owns `src/main/java` — the `app` module, or the repository root itself. */
    fun moduleRoot(): File = ancestors.firstOrNull { File(it, "src/main/java").isDirectory }
        ?: repositoryRoot()

    fun javaRoot(): File = File(moduleRoot(), "src/main/java")

    /** [relative] is resolved against the java root, e.g. `com/m1racle/reader/ui/hub/GlassDock.kt`. */
    fun file(relative: String): File = resolve(relative).also {
        check(it.isFile) { "Missing production source: $relative (root ${javaRoot()})" }
    }

    /** Same resolution as [file] without requiring the file to exist, for negative assertions. */
    fun resolve(relative: String): File = File(javaRoot(), relative)

    /** [relative] is resolved against the repository root, e.g. `app/build.gradle.kts`. */
    fun projectFile(relative: String): File = File(repositoryRoot(), relative).also {
        check(it.isFile) { "Missing project file: $relative (root ${repositoryRoot()})" }
    }

    fun text(relative: String): String = file(relative).readText()

    fun projectText(relative: String): String = projectFile(relative).readText()

    fun compact(relative: String): String = text(relative).replace(Regex("\\s+"), "")
}
