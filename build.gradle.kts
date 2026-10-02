// AGP 9 built-in Kotlin uses the same compiler as the Compose plugin and Backdrop.
buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21") }
}
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.test") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}
