plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
    compileSdk = 35
    buildToolsVersion = "35.0.0" // PC build host has 35.0.0/36.0.0; AGP 8.7.3 default 34.0.0 not installed.

    defaultConfig {
        applicationId = "com.markleaf.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // R7_P0C1_4: geometry visual sample passed; the decoupled tile pipeline is enabled.
        buildConfigField("boolean", "DETAIL_TILE_OVERLAY_ENABLED", detailTileOverlayEnabled.toString())
        // Debug capture controls: base-only, tile-only, combined, and 50% overlay builds.
        buildConfigField("boolean", "DETAIL_TILE_BASE_LAYER_ENABLED", detailTileBaseLayerEnabled.toString())
        buildConfigField("boolean", "DETAIL_TILE_DIAGNOSTICS_ENABLED", detailTileDiagnosticsEnabled.toString())
        buildConfigField("float", "DETAIL_TILE_OVERLAY_ALPHA", "${detailTileOverlayAlpha}f")
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
        release {
            isMinifyEnabled = false
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
        getByName("androidTest").assets.srcDirs("schemas")
    }
}

ksp {
    // P0A: export Room schema JSON for migration testing (spec section 五).
    // First enabling does NOT retro-generate historical v1–v5 schemas; only
    // v6 (current) is exported. Historical backfill is a separate task.
    arg("room.schemaLocation", "$projectDir/schemas")
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
