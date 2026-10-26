import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    id("pardes.compose-vector-moves")
}

// SeforimLibrary as desktop Zayit 0.6.10 builds it: the repository's SeforimLibrary submodule (7ddf045), the same
// checkout the desktop app and the shared modules build with.
val seforimLibrary = rootDir.resolve("../SeforimLibrary")
// Other shared sources reused as-is from the desktop project.
val desktopRoot = rootDir.resolve("..")

// The one generator-common file the delta updater shares with the producer (patch table order and keys), copied on
// its own: its source folder is mostly JDBC tooling that is not compiled here.
val syncPatchTables by tasks.registering(Sync::class) {
    from(seforimLibrary.resolve("generator/common/src/jvmMain/kotlin")) {
        include("io/github/kdroidfilter/seforimlibrary/common/patch/PatchTables.kt")
    }
    into(layout.buildDirectory.dir("generated/deltaPatchTables"))
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(seforimLibrary.resolve("core/src/commonMain/kotlin"))
            kotlin.srcDir(seforimLibrary.resolve("dao/src/commonMain/kotlin"))
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kermit)
            }
        }
        androidMain {
            kotlin.srcDir(seforimLibrary.resolve("dao/src/androidMain/kotlin"))
            // htmlparser as desktop 0.6.10 has it (citation links, plainTextFromHtml), vendored; ../htmlparser equals it
            // since the merge of Zayit 0.6.10.
            kotlin.srcDir("src/androidMain/htmlparser")
            // Desktop full-text search (Lucene), as-is. The two files excluded below read SQLite through JDBC or
            // open the index with MMapDirectory and Path.of; their Android variants (same package and class names)
            // are in src/androidMain/.../android/search, next to the 0.6.10 app files the search needs (vendored).
            kotlin.srcDir(seforimLibrary.resolve("search/src/jvmMain/kotlin"))
            kotlin.exclude(
                "io/github/kdroidfilter/seforimlibrary/search/LuceneSearchEngine.kt",
                "io/github/kdroidfilter/seforimlibrary/search/MagicDictionaryIndex.kt",
            )
            // Desktop delta updater (incremental library updates), as-is: the manifest/release_meta models and the
            // update path chooser. The files excluded below use JDBC, java.net without Range-safe settings, or roll
            // back with an 8 GB backup copy; their Android variants (same package and class names) are in
            // src/androidMain/.../android/delta, with the generator's PatchApplier and LogicalContentHasher.
            kotlin.srcDir(seforimLibrary.resolve("delta-updater/src/jvmMain/kotlin"))
            kotlin.exclude(
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/CatalogUpdater.kt",
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/DeltaApplierClient.kt",
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/DeltaDownloader.kt",
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/DeltaUpdaterClient.kt",
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/LuceneUpdater.kt",
                "io/github/kdroidfilter/seforimlibrary/deltaupdater/UpdateOrchestrator.kt",
            )
            // generator-common's table list (PatchTables.kt), as-is: the rest of that module is the producer side.
            kotlin.srcDir(syncPatchTables)
            // Desktop icons (the search telescope).
            kotlin.srcDir(desktopRoot.resolve("icons/src/commonMain/kotlin"))
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.activity.compose)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqlDelight.driver.android)
                implementation(libs.jsoup)
                implementation(libs.zmanim)
                // Desktop 0.6.10's zmanim library: the עתים לבינה calculator and luach zmanim (data/ZmanimEngine.kt).
                // It brings kotlin-stdlib 2.4.20, kotlinx-datetime 0.8.0 and kotlinx-serialization-json 1.11.0.
                implementation(libs.kosherkotlin)
                implementation(libs.hebrew.numerals)
                // Onboarding: unpacking the desktop library bundle (.tar.zst). The AAR carries the Android .so files;
                // the default jar of zstd-jni only has desktop natives.
                implementation("com.github.luben:zstd-jni:${libs.versions.zstd.jni.get()}@aar")
                implementation(libs.commons.compress)
                // Search: lucene-core 10.5.2 (the version that writes the desktop indexes), patched for API 26.
                implementation(project(":lucene-android"))
                // A library split in chunks on a FAT32 memory card (data/SplitDatabase.kt): a SQLite that can load the
                // multiplex VFS (src/androidMain/cpp), which Android's own SQLite cannot. Below Android 9 it reads every
                // library, with the wide cursor windows Android's SQLite cannot have there.
                implementation(libs.requery.sqlite)
                implementation(libs.androidx.sqlite.framework)
            }
        }
    }
}

