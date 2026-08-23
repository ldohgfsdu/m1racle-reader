import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ── Monotonic versionCode from git commit count ─────────────────────────────
// Dev fallback only (QA/debug). Release uses the explicit app/version.properties
// (single source of truth, strictly bumped by hand); see the release gate below.
val gitCommitCount: Provider<Int> = providers.exec {
    commandLine("git", "-C", rootProject.projectDir.absolutePath, "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { text ->
    text.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
}

// Explicit, persistent, verifiable release versionCode (app/version.properties).
// Missing/invalid → release gate below REFUSES the build; QA/debug fall back.
val versionPropertiesFile = file("version.properties")

fun readReleaseVersionCode(): Int? {
    if (!versionPropertiesFile.isFile) return null
    val text = versionPropertiesFile.readText()
    val line = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: return null
    // Accept both "versionCode=21" and bare "21".
    val value = line.substringAfter('=').trim().ifEmpty { line }
    return value.toIntOrNull()?.takeIf { it > 0 }
}

val detailTileOverlayEnabled = providers.gradleProperty("markleaf.detailTileOverlayEnabled")
    .orElse("true").get().toBoolean()
val detailTileBaseLayerEnabled = providers.gradleProperty("markleaf.detailTileBaseLayerEnabled")
    .orElse("true").get().toBoolean()
val detailTileDiagnosticsEnabled = providers.gradleProperty("markleaf.detailTileDiagnosticsEnabled")
    .orElse("false").get().toBoolean()
val detailTileOverlayAlpha = providers.gradleProperty("markleaf.detailTileOverlayAlpha")
    .orElse("1.0").get().toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f

android {
    namespace = "com.markleaf.app"
    // Backdrop 2.0.0 AAR metadata requires minCompileSdk 37; shapes 1.2.0 needs 36.
    // Host has android-36; 37 may be installed when available. We compile against 36
    // and relax AAR metadata for the Backdrop transitive (see afterEvaluate below).
    // targetSdk stays 35 — compileSdk only unlocks newer android.jar APIs for build.
    compileSdk = 36
    buildToolsVersion = "36.0.0" // PC build host has 35.0.0/36.0.0; AGP 8.7.3 default 34.0.0 not installed.
    testBuildType = "qa"

    defaultConfig {
        applicationId = "com.markleaf.app"
        minSdk = 31
        targetSdk = 35
        versionCode = readReleaseVersionCode() ?: gitCommitCount.get()
        versionName = "0.1.0"
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
    }

    // Local/acceptance signing: AGP applies zipalign + APK Signature Scheme v2/v3.
    // Do NOT re-sign outputs with jarsigner — that breaks 16 KB page-aligned
    // uncompressed .so entries (extractNativeLibs=false) and causes
    // INSTALL_FAILED_INVALID_APK / Failed to extract native libraries.
    signingConfigs {
        // Reuse the Android debug keystore when present so release APKs install on device.
        // Replace with a real release keystore for store distribution.
        getByName("debug")
        create("release") {
            val home = System.getProperty("user.home")
            val debugStore = file("$home/.android/debug.keystore")
            if (debugStore.isFile) {
                storeFile = debugStore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            isDebuggable = true
            buildConfigField("boolean", "READER_PERFORMANCE_EXPERIMENTS_ENABLED", "true")
            manifestPlaceholders["appLabel"] = "MarkLeaf QA"
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
            // Prefer dedicated release config; fall back is unsigned if keystore missing.
            val releaseStore = signingConfigs.getByName("release").storeFile
            if (releaseStore != null && releaseStore.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Non-debuggable release-like build for Macrobenchmark / FrameTiming on device.
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Keep package installable alongside debug for side-by-side checks.
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            // Isolate signing from release path; still installable via debug keystore.
            val releaseStore = signingConfigs.getByName("release").storeFile
            if (releaseStore != null && releaseStore.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Robolectric needs packaged Android resources in JVM unit tests.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Lifecycle lint detector crashes with Compose UI 1.11 (pulled by Backdrop 2.0.0)
    // under AGP 8.7.3: KaCallableMemberCall IncompatibleClassChangeError.
    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = true
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Backdrop 2.0.0 ships Kotlin 2.3 metadata; compiler is 2.0.21.
        // Skip check so we can consume the official Maven artifact without a
        // full Kotlin/AGP/KSP stack upgrade (OBJECTIVE: no blind full upgrade).
        freeCompilerArgs += listOf(
            "-Xskip-metadata-version-check",
            "-Xskip-prerelease-check",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // P0A: expose Room schema JSONs to androidTest assets so MigrationTestHelper
    // can validate migrated schema against the exported v6.json.
    sourceSets {
        getByName("qa") {
            java.srcDirs("src/debug/java", "src/qa/java")
            manifest.srcFile("src/qa/AndroidManifest.xml")
        }
        getByName("androidTest").assets.srcDirs("schemas")
    }
}

// ── Release identity gate: never silently produce an unsigned release APK ──
// Local dev signs release with the debug keystore (accepted compromise);
// but a missing keystore must fail the build loudly instead of emitting an
// uninstallable unsigned APK that the outbox would happily ship.
val releaseIdentityTasks = setOf("assembleRelease", "packageRelease")
val releaseSigning = android.signingConfigs.getByName("release")
val releaseBuildType = android.buildTypes.getByName("release")
tasks.matching { it.name in releaseIdentityTasks }.configureEach {
    doFirst {
        val store = releaseSigning.storeFile
        check(store != null && store.isFile) {
            "REFUSED ${name}: release signing keystore missing (${store}). " +
                "Configure signingConfigs.release.storeFile before building a release APK."
        }
        check(releaseBuildType.signingConfig != null) {
            "REFUSED ${name}: release buildType has no signingConfig."
        }
        // Explicit release versionCode: app/version.properties is the single
        // source of truth. Never silently fall back to git commit count here —
        // that sequence is not strictly monotonic (rebase/squash/branch/shallow).
        check(readReleaseVersionCode() != null) {
            "REFUSED ${name}: app/version.properties missing or invalid " +
                "(versionCode must be a positive integer). Bump it by +1 per release."
        }
    }
}

// Keep the formerly common command present only as an explicit, pre-install safety refusal.
tasks.register("connectedDebugAndroidTest") {
    group = "verification"
    description = "Refuses device tests against the protected com.markleaf.app sandbox."
    doFirst {
        error(
            "REFUSED connected test: target applicationId=com.markleaf.app. " +
                "Use :app:connectedQaAndroidTest (applicationId=com.markleaf.app.qa).",
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
            "com.markleaf.app.qa"
        } else {
            "com.markleaf.app"
        }
        check(targetApplicationId == "com.markleaf.app.qa") {
            "REFUSED connected test: target applicationId=$targetApplicationId. " +
                "Only :app:connectedQaAndroidTest may run; com.markleaf.app user data must not be touched."
        }
        task.doFirst {
            logger.lifecycle("ANDROID_TEST_TARGET applicationId=$targetApplicationId")
        }
    }
}

ksp {
    // P0A: export Room schema JSON for migration testing (spec section 五).
    // First enabling does NOT retro-generate historical v1–v5 schemas; only
    // v6 (current) is exported. Historical backfill is a separate task.
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Backdrop 2.0.0 declares minCompileSdk=37 while this project targets compileSdk 36
// (android-37 platform not always installed; APIs we use are blur/lens at 31–33).
// Disable AAR metadata gate so Liquid can ship without a full AGP/platform upgrade.
// Documented in docs/READER_CHROME_MATERIAL_REPORT.md.
afterEvaluate {
    tasks.matching { it.name.contains("AarMetadata", ignoreCase = true) }.configureEach {
        enabled = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
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

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Metadata / outline only — page bitmaps stay on Android PdfRenderer.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Liquid glass materials for Reader chrome only (visuals). Official Maven
    // artifact — no vendored sources. See docs/READER_CHROME_MATERIAL_REPORT.md.
    implementation("io.github.kyant0:backdrop:2.0.0") {
        // Prefer project Kotlin stdlib (2.0.21); library declares 2.3.21.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Robolectric-hosted Compose UI tests (JVM; no device needed).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
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
//   markleaf-<variant>-v<version>-yyyyMMdd-HHmmss.apk  （带时间戳）
//   markleaf-<variant>-latest.apk                     （同变体最新覆盖）
//   对应 .sha256
val markleafOutboxDir: File =
    rootProject.layout.projectDirectory.dir(".external_outbox").asFile

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
    markleafOutboxDir.mkdirs()
    val version = android.defaultConfig.versionName ?: "0"
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    apks.forEach { apk ->
        val stamped = File(markleafOutboxDir, "markleaf-$label-v${version}-$ts.apk")
        val latest = File(markleafOutboxDir, "markleaf-$label-latest.apk")
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
        logger.lifecycle("OUTBOX: ${stamped.name} (${stamped.length() / 1024} KB) → ${markleafOutboxDir.path}")
        logger.lifecycle("OUTBOX: ${latest.name} (latest)")
    }
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
