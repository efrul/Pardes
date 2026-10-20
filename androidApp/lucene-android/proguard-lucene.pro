# Lucene's keep rules, listed by the main app (app/build.gradle.kts) and by Pardes for Android 4.4
# (legacy/build.gradle.kts): both release builds are shrunk by R8.
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
