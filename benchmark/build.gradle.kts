plugins {
    id("com.android.test")
}

android {
    // The instrumentation APK takes its package from the namespace, so it must not
    // collide with the app-under-test package. :app's benchmark variant installs as
    // com.m1racle.reader.benchmark (applicationIdSuffix), and sharing that name made
    // the test APK install fail with INSTALL_FAILED_VERSION_DOWNGRADE.
    namespace = "com.m1racle.reader.benchmark.host"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // Prefer non-debuggable builds for FrameTimingMetric accuracy.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
        }
    }
}

// Only instrument the app's benchmark / release variants (not debug).
androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val name = variantBuilder.name
        // com.android.test variants mirror the target app's build types.
        variantBuilder.enable = name.contains("benchmark", ignoreCase = true) ||
            name.contains("release", ignoreCase = true)
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
