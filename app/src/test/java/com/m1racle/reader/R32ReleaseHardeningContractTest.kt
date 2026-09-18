package com.m1racle.reader

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R32 public-release identity, signing and Room-baseline contracts. */
class R32ReleaseHardeningContractTest {

    @Test
    fun `public version identity is explicit SemVer and never Git derived`() {
        val version = Properties().apply {
            File(projectRoot(), "app/version.properties").inputStream().use(::load)
        }
        assertEquals("23", version.getProperty("versionCode"))
        assertEquals("1.0.0", version.getProperty("versionName"))

        val build = buildScript()
        assertTrue(build.contains("versionCode = releaseVersionCode"))
        assertTrue(build.contains("versionName = releaseVersionName"))
        assertFalse(build.contains("gitCommitCount"))
        assertFalse(build.contains("versionName = \"0.1.0\""))
    }

    @Test
    fun `public signing accepts only complete environment injection`() {
        val build = buildScript()
        listOf(
            "M1RACLE_RELEASE_STORE_FILE",
            "M1RACLE_RELEASE_STORE_PASSWORD",
            "M1RACLE_RELEASE_KEY_ALIAS",
            "M1RACLE_RELEASE_KEY_PASSWORD",
        ).forEach { assertTrue("missing $it", build.contains("\"$it\"")) }
        assertTrue(build.contains("validatePublicReleaseConfiguration"))
        assertTrue(build.contains("signingConfig = signingConfigs.getByName(\"release\")"))
        assertTrue(build.contains("store.name != \"debug.keystore\""))
        assertTrue(build.contains("releaseSigning.keyAlias != \"androiddebugkey\""))
        assertFalse(build.contains("$" + "home/.android/debug.keystore"))
        assertFalse(build.contains("storePassword = \"android\""))
    }

    @Test
    fun `signing material patterns are excluded from Git`() {
        val ignores = File(projectRoot(), ".gitignore").readText()
        listOf("*.jks", "*.keystore", "*.p12", "*.pfx", "keystore.properties")
            .forEach { assertTrue("missing ignore $it", ignores.lineSequence().contains(it)) }
    }

    @Test
    fun `v12 public baseline upgrades explicitly through v13 to v14`() {
        val database = source("data/local/AppDatabase.kt")
        assertTrue(database.contains("version = 14"))
        assertTrue(database.contains("MIGRATION_12_13"))
        assertTrue(database.contains("MIGRATION_13_14"))
        assertTrue(File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/14.json").isFile)
        assertTrue(
            File(
                projectRoot(),
                "app/schemas/com.m1racle.reader.data.local.AppDatabase/12.json",
            ).isFile,
        )
        assertTrue(
            File(
                projectRoot(),
                "app/schemas/com.m1racle.reader.data.local.AppDatabase/13.json",
            ).isFile,
        )
        val checklist = File(projectRoot(), "RELEASE_CHECKLIST.md").readText()
        assertTrue(checklist.contains("v12 is the first public database baseline"))
        assertTrue(checklist.contains("Do not fabricate v1-v5 schema snapshots"))
    }

    @Test
    fun `release checklist records every mandatory release evidence group`() {
        val checklist = File(projectRoot(), "RELEASE_CHECKLIST.md").readText()
        listOf(
            "Signing",
            "Version",
            "Pre-migration R32 APK identity and hash (historical evidence)",
            "Automated verification",
            "Room migration policy",
            "Known limitations",
        ).forEach { assertTrue("missing checklist section $it", checklist.contains("## $it")) }
    }

    private fun projectRoot(): File = listOf(File("."), File(".."), File("/root/code/m1racle-reader"))
        .first { File(it, "app/src/main/java/com/m1racle/reader").isDirectory }
        .canonicalFile

    private fun buildScript(): String = File(projectRoot(), "app/build.gradle.kts").readText()

    private fun source(relative: String): String =
        File(projectRoot(), "app/src/main/java/com/m1racle/reader/$relative").readText()
}
