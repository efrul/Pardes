import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
}

// Pardes for Android 4.4 to 7.1 (README.md here). The main app (:app) needs Android 8.0: Jetpack Compose needs 6.0,
// Lucene 8.0. This edition draws its screens with Android's own views and reads the same library through the same
// desktop dao (SeforimLibrary core and dao, compiled in unchanged as :app does), on a SQLite of its own
// (src/main/cpp): Android 4.4's SQLite 3.7.11 cannot read the library's schema.
val seforimLibrary = rootDir.resolve("upstream/SeforimLibrary")
val mainApp = rootDir.resolve("app/src/androidMain")

// ------------------------------------------------------------------ SQLite (libpardes_sqlite.so)

// The amalgamation of the SQLite release that requery 3.49 (the main app's bundled SQLite) is built from, its last
// patch release. Downloaded from sqlite.org once and checked against this digest.
val sqliteRelease = "3490200"
val sqliteYear = "2025"
val sqliteSha256 = "921fc725517a694df7df38a2a3dfede6684024b5788d9de464187c612afb5918"
val sqliteDir = layout.buildDirectory.dir("sqlite/sqlite-amalgamation-$sqliteRelease")

abstract class SqliteAmalgamation : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val bytes = URI(url.get()).toURL().openStream().use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == sha256.get()) { "${url.get()}: sha256 $digest, expected ${sha256.get()}" }
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.filter { !it.isDirectory }.forEach { entry ->
                val name = entry.name.substringAfterLast('/')
                if (name == "sqlite3.c" || name == "sqlite3.h" || name == "sqlite3ext.h") dir.resolve(name).writeBytes(zip.readBytes())
            }
        }
        check(dir.resolve("sqlite3.c").isFile) { "sqlite3.c is not in ${url.get()}" }
    }
}

val sqliteAmalgamation by tasks.registering(SqliteAmalgamation::class) {
    url.set("https://www.sqlite.org/$sqliteYear/sqlite-amalgamation-$sqliteRelease.zip")
    sha256.set(sqliteSha256)
    outputDir.set(sqliteDir)
}

// ------------------------------------------------------------------ what this edition shares with the main app

// The main app's plain Kotlin (no Compose, no AndroidX lifecycle, nothing above API 19 unguarded), copied in at build
// time rather than kept twice, under its own names and packages. Each of these files says so in its first lines, and
// lint checks them for API 19 here (lint below).
val sharedAppSources by tasks.registering(Sync::class) {
    from(mainApp.resolve("kotlin")) {
        // Desktop's commentator grouping and line connections as the main app ported them, and the panes' reads.
        include("io/github/kdroidfilter/seforimapp/android/data/CommentatorGrouping.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/reader/LineConnections.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/reader/ReaderLinks.kt")
        // The niqqud and cantillation modes (the ניקוד button, Settings), and פרטי הספר.
        include("io/github/kdroidfilter/seforimapp/android/ui/reader/Diacritics.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/BookDetailsLoader.kt")
        // How an author's name is shown (פרטי הספר), vendored from desktop.
        include("io/github/kdroidfilter/seforimapp/android/search/AuthorNames.kt")
        // The search bar's references (ברכות דף ב, בראשית א ג), vendored from desktop, and the main app's source for
        // them. Its id lists are not split here: PardesApp sets SqlVariableLimit.bindsMany (SQLite 3.49 binds 32,766).
        include("io/github/kdroidfilter/seforimapp/android/search/reference/ReferenceParser.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/reference/ReferenceResolver.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/reference/RepositoryReferenceSource.kt")
        include("io/github/kdroidfilter/seforimapp/android/data/SqlVariableLimit.kt")
        // The Home's catalog row: catalog.pb (else the database's tree) and desktop's dropdowns.
        include("io/github/kdroidfilter/seforimapp/android/data/CatalogAccess.kt")
        include("io/github/kdroidfilter/seforimapp/android/data/CatalogMenus.kt")
        // zayit:// links (desktop's ZayitDeepLink) and the places they open.
        include("io/github/kdroidfilter/seforimapp/android/ui/shell/DeepLinks.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/Destination.kt")
        include("io/github/kdroidfilter/seforimapp/android/data/SearchScope.kt")
        // CONDITIONS_MARKDOWN (desktop's CONDITIONS.md under Pardes's name), shown by Settings › תנאים; the texts of
        // אודות and רישיונות; who made Pardes (PARDES_BASED_ON) and sizes as desktop prints them (formatBytes).
        include("io/github/kdroidfilter/seforimapp/android/ui/onboarding/LicenceText.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/settings/AboutText.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/settings/LicenceTexts.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/onboarding/OnboardingFormat.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/onboarding/DesktopStrings.kt")
        // The search: the desktop engine on the release's prebuilt Lucene indexes and lexical.db (data/LegacySearch.kt
        // opens it and falls back), with its book and author lookup, acronyms, snippets and knowledge panel, and the
        // results' and the search bar's view models and rows. LibraryStore, SplitDatabase, LexicalDb (openLexicalDb)
        // and PreviewText are this edition's own, under the same names (src/androidMain/.../seforimapp/android), and
        // so are AndroidX's ViewModel and Compose's @Immutable (src/*/kotlin/androidx).
        include("io/github/kdroidfilter/seforimapp/android/search/AcronymFrequencyCache.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/LuceneLookupSearchService.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/LuceneSearchEngine.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/LuceneTuning.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/MagicDictionaryIndex.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/RepositorySnippetSourceProvider.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/SearchEntityFinder.kt")
        include("io/github/kdroidfilter/seforimapp/android/search/SearchService.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/search/SearchViewModel.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/search/SearchBarViewModel.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/search/SearchResults.kt")
        include("io/github/kdroidfilter/seforimapp/android/ui/search/SearchBarRows.kt")
    }
    into(layout.buildDirectory.dir("generated/sharedAppSources"))
}

