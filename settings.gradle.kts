pluginManagement {
    repositories {
        // PC build host: dl.google.com unreachable. Aliyun mirrors take priority.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // PC build host: dl.google.com unreachable. Aliyun mirrors take priority.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "m1racle-reader"
include(":app")
include(":benchmark")
