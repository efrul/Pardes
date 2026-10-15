import org.gradle.api.artifacts.type.ArtifactTypeDefinition

// lucene-core rebuilt so that it links and runs on Android 13 (API 33). The app depends on this project instead
// of org.apache.lucene:lucene-core. Same version as upstream SeforimLibrary/Zayit (10.5.2): the indexes of the
// current data release are written by 10.5.2, which 10.4 cannot read (doc values meta version 2).
// The jar is the original one except for:
//  - classes rebuilt from src/main/java: original sources edited at "// Android:" comments, plus the small
//    compat classes the rewriter points to (AndroidCompat, CompatClassValue, CompatHexFormat, CompatRuntimeVersion);
//  - JDK calls and types that are missing below API 34/35, redirected by rewriter/ (ASM);
//  - Panama/MMap/Module-only classes that can never load on ART, stripped.
// LICENSE.txt and NOTICE.txt next to this file are Lucene's own (META-INF of lucene-core-10.5.2.jar, the same in
// lucene-analysis-common); the app bundles them for Settings › רישיונות.
// Upgrading Lucene: copy the edited files again from the new sources jar, re-apply the "// Android:" edits, copy
// LICENSE.txt and NOTICE.txt again from the new jar, then run the gate: ./check-api33.sh (TraceReferences on the
// dexed jar vs the API 33 android.jar: nothing missing).
// JVM smoke test: java -cp build/libs/lucene-android-10.5.2-android.jar smoke/LuceneSmoke.java <index dir>
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

// Android 13's core library is OpenJDK 11, so the patched sources cannot pick up newer JDK API by accident.
tasks.compileJava { options.release = 11 }
tasks.named<JavaCompile>(rewriter.compileJavaTaskName) { options.release = 17 }

val rewrittenDir = layout.buildDirectory.dir("lucene-android/classes")
val originalManifest = layout.buildDirectory.file("lucene-android/MANIFEST.MF")

val rewriteLucene by tasks.registering(JavaExec::class) {
    description = "Copies lucene-core without replaced/stripped classes and rewrites API 34+ call sites."
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
        attributes("X-Android-Patched" to "API 33 (see androidApp/lucene-android)")
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
