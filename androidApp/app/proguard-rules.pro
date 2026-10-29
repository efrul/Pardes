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

# Lucene (:lucene-android, the desktop full-text search). Each group below was checked by shrinking the jar
# with these rules and running a search on a desktop index (lucene-android/smoke); dropping the attribute or
# attribute-key rules breaks it.
# Codecs, postings/doc-values/vector formats, index-sort providers and tokenizer factories are found by name
# through META-INF/services: the service types keep their names (the file names) and the providers their names
# and no-arg constructors (the file contents), whether or not R8 rewrites the service files.
-keepnames class org.apache.lucene.codecs.Codec
-keepnames class org.apache.lucene.codecs.PostingsFormat
-keepnames class org.apache.lucene.codecs.DocValuesFormat
-keepnames class org.apache.lucene.codecs.KnnVectorsFormat
-keepnames class org.apache.lucene.index.SortFieldProvider
-keepnames class org.apache.lucene.analysis.TokenizerFactory
-keep class * extends org.apache.lucene.codecs.Codec { public <init>(); }
-keep class * extends org.apache.lucene.codecs.PostingsFormat { public <init>(); }
-keep class * extends org.apache.lucene.codecs.DocValuesFormat { public <init>(); }
-keep class * extends org.apache.lucene.codecs.KnnVectorsFormat { public <init>(); }
-keep class * extends org.apache.lucene.index.SortFieldProvider { public <init>(); }
# Analysis factories are looked up by their static NAME and built through the (Map) constructor.
-keep class * extends org.apache.lucene.analysis.AbstractAnalysisFactory {
    public static final java.lang.String NAME;
    public <init>(java.util.Map);
}
# AttributeFactory loads "<attribute interface name>Impl" by name and calls its public no-arg constructor.
-keepnames interface * extends org.apache.lucene.util.Attribute
-keep class * extends org.apache.lucene.util.AttributeImpl { public <init>(); }
# Segment and field attribute keys are built from these class names ("Lucene90StoredFieldsFormat.mode",
# "PerFieldPostingsFormat.format", ...) and must match the keys the desktop wrote into the index.
-keepnames class org.apache.lucene.codecs.lucene90.Lucene90StoredFieldsFormat
-keepnames class org.apache.lucene.codecs.perfield.PerFieldPostingsFormat
-keepnames class org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat
-keepnames class org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat
# AndroidCompatApi33 holds lucene-android's API 33 calls (VarHandle's signature-polymorphic get/set, the Arrays ranges...),
# reached only on Android 13+. R8 moved its VarHandle methods into GroupVIntUtil, which every search loads: Android 11's
# verifier rejected the whole class (VerifyError) and search failed. Kept, its methods stay in a class only 13+ loads.
-keep,allowobfuscation class org.apache.lucene.util.AndroidCompatApi33 { *; }
# ONNX Runtime (the semantic search's model): its JNI library finds the Java classes, constructors, fields and methods
# by name (OnnxTensor, OrtException, the session's results, TensorInfo...), and its AAR brings no keep rules.
-keep class ai.onnxruntime.** { *; }
