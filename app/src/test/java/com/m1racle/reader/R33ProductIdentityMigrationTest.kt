package com.m1racle.reader

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R33ProductIdentityMigrationTest {

    @Test
    fun `android and repository identity is m1racle reader`() {
        val appBuild = file("app/build.gradle.kts").readText()
        val benchmarkBuild = file("benchmark/build.gradle.kts").readText()
        val settings = file("settings.gradle.kts").readText()

        assertTrue(appBuild.contains("namespace = \"com.m1racle.reader\""))
        assertTrue(appBuild.contains("applicationId = \"com.m1racle.reader\""))
        assertTrue(benchmarkBuild.contains("namespace = \"com.m1racle.reader.benchmark\""))
        assertTrue(settings.contains("rootProject.name = \"m1racle-reader\""))
    }

    @Test
    fun `manifest providers and visible brand use migrated identity`() {
        val mainManifest = file("app/src/main/AndroidManifest.xml").readText()
        val qaManifest = file("app/src/qa/AndroidManifest.xml").readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()

        assertTrue(mainManifest.contains("android:name=\".M1racleReaderApplication\""))
        assertTrue(mainManifest.contains("@style/Theme.M1racleReader"))
        assertTrue(qaManifest.contains("com.m1racle.reader.qa.h1fixture"))
        assertTrue(qaManifest.contains("com.m1racle.reader.qa.externalopen"))
        assertTrue(strings.contains("<string name=\"app_name\">M1racle Reader</string>"))
    }

    @Test
    fun `active code and scripts contain no legacy identity`() {
        val oldPackage = "com." + "mark" + "leaf.app"
        val oldPath = "com/" + "mark" + "leaf/app"
        val oldBrand = "Mark" + "Leaf"
        val roots = listOf("app/src", "benchmark/src", "scripts")
        val offenders = roots.flatMap { relative ->
            file(relative).walkTopDown()
                .filter { it.isFile && it.extension in activeTextExtensions }
                .filter { source ->
                    val text = source.readText()
                    text.contains(oldPackage) || text.contains(oldPath) || text.contains(oldBrand, ignoreCase = true)
                }
                .map { it.relativeTo(projectRoot()).invariantSeparatorsPath }
                .toList()
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `room schema bytes stay unchanged while fqcn directory migrates`() {
        val schemaRoot = file("app/schemas/com.m1racle.reader.data.local.AppDatabase")
        val expected = mapOf(
            "6.json" to "c42c8784223ccb6dba45a5a6b9c2d2e1e200cdf636b66d886330ebb9ebc832f3",
            "7.json" to "452eb0cfc58c592c7e995b11f79c57935a81d5879f23d00f5c706846ab375404",
            "8.json" to "8e7e0beb28070316efdc2d98de030b0fb456a160cb5743baae7e73cae35f42ce",
            "9.json" to "fd19ba9c535bfe0ce34e5e01e95356f9fbd8977e610755eabeb7cf3490f1123d",
            "10.json" to "acbacba8e7cc3be5cb83561c388e3e855ac36f5d5312df9d3afe025d81cefbc2",
            "11.json" to "949956265079f1f73ff458769d8fc0013b0704d5e5674a1bff82c70e8159c1a9",
            "12.json" to "6fa8d5cfb97c8e944c478533866daf9dea3c611062812720bb8c5a1bc45e2d16",
        )
        assertEquals(expected.keys, schemaRoot.listFiles().orEmpty().map { it.name }.toSet())
        expected.forEach { (name, digest) ->
            assertEquals(digest, sha256(File(schemaRoot, name)))
        }
    }

    @Test
    fun `release signing contract uses only migrated environment names`() {
        val build = file("app/build.gradle.kts").readText()
        listOf(
            "M1RACLE_RELEASE_STORE_FILE",
            "M1RACLE_RELEASE_STORE_PASSWORD",
            "M1RACLE_RELEASE_KEY_ALIAS",
            "M1RACLE_RELEASE_KEY_PASSWORD",
        ).forEach { assertTrue(build.contains(it)) }
        assertFalse(build.contains("MARK" + "LEAF_RELEASE_"))
    }

    private fun file(relative: String): File = File(projectRoot(), relative)

    private fun projectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val activeTextExtensions = setOf("kt", "kts", "xml", "pro", "properties", "cfg", "sh", "ps1", "sql")
    }
}