android {
    namespace = "io.github.kdroidfilter.seforimapp.android"
    compileSdk = 36

    defaultConfig {
        // Pardes, not Zayit: renamed at the request of Zayit's author, who asked forks not to use his app's name or icon.
        // A new id, so it installs beside the earlier io.github.efrul.zayit builds instead of over them.
        applicationId = "io.github.efrul.pardes"
        // Android 8.0 (33 up to 0.3.1, then 30). Newer platform calls sit behind SDK_INT checks with a fallback, so
        // Android 11 and later behave as with 30 and Android 12 and later as with 33; the launcher widgets need 12,
        // incremental library updates 11 (docs/DEVELOPMENT.md › Android 8 to 12). API 26 is the floor: Lucene needs
        // java.nio.file, java.time and MethodHandle.invoke.
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0"
    }

    // libpardes_multiplex.so: SQLite's multiplex VFS as an extension of requery's SQLite (data/SplitDatabase.kt).
    ndkVersion = "27.3.13750724"
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    // hallucination_blacklist.tsv, read by LuceneSearchEngine as a class-path resource.
    sourceSets["main"].resources.srcDirs(seforimLibrary.resolve("search/src/jvmMain/resources"))
    // licenses/: the third-party licence texts of Settings › רישיונות (the copied ones are added by licenceAssets below).
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // The project's own debug key (androidApp/debug.keystore) instead of each machine's ~/.android one, so a
        // build from a cloud session and a build from a PC install over each other. It is public on purpose and
        // only fit for development builds.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Public releases: Pardes's own key, which only the release workflow has (the repository's secrets; the
        // workflow writes it to the file PARDES_KEYSTORE names). A build without it, on any machine, stays signed with
        // the debug key above, so it still installs over the development builds.
        val releaseStore = System.getenv("PARDES_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("PARDES_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PARDES_KEY_ALIAS")
                keyPassword = System.getenv("PARDES_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 cuts the dex roughly tenfold (icons-extended is huge)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // The dependencies' META-INF licence files never reach an APK (AGP drops META-INF/LICENSE* and NOTICE*): their texts
    // are in assets/licenses instead, shown by Settings › רישיונות.
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        // Below minSdk 28 AGP compresses classes.dex by default: stored as it was with minSdk 28 and later, so the APK
        // each installed app downloads as its update does not change for that, and Android 9 and later keep reading
        // the dex in place from it.
        dex.useLegacyPackaging = false
    }
}

/**
 * The licence texts copied into the APK's assets/licenses rather than kept twice in the repository: the repository's
 * LICENSE (AGPL-3.0 with Zayit's §7b terms, word for word) and Lucene's LICENSE.txt and NOTICE.txt (lucene-android).
 */
abstract class LicenceAssets : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val luceneLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val luceneNotice: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        appLicence.get().asFile.copyTo(dir.resolve("Pardes-LICENSE.txt"))
        luceneLicence.get().asFile.copyTo(dir.resolve("Lucene-LICENSE.txt"))
        luceneNotice.get().asFile.copyTo(dir.resolve("Lucene-NOTICE.txt"))
    }
}

/**
 * Desktop's own texts the app reads as they are, copied into the APK's assets rather than kept twice: temple_count.txt,
 * the Temple widget's counts explained (desktop composeResources/files, since 0.6.8).
 */
abstract class DesktopTextAssets : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templeCount: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        templeCount.get().asFile.copyTo(dir.resolve("temple_count.txt"))
    }
}

androidComponents {
    onVariants { variant ->
        val licenceAssets = tasks.register<LicenceAssets>("${variant.name}LicenceAssets") {
            appLicence.set(desktopRoot.resolve("LICENSE"))
            luceneLicence.set(rootDir.resolve("lucene-android/LICENSE.txt"))
            luceneNotice.set(rootDir.resolve("lucene-android/NOTICE.txt"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(licenceAssets, LicenceAssets::outputDir)
        val desktopTexts = tasks.register<DesktopTextAssets>("${variant.name}DesktopTextAssets") {
            templeCount.set(desktopRoot.resolve("SeforimApp/src/commonMain/composeResources/files/temple_count.txt"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(desktopTexts, DesktopTextAssets::outputDir)
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}

// Stock lucene-core does not link on Android: if a library ever brings it transitively, use the patched one.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.apache.lucene:lucene-core")).using(project(":lucene-android"))
    }
}

sqldelight {
    databases {
        create("SeforimDb") {
            packageName.set("io.github.kdroidfilter.seforimlibrary.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
            srcDirs.setFrom(seforimLibrary.resolve("dao/src/commonMain/sqldelight"))
        }
        create("UserSettingsDb") {
            packageName.set("io.github.kdroidfilter.seforimapp.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
            // Desktop 0.6.10's user_settings.db schema (SeforimApp/src/commonMain/sqldelight at v0.6.10, the same since
            // v0.6.1), vendored: notes, highlights, visit history, favorites and shnayim mikra, so a desktop file
            // imported with --with-notes is read as is.
            // sqldelight-user-legacy: the two UPSERTs as Android 8-10's SQLite can run them (no schema of its own).
            srcDirs.setFrom(file("src/androidMain/sqldelight-user"), file("src/androidMain/sqldelight-user-legacy"))
        }
    }
}
