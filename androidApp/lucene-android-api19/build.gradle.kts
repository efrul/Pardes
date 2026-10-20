// The lucene-android jar of Pardes for Android 4.4 (:legacy, minSdk 19): :lucene-android's jarApi19, built from the
// same sources as the main app's jar (see ../lucene-android/build.gradle.kts). This project only offers that jar, as a
// plain JVM library like a jar from Maven, so :legacy resolves it as any dependency while :app keeps :lucene-android.
// One variant, the jar: with no classes-directory variant, a consumer that asks for classes gets the jar.
plugins {
    base
}

evaluationDependsOn(":lucene-android")
val jarApi19 = project(":lucene-android").tasks.named<Jar>("jarApi19")

listOf("apiElements" to Usage.JAVA_API, "runtimeElements" to Usage.JAVA_RUNTIME).forEach { (name, usage) ->
    configurations.create(name) {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(usage))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
            // As :lucene-android's own variants (its compileJava release): the consumers' Java 17 accepts it.
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
        }
        outgoing.artifact(jarApi19)
    }
}

tasks.assemble { dependsOn(jarApi19) }
