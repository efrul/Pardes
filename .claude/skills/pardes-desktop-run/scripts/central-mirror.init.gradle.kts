// This container only: Maven Central answers 429 through the shared egress address, so its artifacts are taken from
// Google's mirror of it first; every repository a build declares stays, after it.
val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.centralMirrorFirst() {
    if (any { it.name == "CentralMirror" }) return
    val mirror = maven { name = "CentralMirror"; setUrl(centralMirror) }
    remove(mirror)
    addFirst(mirror)
}

settingsEvaluated {
    // Plugins: before the build's own repositories, or before the plugin portal a build has when it names none
    pluginManagement.repositories.apply {
        if (isEmpty()) gradlePluginPortal()
        centralMirrorFirst()
    }
    dependencyResolutionManagement.repositories.apply { if (isNotEmpty()) centralMirrorFirst() }
}

allprojects {
    afterEvaluate {
        buildscript.repositories.apply { if (isNotEmpty()) centralMirrorFirst() }
        repositories.apply { if (isNotEmpty()) centralMirrorFirst() }
    }
}
