pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // A mirror outage must not shadow artifacts available at their publisher.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "m1racle-reader"
include(":app")
include(":benchmark")
