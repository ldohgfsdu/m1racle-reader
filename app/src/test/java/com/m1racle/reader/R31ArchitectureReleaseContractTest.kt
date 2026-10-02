package com.m1racle.reader

import com.m1racle.reader.data.local.AppDatabase
import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R31 executable architecture, migration and release-configuration gates. */
class R31ArchitectureReleaseContractTest {

    @Test
    fun `core and data packages never depend on application UI`() {
        val roots = listOf("data", "markdown", "packageimport", "pdf", "share")
        val reverseImports = roots.flatMap { relative ->
            sourceDir(relative).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().asSequence()
                        .filter { it.startsWith("import com.m1racle.reader.ui.") }
                        .map { "${file.relativeTo(projectRoot()).path}:$it" }
                }
                .toList()
        }
        assertEquals(emptyList<String>(), reverseImports)
    }

    @Test
    fun `UI consumes repositories and never imports DAOs`() {
        val uiFiles = sourceDir("ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val daoImports = uiFiles.flatMap { file ->
            file.readLines().filter {
                it.startsWith("import com.m1racle.reader.data.local.") && it.trimEnd().endsWith("Dao")
            }
        }
        assertTrue(daoImports.isEmpty())
        assertTrue(source("M1racleReaderApplication.kt").contains("searchRepository = searchRepository"))
    }

    @Test
    fun `Room migration chain is contiguous through v14 with committed v6 plus schemas`() {
        val migrations = listOf(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
        )
        assertEquals((1..13).map { it to it + 1 }, migrations.map { it.startVersion to it.endVersion })
        val schemas = File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase")
            .listFiles()
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
        assertEquals((6..14).toList(), schemas)
    }

    @Test
    fun `release enables shrink strips debug logs and bounds outbox history`() {
        val build = File(projectRoot(), "app/build.gradle.kts").readText()
        val rules = File(projectRoot(), "app/proguard-rules.pro").readText()
        assertTrue(build.contains("isMinifyEnabled = true"))
        assertTrue(build.contains("isShrinkResources = true"))
        assertTrue(build.contains("m1racleReaderOutboxHistoryLimit = 5"))
        assertTrue(rules.contains("-assumenosideeffects class android.util.Log"))
        assertTrue(rules.contains("public static int d(...);"))
        assertTrue(rules.contains("public static int v(...);"))
    }

    @Test
    fun `removed compatibility-only members stay deleted`() {
        assertFalse(source("pdf/PdfRenderQuality.kt").contains("MAX_RENDER_WIDTH_PX"))
        assertFalse(
            source("ui/viewer/components/MarkdownTableLayout.kt")
                .contains("maximumWideCompressionFraction"),
        )
    }

    private fun projectRoot(): File = ProductionSource.repositoryRoot()

    private fun sourceDir(relative: String): File =
        ProductionSource.resolve("com/m1racle/reader/$relative")

    private fun source(relative: String): String = ProductionSource.text("com/m1racle/reader/$relative")
}
