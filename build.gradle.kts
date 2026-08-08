plugins {
    id("com.android.application") version "8.7.3" apply false
    // Keep Kotlin 2.0.21 (project baseline). Backdrop 2.0.0 is compiled with
    // Kotlin 2.3 metadata — see app/build.gradle.kts freeCompilerArgs
    // -Xskip-metadata-version-check (minimal isolation; no full stack upgrade).
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
