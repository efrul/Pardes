// Google's mirror of Maven Central, after the repositories a build declares: for an artifact Maven Central no longer
// serves. On 2026-11-23 it answered 404 (NoSuchKey) for io.github.kdroidfilter:kosherkotlin 3.0.3 to 3.0.5, which its
// index still lists, and no clean build of Pardes could resolve the 3.0.5 that Zayit 0.6.12 is built with. The mirror
// has the same files. desktop-release.yml copies this into Gradle's init.d; Maven Central stays first.
val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.centralMirrorLast() {
    if (none { it.name == "CentralMirror" }) {
        maven {
            name = "CentralMirror"
            setUrl(centralMirror)
        }
    }
}

settingsEvaluated {
    // Only where a build names its repositories in its settings: a build that names them in its projects keeps them
    pluginManagement.repositories.apply { if (isNotEmpty()) centralMirrorLast() }
    dependencyResolutionManagement.repositories.apply { if (isNotEmpty()) centralMirrorLast() }
}
