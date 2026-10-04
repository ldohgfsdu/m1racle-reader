import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Public version identity is explicit and committed. Never derive it from Git:
// commit counts are not monotonic across rebases, shallow clones or branches.
val versionPropertiesFile = file("version.properties")
check(versionPropertiesFile.isFile) {
    "Missing app/version.properties; release identity must be committed explicitly."
}
val releaseVersionProperties = Properties().apply {
    versionPropertiesFile.inputStream().use(::load)
}
val releaseVersionCode = releaseVersionProperties.getProperty("versionCode")
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("Invalid app/version.properties: versionCode must be a positive integer.")
val releaseVersionName = releaseVersionProperties.getProperty("versionName")
    ?.trim()
    ?.takeIf { it.matches(Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?$")) }
    ?: error("Invalid app/version.properties: versionName must be SemVer (for example 1.0.0).")

// Secrets are injected by the caller/CI environment and are never read from a
// committed file or supplied as Gradle -P arguments (which may leak in logs).
val releaseSigningEnvironmentNames = listOf(
    "M1RACLE_RELEASE_STORE_FILE",
    "M1RACLE_RELEASE_STORE_PASSWORD",
    "M1RACLE_RELEASE_KEY_ALIAS",
    "M1RACLE_RELEASE_KEY_PASSWORD",
)
val releaseSigningEnvironment = releaseSigningEnvironmentNames.associateWith { name ->
    providers.environmentVariable(name).orNull?.takeIf { it.isNotEmpty() }
}
val providedReleaseSigningNames = releaseSigningEnvironment.filterValues { it != null }.keys
check(providedReleaseSigningNames.isEmpty() || providedReleaseSigningNames.size == releaseSigningEnvironmentNames.size) {
    val missing = releaseSigningEnvironmentNames.filterNot(providedReleaseSigningNames::contains)
    "Partial M1racleReader release signing environment; missing: ${missing.joinToString()}."
}
val hasInjectedReleaseSigning = providedReleaseSigningNames.size == releaseSigningEnvironmentNames.size
val injectedReleaseStoreFile = releaseSigningEnvironment.getValue("M1RACLE_RELEASE_STORE_FILE")
    ?.let(::File)
    ?.let { if (it.isAbsolute) it else rootProject.file(it.path) }

val detailTileOverlayEnabled = providers.gradleProperty("m1racle-reader.detailTileOverlayEnabled")
    .orElse("true").get().toBoolean()
val detailTileBaseLayerEnabled = providers.gradleProperty("m1racle-reader.detailTileBaseLayerEnabled")
    .orElse("true").get().toBoolean()
val detailTileDiagnosticsEnabled = providers.gradleProperty("m1racle-reader.detailTileDiagnosticsEnabled")
    .orElse("false").get().toBoolean()
val detailTileOverlayAlpha = providers.gradleProperty("m1racle-reader.detailTileOverlayAlpha")
    .orElse("1.0").get().toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

android {
    namespace = "com.m1racle.reader"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    testBuildType = "qa"

    defaultConfig {
        applicationId = "com.m1racle.reader"
        minSdk = 31
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        // R7_P0C1_4: geometry visual sample passed; the decoupled tile pipeline is enabled.
        buildConfigField("boolean", "DETAIL_TILE_OVERLAY_ENABLED", detailTileOverlayEnabled.toString())
        // Debug capture controls: base-only, tile-only, combined, and 50% overlay builds.
        buildConfigField("boolean", "DETAIL_TILE_BASE_LAYER_ENABLED", detailTileBaseLayerEnabled.toString())
        buildConfigField("boolean", "DETAIL_TILE_DIAGNOSTICS_ENABLED", detailTileDiagnosticsEnabled.toString())
        buildConfigField("float", "DETAIL_TILE_OVERLAY_ALPHA", "${detailTileOverlayAlpha}f")
        // Disabled for every normal build; the qa build type overrides this below.
        buildConfigField("boolean", "READER_PERFORMANCE_EXPERIMENTS_ENABLED", "false")
        // Glass Lab workbench (Settings entry + route). Off unless a build type enables it.
        buildConfigField("boolean", "GLASS_LAB_ENABLED", "false")
    }

    // Public signing: AGP applies zipalign and APK Signature Scheme signing.
    // Do NOT re-sign outputs with jarsigner — that breaks 16 KB page-aligned
    // uncompressed .so entries (extractNativeLibs=false) and causes
    // INSTALL_FAILED_INVALID_APK / Failed to extract native libraries.
    signingConfigs {
        getByName("debug")
        create("release") {
            if (hasInjectedReleaseSigning) {
                storeFile = injectedReleaseStoreFile
                storePassword = releaseSigningEnvironment.getValue("M1RACLE_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningEnvironment.getValue("M1RACLE_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningEnvironment.getValue("M1RACLE_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "GLASS_LAB_ENABLED", "true")
        }
        create("qa") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            isDebuggable = true
            buildConfigField("boolean", "READER_PERFORMANCE_EXPERIMENTS_ENABLED", "true")
            // QA keeps Glass Lab for internal acceptance; flip to "false" to hide it in QA too.
            buildConfigField("boolean", "GLASS_LAB_ENABLED", "true")
            manifestPlaceholders["appLabel"] = "M1racle Reader QA"
        }
        release {
            // Size: R8 full mode + resource shrink. Keep rules in proguard-rules.pro
            // (PDFBox reflection, Room, Compose). QA/debug stay unminified for FT.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // Public release never ships Glass Lab (no Settings entry, no route).
            buildConfigField("boolean", "GLASS_LAB_ENABLED", "false")
        }
        // Non-debuggable release-like build for Macrobenchmark / FrameTiming on device.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Keep package installable alongside debug for side-by-side checks.
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            // Benchmark is an internal installable artifact, never a public release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Robolectric needs packaged Android resources in JVM unit tests.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // R38 size experiment: PDFBox brings the complete BouncyCastle PQC
            // parameter tables even though M1racle Reader does not invoke the
            // Picnic or SIKE algorithms. Keep all BouncyCastle dependencies and
            // code; exclude only the five measured algorithm data resources.
            excludes += setOf(
                "/org/bouncycastle/pqc/crypto/picnic/lowmc.properties",
                "/org/bouncycastle/pqc/crypto/sike/p434.properties",
                "/org/bouncycastle/pqc/crypto/sike/p503.properties",
                "/org/bouncycastle/pqc/crypto/sike/p610.properties",
                "/org/bouncycastle/pqc/crypto/sike/p751.properties",
            )
        }
    }

    // P0A: expose Room schema JSONs to androidTest assets so MigrationTestHelper
    // can validate migrated schema against the exported v6.json.
    sourceSets {
        getByName("qa") {
            kotlin.srcDirs("src/debug/java", "src/qa/java")
            manifest.srcFile("src/qa/AndroidManifest.xml")
        }
        getByName("androidTest").assets.srcDirs("schemas")
    }
}

// ── Public release gate: never fall back to debug or unsigned output ─────────
val releaseSigning = android.signingConfigs.getByName("release")
val releaseBuildType = android.buildTypes.getByName("release")
val validatePublicReleaseConfiguration = tasks.register("validatePublicReleaseConfiguration") {
    group = "verification"
    description = "Rejects public release output unless version and injected signing are complete."
    doLast {
        check(hasInjectedReleaseSigning) {
            "REFUSED public release: inject ${releaseSigningEnvironmentNames.joinToString()} via environment."
        }
        val store = releaseSigning.storeFile
        check(store != null && store.isFile) {
            "REFUSED public release: injected keystore does not exist or is not a file."
        }
        check(store.name != "debug.keystore") {
            "REFUSED public release: Android debug.keystore is not an acceptable signing identity."
        }
        check(releaseSigning.keyAlias != "androiddebugkey") {
            "REFUSED public release: androiddebugkey is not an acceptable signing alias."
        }
        check(releaseBuildType.signingConfig == releaseSigning) {
            "REFUSED public release: release buildType is not bound to the injected signing config."
        }
        logger.lifecycle("PUBLIC_RELEASE_IDENTITY versionCode=$releaseVersionCode versionName=$releaseVersionName")
    }
}
val publicReleaseOutputTasks = setOf("assembleRelease", "bundleRelease", "packageRelease")
tasks.matching { it.name in publicReleaseOutputTasks }.configureEach {
    dependsOn(validatePublicReleaseConfiguration)
}

// Keep the formerly common command present only as an explicit, pre-install safety refusal.
tasks.register("connectedDebugAndroidTest") {
    group = "verification"
    description = "Refuses device tests against the protected com.m1racle.reader sandbox."
    doFirst {
        error(
            "REFUSED connected test: target applicationId=com.m1racle.reader. " +
                "Use :app:connectedQaAndroidTest (applicationId=com.m1racle.reader.qa).",
        )
    }
}

// Device-test safety: connected tasks may target only the isolated QA application sandbox.
gradle.taskGraph.whenReady {
    val connectedAndroidTests = allTasks.filter {
        it.project == project && it.name.startsWith("connected") && it.name.endsWith("AndroidTest")
    }
    connectedAndroidTests.forEach { task ->
        val targetApplicationId = if (task.name == "connectedQaAndroidTest") {
            "com.m1racle.reader.qa"
        } else {
            "com.m1racle.reader"
        }
        check(targetApplicationId == "com.m1racle.reader.qa") {
            "REFUSED connected test: target applicationId=$targetApplicationId. " +
                "Only :app:connectedQaAndroidTest may run; com.m1racle.reader user data must not be touched."
        }
        task.doFirst {
            logger.lifecycle("ANDROID_TEST_TARGET applicationId=$targetApplicationId")
        }
    }
}

ksp {
    // P0A: export Room schema JSON for migration testing (spec section 五).
    // Schema export began at v6; v1–v5 snapshots cannot be reconstructed by KSP.
    // Every current build exports the latest schema while committed v6+ snapshots
    // remain available to migration verification.
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Robolectric resolves Android runtime jars separately from Gradle repositories.
// Use the same official Central endpoint as the build dependencies.
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Metadata / outline only — page bitmaps stay on Android PdfRenderer.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Liquid glass materials for Reader chrome only (visuals). Official Maven
    // artifact — no vendored sources. See docs/READER_CHROME_MATERIAL_REPORT.md.
    implementation("io.github.kyant0:backdrop:2.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Robolectric-hosted Compose UI tests (JVM; no device needed).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    // Host migration tests use a real SQLite file. Test-only; never packaged in APKs.
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    // Unit tests run against the QA build type (testBuildType = "qa"), so the
    // manifest that declares ComponentActivity for Compose/Robolectric rules
    // must ship with QA too — debugImplementation alone left testQaUnitTest
    // unable to resolve any createComposeRule() activity.
    add("qaImplementation", "androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ── 打完包自动导出到项目 outbox（给 agent 菜单「发件箱」/人工取包）──
// 目录：<repo>/.external_outbox/（已 gitignore，不进仓库）
// 每次 assemble* 成功后：
//   m1racle-reader-<variant>-v<version>-yyyyMMdd-HHmmss.apk  （带时间戳）
//   m1racle-reader-<variant>-latest.apk                     （同变体最新覆盖）
//   对应 .sha256
val m1racleReaderOutboxDir: File =
    rootProject.layout.projectDirectory.dir(".external_outbox").asFile
val m1racleReaderOutboxHistoryLimit = 5

fun pruneVariantOutboxHistory(label: String) {
    val stampedPattern = Regex("^m1racle-reader-${Regex.escape(label)}-v.+\\.apk$")
    val stampedApks = m1racleReaderOutboxDir.listFiles()
        ?.filter { it.isFile && stampedPattern.matches(it.name) }
        .orEmpty()
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
    stampedApks.drop(m1racleReaderOutboxHistoryLimit).forEach { obsoleteApk ->
        val checksum = File(obsoleteApk.path + ".sha256")
        val removedBytes = obsoleteApk.length() + checksum.length()
        val apkRemoved = obsoleteApk.delete()
        val checksumRemoved = !checksum.exists() || checksum.delete()
        check(apkRemoved && checksumRemoved) {
            "OUTBOX: failed to prune ${obsoleteApk.name} or its checksum"
        }
        logger.lifecycle("OUTBOX: pruned ${obsoleteApk.name} ($removedBytes bytes)")
    }
}

fun exportVariantApksToOutbox(variantFolder: String, label: String) {
    val apkDir = layout.buildDirectory.dir("outputs/apk/$variantFolder").get().asFile
    if (!apkDir.isDirectory) {
        logger.lifecycle("OUTBOX: skip $label (no dir ${apkDir.path})")
        return
    }
    val apks = apkDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }.orEmpty()
    if (apks.isEmpty()) {
        logger.lifecycle("OUTBOX: skip $label (no apk in ${apkDir.path})")
        return
    }
    m1racleReaderOutboxDir.mkdirs()
    val version = android.defaultConfig.versionName ?: "0"
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    apks.forEach { apk ->
        ZipFile(apk).use { zip ->
            for (entry in listOf("AndroidManifest.xml", "resources.arsc", "classes.dex")) {
                check(zip.getEntry(entry)?.size?.let { it > 0 } == true) {
                    "OUTBOX: refusing incomplete APK ${apk.name}: missing or empty $entry"
                }
            }
        }
        val stamped = File(m1racleReaderOutboxDir, "m1racle-reader-$label-v${version}-$ts.apk")
        val latest = File(m1racleReaderOutboxDir, "m1racle-reader-$label-latest.apk")
        apk.copyTo(stamped, overwrite = true)
        apk.copyTo(latest, overwrite = true)
        fun writeSha(target: File) {
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hex = digest.digest().joinToString("") { b: Byte -> "%02x".format(b) }
            File(target.path + ".sha256").writeText("$hex  ${target.name}\n")
        }
        writeSha(stamped)
        writeSha(latest)
        logger.lifecycle("OUTBOX: ${stamped.name} (${stamped.length() / 1024} KB) → ${m1racleReaderOutboxDir.path}")
        logger.lifecycle("OUTBOX: ${latest.name} (latest)")
    }
    pruneVariantOutboxHistory(label)
}

listOf(
    "assembleDebug" to ("debug" to "debug"),
    "assembleRelease" to ("release" to "release"),
    "assembleQa" to ("qa" to "qa"),
    "assembleBenchmark" to ("benchmark" to "benchmark"),
).forEach { (taskName, pair) ->
    val (folder, label) = pair
    tasks.matching { it.name == taskName }.configureEach {
        doLast {
            exportVariantApksToOutbox(folder, label)
        }
    }
}

// version.properties participates in manifest task up-to-date checks, so a
// bump invalidates process*Manifest instead of silently reusing stale output.
tasks.matching { it.name.startsWith("process") && it.name.contains("Manifest") }.configureEach {
    inputs.file(versionPropertiesFile)
}

// 仅导出当前已编好的 APK，不重新编译
tasks.register("exportApksToOutbox") {
    group = "distribution"
    description = "Copy existing debug/qa/release/benchmark APKs into .external_outbox/"
    doLast {
        listOf(
            "debug" to "debug",
            "release" to "release",
            "qa" to "qa",
            "benchmark" to "benchmark",
        ).forEach { (folder, label) ->
            exportVariantApksToOutbox(folder, label)
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