/**
 * The fonts, licence texts and Home wallpapers this edition takes from the main app (assets/fonts, assets/licenses,
 * assets/home), and the repository LICENSE (AGPL-3.0 with Zayit's §7b terms) and Lucene's LICENSE.txt and NOTICE.txt
 * (lucene-android, which Apache 2.0 §4(d) asks to ship with it), word for word, as the main app copies them.
 */
abstract class LegacyAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fontDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenceDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val luceneLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val luceneNotice: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wallpaperDir: DirectoryProperty

    @get:Input abstract val wallpapers: ListProperty<String>

    @get:Input abstract val fonts: ListProperty<String>

    @get:Input abstract val licences: ListProperty<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val fontsOut = out.resolve("fonts").apply { mkdirs() }
        fonts.get().forEach { fontDir.get().asFile.resolve(it).copyTo(fontsOut.resolve(it)) }
        val licencesOut = out.resolve("licenses").apply { mkdirs() }
        licences.get().forEach { licenceDir.get().asFile.resolve(it).copyTo(licencesOut.resolve(it)) }
        appLicence.get().asFile.copyTo(licencesOut.resolve("Pardes-LICENSE.txt"))
        luceneLicence.get().asFile.copyTo(licencesOut.resolve("Lucene-LICENSE.txt"))
        luceneNotice.get().asFile.copyTo(licencesOut.resolve("Lucene-NOTICE.txt"))
        // The Home's wallpapers, their 960px copies (assets rather than resources: decoded once, at the view's size).
        val homeOut = out.resolve("home").apply { mkdirs() }
        wallpapers.get().forEach { wallpaperDir.get().asFile.resolve(it).copyTo(homeOut.resolve(it)) }
        // The main app's font notices (copied from the fonts' name tables), for the fonts this edition carries only.
        val sections = licenceDir.get().asFile.resolve("Fonts-NOTICE.txt").readText().split("\n\n\n")
        val kept = sections.drop(1).filter { section -> fonts.get().any { section.lineSequence().first().contains(it) } }
        licencesOut.resolve("Fonts-NOTICE.txt").writeText(
            (listOf(FONTS_NOTICE_INTRO) + kept).joinToString("\n\n\n"),
        )
    }

    private companion object {
        const val FONTS_NOTICE_INTRO =
            "The notices below are those embedded in the fonts bundled with Pardes for Android 4.4 (name table: " +
                "copyright, licence description and licence URL), copied verbatim. See also GPL-2.0.txt, " +
                "Culmus-LICENSE.txt and the OFL-*.txt files."
    }
}

// ------------------------------------------------------------------ the app

