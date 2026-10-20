# Keep source file names and line numbers in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional dependencies of jsoup and KosherJava that are not on Android.
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
-dontwarn java.awt.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**

# zstd-jni and commons-compress (library download/extraction) use JNI and reflection.
-keep class com.github.luben.zstd.** { *; }
-dontwarn com.github.luben.zstd.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**

# kotlinx.serialization: the core/dao models are compiled into this app, so keep their generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class io.github.kdroidfilter.**$$serializer { *; }
-keepclassmembers class io.github.kdroidfilter.** { *** Companion; }
-keepclasseswithmembers class io.github.kdroidfilter.** { kotlinx.serialization.KSerializer serializer(...); }

# Lucene (:lucene-android): its keep rules are in lucene-android/proguard-lucene.pro, which the Android 4.4 edition
# lists too. This one is the main app's alone (the Android 4.4 jar's AndroidCompatApi33 is a stub, never called).
# AndroidCompatApi33 holds lucene-android's API 33 calls (VarHandle's signature-polymorphic get/set, the Arrays ranges...),
# reached only on Android 13+. R8 moved its VarHandle methods into GroupVIntUtil, which every search loads: Android 11's
# verifier rejected the whole class (VerifyError) and search failed. Kept, its methods stay in a class only 13+ loads.
-keep,allowobfuscation class org.apache.lucene.util.AndroidCompatApi33 { *; }
