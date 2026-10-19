rootProject.name = "ZayitAndroid"

pluginManagement {
    // pardes.compose-vector-moves (app/build.gradle.kts): icons on Android 11, see VectorPathCompat
    includeBuild("build-logic")
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
        // requery sqlite-android (a bundled SQLite, for a library split on a FAT32 card and any library below Android 9)
        // is published on JitPack only.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.requery") }
        }
    }
}

include(":app")
// lucene-core patched to link on Android 8.0 (API 26); see lucene-android/build.gradle.kts.
include(":lucene-android")
// Pardes for Android 4.4 to 7.1 (minSdk 19): its own Views UI and SQLite, over the same SeforimLibrary; see legacy/README.md.
include(":legacy")
