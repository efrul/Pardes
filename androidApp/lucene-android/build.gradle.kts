import org.gradle.api.artifacts.type.ArtifactTypeDefinition

// lucene-core rebuilt so that it links and runs on Android 8.0 (API 26) and later. The app depends on this project instead
// of org.apache.lucene:lucene-core. Same version as upstream SeforimLibrary/Zayit (10.5.2): the indexes of the
// current data release are written by 10.5.2, which 10.4 cannot read (doc values meta version 2).
// The jar is the original one except for:
//  - classes rebuilt from src/main/java: original sources edited at "// Android:" comments, plus the small
//    compat classes the rewriter points to (AndroidCompat, AndroidCompatApi33, CompatClassValue, CompatHexFormat,
//    CompatRuntimeVersion, CompatVarHandle) and two that those and AttributeFactory call (AndroidCompatFlags,
//    AndroidCompatMethodHandles);
//  - JDK calls and types that Android lacks, redirected by rewriter/ (ASM): those of API 34/35 always run the compat
//    code, those of API 33 run the JDK method on Android 13+ and the compat code on Android 8.0 to 12;
//  - Panama/MMap/Module-only classes that can never load on ART, stripped.
// LICENSE.txt and NOTICE.txt next to this file are Lucene's own (META-INF of lucene-core-10.5.2.jar, the same in
// lucene-analysis-common); the app bundles them for Settings › רישיונות.
// Upgrading Lucene: copy the edited files again from the new sources jar, re-apply the "// Android:" edits, copy
// LICENSE.txt and NOTICE.txt again from the new jar, then run the gate: ./check-min-api.sh (the jar dexed by AGP's D8
// for minSdk 26: nothing above API 26, nothing missing on the API 26 and 33 android.jar).
// JVM tests: smoke/LuceneSmoke.java <index dir> and smoke/CompatFallbacks.java, each also with
// -Dlucene.android.forceFallbacks=true (the Android 8.0-12 code); see their class comments.
// The jar also carries three classes of lucene-analysis-common (analysisClasses, same rewrites, same gate): the
// generator's 4-gram analyzer, which the delta updater needs to write lines into the full-text index on the phone.
//
// A second jar from the same sources serves Pardes for Android 4.4 (:legacy, minSdk 19, with core library desugaring
// from desugar_jdk_libs_nio), which takes it through :lucene-android-api19: jarApi19,
// build/libs/lucene-android-<version>-android-api19.jar. Its classes are the main jar's except for:
//  - src/api19/java, compiled in place of the src/main/java files of the same name: stubs of AndroidCompatApi33 and
//    AndroidCompatMethodHandles (D8 cannot dex their signature-polymorphic calls below API 26, and AGP stops the build
//    on them) with AndroidCompatFlags.polymorphicCalls() false, so AndroidCompat.API_30 and API_33 are false on every
//    level; and the look-alikes of the API 24 classes that library desugaring does not add (Compat*);
//  - the rewriter's --api19 mode: those API 24 types redirected, ConcurrentHashMap.keySet() made to link against the
//    desugared library, @FunctionalInterface (API 24) dropped.
// Its gate: ./check-min-api.sh --legacy (the jar dexed for minSdk 19 with the desugared library, then the KitKat
// Dalvik verifier's checks and the signature-polymorphic call scan); its JVM test with the desugared library and the
// emulated file system: smoke/run-desugared.sh (smoke/README.md).
plugins {
    `java-library`
}

val luceneVersion = "10.5.2"

group = "io.github.kdroidfilter.seforimapp.android"
version = "$luceneVersion-android"

val lucene: Configuration by configurations.creating { isTransitive = false }
val luceneAnalysis: Configuration by configurations.creating { isTransitive = false }
// For check-min-api.sh and smoke/run-desugared.sh, not the jars (gateTools below): ASM for the Dalvik checks, and core
// library desugaring in both flavours at the version :legacy uses.
val gateAsm: Configuration by configurations.creating
val desugarDefault: Configuration by configurations.creating
val desugarNio: Configuration by configurations.creating

// SeforimLibrary generator/searchindex BuildLuceneIndex: PerFieldAnalyzerWrapper(StandardAnalyzer, text_ng4 ->
// StandardTokenizer + core.LowerCaseFilter + NGramTokenFilter(4, 4)). Each depends on lucene-core only.
val analysisClasses = listOf(
    "org/apache/lucene/analysis/miscellaneous/PerFieldAnalyzerWrapper",
    "org/apache/lucene/analysis/core/LowerCaseFilter",
    "org/apache/lucene/analysis/ngram/NGramTokenFilter",
)
val rewriter: SourceSet by sourceSets.creating { java.setSrcDirs(listOf("rewriter")) }
// jarApi19's classes: src/main/java with src/api19/java's files in place of those of the same name.
val api19Sources: File = file("src/api19/java")
val api19: SourceSet by sourceSets.creating {
    java.setSrcDirs(listOf("src/main/java", api19Sources))
    java.exclude { element ->
        !element.isDirectory && !element.file.startsWith(api19Sources) &&
            api19Sources.resolve(element.relativePath.pathString).isFile
    }
}