android {
    namespace = "io.github.kdroidfilter.seforimapp.legacy"
    compileSdk = 36

    defaultConfig {
        // The main app's id: the same library folder (Android/data/io.github.efrul.pardes/files/library), so the
        // transfer scripts, Zayit Subset and a library copied by hand reach this edition too. The main app needs
        // Android 8.0, so a phone has one or the other.
        applicationId = "io.github.efrul.pardes"
        minSdk = 19
        targetSdk = 36
        // Below the main app's (4 at 0.4.0): where both could install (Android 8.0 and later), the main app installs
        // over this edition and never the other way round.
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DSQLITE_DIR=${sqliteDir.get().asFile.absolutePath}",
                    "-DMULTIPLEX_DIR=${mainApp.resolve("cpp").absolutePath}",
                )
            }
        }
        ndk {
            // The ABIs of phones with Android 4.4 to 7.1; the 64-bit ones start at Android 5.0.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // NDK r25 is the last that builds for API 19 (r26 starts at 21). The main app's own NDK is newer (27).
    ndkVersion = "25.2.9519653"
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.util's Java 8 methods that the shared Kotlin code calls (Map.forEach, getOrDefault, removeIf...) are
        // only in Android 7.0's library: below it they come from the desugared library.
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        // The project's debug key (androidApp/debug.keystore), as :app: a build from any machine installs over another.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
            // Lucene's keep rules are the main app's (lucene-android/proguard-lucene.pro): its codecs are found by name.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                rootProject.file("lucene-android/proguard-lucene.pro"),
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    // hallucination_blacklist.tsv, read by LuceneSearchEngine as a class-path resource (as the main app's).
    sourceSets["main"].resources.srcDirs(seforimLibrary.resolve("search/src/jvmMain/resources"))
    // licenses/desugar_jdk_libs-LICENSE.txt; the rest of assets/ is copied from the main app (LegacyAssets).
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")

    lint {
        // lint.xml: NewApi is not checked in the shared desktop sources, which hold code this edition never calls
        // (catalog.pb read through java.nio.file); the release dex is checked for calls above API 19 instead
        // (lucene-android/check-release-dex.sh --legacy).
        lintConfig = file("lint.xml")
        // The API level checks only, on the generated sources too: the main app's files copied in (sharedAppSources)
        // are written for API 26 and must not call above API 19 here.
        checkOnly += setOf("NewApi", "InlinedApi")
        checkGeneratedSources = true
    }
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
                implementation(libs.kermit)
                implementation(libs.sqlDelight.runtime)
            }
        }
        androidMain {
            kotlin.srcDir(seforimLibrary.resolve("dao/src/androidMain/kotlin"))
            kotlin.srcDir(sharedAppSources)
            // Desktop full-text search (Lucene), as-is, as the main app compiles it: the two files left out read SQLite
            // through JDBC or open the index with MMapDirectory; their Android variants come with sharedAppSources.
            kotlin.srcDir(seforimLibrary.resolve("search/src/jvmMain/kotlin"))
            kotlin.exclude(
                "io/github/kdroidfilter/seforimlibrary/search/LuceneSearchEngine.kt",
                "io/github/kdroidfilter/seforimlibrary/search/MagicDictionaryIndex.kt",
            )
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.multidex)
                // Hebrew numerals for the references (ReferenceParser), as the main app.
                implementation(libs.hebrew.numerals)
                // The main app's search engine, lucene-core 10.5.2, from the same sources built for minSdk 19 (jarApi19 of
                // lucene-android/build.gradle.kts), and jsoup, which its snippets (SeforimLibrary search) and htmlparser use.
                implementation(project(":lucene-android-api19"))
                implementation(libs.jsoup)
            }
        }
    }
}

tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }.configureEach {
    dependsOn(sqliteAmalgamation)
}

androidComponents {
    onVariants { variant ->
        val assets = tasks.register<LegacyAssets>("${variant.name}LegacyAssets") {
            fontDir.set(mainApp.resolve("res/font"))
            licenceDir.set(mainApp.resolve("assets/licenses"))
            appLicence.set(rootDir.resolve("../LICENSE"))
            luceneLicence.set(rootDir.resolve("lucene-android/LICENSE.txt"))
            luceneNotice.set(rootDir.resolve("lucene-android/NOTICE.txt"))
            wallpaperDir.set(mainApp.resolve("res/drawable-nodpi"))
            wallpapers.set(listOf("homepage_wallpaper_small.jpg", "homepage_wallpaper_dark_small.jpg"))
            // The main app's default book and commentary fonts, and a cantillation font for the targum.
            fonts.set(listOf("notoserifhebrew.ttf", "frankruhllibre.ttf", "taameyfrankclm_medium.ttf"))
            licences.set(
                listOf(
                    "Apache-2.0.txt",
                    "OFL-Noto-Hebrew.txt",
                    "OFL-Frank-Ruhl-Libre.txt",
                    "GPL-2.0.txt",
                    "Culmus-LICENSE.txt",
                    "jsoup-LICENSE.txt",
                ),
            )
        }
        variant.sources.assets?.addGeneratedSourceDirectory(assets, LegacyAssets::outputDir)
    }
}

dependencies {
    // The nio flavour: Lucene's directories need java.nio.file, which Android has from 8.0 only (and the default flavour
    // leaves out); see lucene-android/check-min-api.sh --legacy. On Android 4.4 logcat shows "VFY: unable to resolve"
    // notes for its j$ classes that convert to the platform's java.nio.file, streams and Unsafe: Dalvik replaces those
    // calls, which run on later Android versions only, and the classes load (no VerifyError).
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}

sqldelight {
    databases {
        create("SeforimDb") {
            packageName.set("io.github.kdroidfilter.seforimlibrary.db")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:${libs.versions.sqlDelight.get()}")
            srcDirs.setFrom(seforimLibrary.resolve("dao/src/commonMain/sqldelight"))
        }
    }
}
