# Keep source file names and line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional dependencies of jsoup that are not on Android (as in the main app's rules).
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**

# Lucene's keep rules: lucene-android/proguard-lucene.pro, listed in build.gradle.kts.

# libpardes_sqlite.so registers NativeSqlite's methods by name and builds SqliteException(int, String) (pardes_sqlite.c).
-keep class io.github.kdroidfilter.seforimapp.legacy.db.NativeSqlite { native <methods>; }
-keep class io.github.kdroidfilter.seforimapp.legacy.db.SqliteException { <init>(int, java.lang.String); }

# kotlinx.serialization: the core/dao models are compiled into this app, so keep their generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class io.github.kdroidfilter.**$$serializer { *; }
-keepclassmembers class io.github.kdroidfilter.** { *** Companion; }
-keepclasseswithmembers class io.github.kdroidfilter.** { kotlinx.serialization.KSerializer serializer(...); }
