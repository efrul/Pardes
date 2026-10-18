import org.gradle.api.artifacts.type.ArtifactTypeDefinition

// lucene-core rebuilt so that it links and runs on Android 11 (API 30) and later. The app depends on this project instead
// of org.apache.lucene:lucene-core. Same version as upstream SeforimLibrary/Zayit (10.5.2): the indexes of the
// current data release are written by 10.5.2, which 10.4 cannot read (doc values meta version 2).
// The jar is the original one except for:
//  - classes rebuilt from src/main/java: original sources edited at "// Android:" comments, plus the small
//    compat classes the rewriter points to (AndroidCompat, AndroidCompatApi33, CompatClassValue, CompatHexFormat,
//    CompatRuntimeVersion, CompatVarHandle);
//  - JDK calls and types that Android lacks, redirected by rewriter/ (ASM): those of API 34/35 always run the compat
//    code, those of API 33 run the JDK method on Android 13+ and the compat code on Android 11 and 12;
//  - Panama/MMap/Module-only classes that can never load on ART, stripped.
// LICENSE.txt and NOTICE.txt next to this file are Lucene's own (META-INF of lucene-core-10.5.2.jar, the same in
// lucene-analysis-common); the app bundles them for Settings › רישיונות.
// Upgrading Lucene: copy the edited files again from the new sources jar, re-apply the "// Android:" edits, copy
// LICENSE.txt and NOTICE.txt again from the new jar, then run the gate: ./check-min-api.sh (the jar dexed by AGP's D8
// for minSdk 30: nothing above API 30, nothing missing on the API 30 and 33 android.jar).
// JVM tests: smoke/LuceneSmoke.java <index dir> and smoke/CompatFallbacks.java, each also with
// -Dlucene.android.forceFallbacks=true (the Android 11/12 code); see their class comments.
// The jar also carries three classes of lucene-analysis-common (analysisClasses, same rewrites, same gate): the
// generator's 4-gram analyzer, which the delta updater needs to write lines into the full-text index on the phone.
plugins {
    `java-library`
}

val luceneVersion = "10.5.2"

group = "io.github.kdroidfilter.seforimapp.android"
version = "$luceneVersion-android"

val lucene: Configuration by configurations.creating { isTransitive = false }
val luceneAnalysis: Configuration by configurations.creating { isTransitive = false }

// SeforimLibrary generator/searchindex BuildLuceneIndex: PerFieldAnalyzerWrapper(StandardAnalyzer, text_ng4 ->
// StandardTokenizer + core.LowerCaseFilter + NGramTokenFilter(4, 4)). Each depends on lucene-core only.
val analysisClasses = listOf(
    "org/apache/lucene/analysis/miscellaneous/PerFieldAnalyzerWrapper",
    "org/apache/lucene/analysis/core/LowerCaseFilter",
    "org/apache/lucene/analysis/ngram/NGramTokenFilter",
)
val rewriter: SourceSet by sourceSets.creating { java.setSrcDirs(listOf("rewriter")) }

dependencies {
    lucene("org.apache.lucene:lucene-core:$luceneVersion")
    luceneAnalysis("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    // Only to compile the patched sources; the original jar must not reach consumers.
    compileOnly("org.apache.lucene:lucene-core:$luceneVersion")
    rewriter.implementationConfigurationName("org.ow2.asm:asm-commons:9.9.1")
}

// Android 13's core library is OpenJDK 11: the patched sources cannot pick up newer JDK API by accident. What Android 11
// and 12 lack of it (Java 9-11 API arrived with API 33), check-min-api.sh finds.
tasks.compileJava { options.release = 11 }
tasks.named<JavaCompile>(rewriter.compileJavaTaskName) { options.release = 17 }

val rewrittenDir = layout.buildDirectory.dir("lucene-android/classes")
val originalManifest = layout.buildDirectory.file("lucene-android/MANIFEST.MF")

val rewriteLucene by tasks.registering(JavaExec::class) {
    description = "Copies lucene-core without replaced/stripped classes and rewrites call sites Android 11 lacks."
    val luceneJar: FileCollection = lucene
    val analysisJar: FileCollection = luceneAnalysis
    val patchedClasses = tasks.compileJava.flatMap { it.destinationDirectory }
    inputs.files(luceneJar).withPropertyName("luceneJar")
    inputs.files(analysisJar).withPropertyName("analysisJar")
    inputs.property("analysisClasses", analysisClasses)
    inputs.dir(patchedClasses).withPropertyName("patchedClasses")
    outputs.dir(rewrittenDir)
    outputs.file(originalManifest)
    classpath = rewriter.runtimeClasspath
    mainClass = "LuceneAndroidRewriter"
    argumentProviders += CommandLineArgumentProvider {
        listOf(luceneJar.singleFile, patchedClasses.get().asFile, rewrittenDir.get().asFile, originalManifest.get().asFile)
            .map { it.absolutePath } + listOf(analysisJar.singleFile.absolutePath, analysisClasses.joinToString(","))
    }
}

tasks.jar {
    from(files(rewrittenDir).builtBy(rewriteLucene))
    manifest {
        from(originalManifest)
        attributes("X-Android-Patched" to "API 30 (see androidApp/lucene-android)")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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