dependencies {
    lucene("org.apache.lucene:lucene-core:$luceneVersion")
    luceneAnalysis("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    // Only to compile the patched sources; the original jar must not reach consumers.
    compileOnly("org.apache.lucene:lucene-core:$luceneVersion")
    api19.compileOnlyConfigurationName("org.apache.lucene:lucene-core:$luceneVersion")
    rewriter.implementationConfigurationName("org.ow2.asm:asm-commons:9.9.1")
    gateAsm("org.ow2.asm:asm-analysis:9.9.1")
    desugarDefault("com.android.tools:desugar_jdk_libs:${libs.versions.desugar.jdk.libs.get()}")
    desugarNio(libs.desugar.jdk.libs.nio)
}

// Android 13's core library is OpenJDK 11: the patched sources cannot pick up newer JDK API by accident. What Android
// 8.0 to 12 lack of it (Java 9-11 API arrived with API 33), check-min-api.sh finds.
tasks.compileJava { options.release = 11 }
tasks.named<JavaCompile>(api19.compileJavaTaskName) { options.release = 11 }
tasks.named<JavaCompile>(rewriter.compileJavaTaskName) { options.release = 17 }

val rewrittenDir = layout.buildDirectory.dir("lucene-android/classes")
val originalManifest = layout.buildDirectory.file("lucene-android/MANIFEST.MF")
val rewrittenApi19Dir = layout.buildDirectory.dir("lucene-android-api19/classes")
val api19Manifest = layout.buildDirectory.file("lucene-android-api19/MANIFEST.MF")

/** A rewriter run over lucene-core (and analysisClasses) into [outDir], leaving out the classes of [patchedClasses]. */
fun registerRewrite(
    name: String,
    what: String,
    mode: List<String>,
    patchedClasses: Provider<Directory>,
    outDir: Provider<Directory>,
    manifest: Provider<RegularFile>,
) = tasks.register<JavaExec>(name) {
    description = what
    val luceneJar: FileCollection = lucene
    val analysisJar: FileCollection = luceneAnalysis
    inputs.files(luceneJar).withPropertyName("luceneJar")
    inputs.files(analysisJar).withPropertyName("analysisJar")
    inputs.property("analysisClasses", analysisClasses)
    inputs.property("mode", mode)
    inputs.dir(patchedClasses).withPropertyName("patchedClasses")
    outputs.dir(outDir)
    outputs.file(manifest)
    classpath = rewriter.runtimeClasspath
    mainClass = "LuceneAndroidRewriter"
    argumentProviders += CommandLineArgumentProvider {
        mode + listOf(luceneJar.singleFile, patchedClasses.get().asFile, outDir.get().asFile, manifest.get().asFile)
            .map { it.absolutePath } + listOf(analysisJar.singleFile.absolutePath, analysisClasses.joinToString(","))
    }
}

val rewriteLucene = registerRewrite(
    "rewriteLucene",
    "Copies lucene-core without replaced/stripped classes and rewrites call sites Android 8.0 lacks.",
    emptyList(),
    tasks.compileJava.flatMap { it.destinationDirectory },
    rewrittenDir,
    originalManifest,
)

val rewriteLuceneApi19 = registerRewrite(
    "rewriteLuceneApi19",
    "rewriteLucene for jarApi19: also the API 24 types, ConcurrentHashMap.keySet() and @FunctionalInterface.",
    listOf("--api19"),
    tasks.named<JavaCompile>(api19.compileJavaTaskName).flatMap { it.destinationDirectory },
    rewrittenApi19Dir,
    api19Manifest,
)

tasks.jar {
    from(files(rewrittenDir).builtBy(rewriteLucene))
    manifest {
        from(originalManifest)
        attributes("X-Android-Patched" to "API 26 (see androidApp/lucene-android)")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val jarApi19 by tasks.registering(Jar::class) {
    description = "The lucene-android jar of Pardes for Android 4.4 (minSdk 19), for :lucene-android-api19."
    archiveClassifier = "api19"
    from(api19.output)
    from(files(rewrittenApi19Dir).builtBy(rewriteLuceneApi19))
    manifest {
        from(api19Manifest)
        attributes("X-Android-Patched" to "API 19 (see androidApp/lucene-android)")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// What check-min-api.sh and smoke/run-desugared.sh run besides AGP's D8 (the Gradle cache's), as "name=path:path"
// lines: ASM for gate/DalvikVerify44.java and gate/PolyScan.java, and desugar_jdk_libs with its configuration jar,
// default and nio flavours.
val gateTools by tasks.registering {
    description = "Resolves the gate's tools and lists their files in build/check-min-api/tools.txt."
    val tools = mapOf<String, FileCollection>("asm" to gateAsm, "default" to desugarDefault, "nio" to desugarNio)
    val list = layout.buildDirectory.file("check-min-api/tools.txt")
    tools.forEach { (name, files) -> inputs.files(files).withPropertyName(name) }
    outputs.file(list)
    doLast {
        list.get().asFile.writeText(
            tools.entries.joinToString("") { (name, files) -> "$name=${files.joinToString(File.pathSeparator)}\n" },
        )
    }
}

// Consumers that take the classes-directory variants instead of the jar must see the whole library too.
listOf(configurations.apiElements, configurations.runtimeElements).forEach { elements ->
    elements.configure {
        outgoing.variants.named("classes") {
            artifact(rewrittenDir) {
                type = ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
                builtBy(rewriteLucene)
            }
        }
    }
}
