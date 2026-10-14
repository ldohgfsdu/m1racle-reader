package com.m1racle.reader

import com.m1racle.reader.testing.ProductionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** R32 public-release identity, signing and Room-baseline contracts. */
class R32ReleaseHardeningContractTest {

    @Test
    fun `public version identity is explicit SemVer and never Git derived`() {
        val version = Properties().apply {
            File(projectRoot(), "app/version.properties").inputStream().use(::load)
        }
        val code = version.getProperty("versionCode").toInt()
        val name = version.getProperty("versionName")
        assertTrue("public versionCode must be positive", code > 0)
        assertTrue("public versionName must be SemVer",
            name.matches(Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?$")))
        assertEquals("compiled identity must come from version.properties", code, BuildConfig.VERSION_CODE)
        assertEquals(name + if (BuildConfig.BUILD_TYPE == "qa") "-qa" else "", BuildConfig.VERSION_NAME)

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
    fun `v12 public baseline upgrades explicitly through every data preserving migration to v17`() {
        val database = source("data/local/AppDatabase.kt")
        assertTrue(database.contains("version = 17"))
        assertTrue(database.contains("MIGRATION_12_13"))
        assertTrue(database.contains("MIGRATION_13_14"))
        assertTrue(database.contains("MIGRATION_14_15"))
        assertTrue(database.contains("MIGRATION_15_16"))
        assertTrue(database.contains("MIGRATION_16_17"))
        assertTrue(File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/16.json").isFile)
        assertTrue(File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/17.json").isFile)
        assertTrue(File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/14.json").isFile)
        assertTrue(File(projectRoot(), "app/schemas/com.m1racle.reader.data.local.AppDatabase/15.json").isFile)
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
        assertTrue(checklist.contains("公开基线自 12 起"))
        assertTrue(checklist.contains("不编造早期 schema"))
    }

    @Test
    fun `release checklist records every mandatory release evidence group`() {
        val checklist = File(projectRoot(), "RELEASE_CHECKLIST.md").readText()
        listOf(
            "源码与版本",
            "自动验证",
            "数据与迁移",
            "签名、产物与交付",
            "人工验收与结论",
        ).forEach { assertTrue("missing checklist section $it", checklist.contains("## $it")) }
        assertTrue(checklist.contains("历史版本通过不能自动继承"))
        assertTrue(checklist.contains("禁止卸载或清库"))
        assertTrue(checklist.contains("不放宽断言"))
    }

    private fun projectRoot(): File = ProductionSource.repositoryRoot()

    private fun buildScript(): String = ProductionSource.projectText("app/build.gradle.kts")

    private fun source(relative: String): String =
        ProductionSource.text("com/m1racle/reader/$relative")
}
