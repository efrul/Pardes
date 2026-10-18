rootProject.name = "ZayitAndroid"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // requery sqlite-android (a bundled SQLite, for a library split on a FAT32 card) is published on JitPack only.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
    }
}

include(":app")
// lucene-core patched to link on Android 11 (API 30); see lucene-android/build.gradle.kts.
include(":lucene-android")
