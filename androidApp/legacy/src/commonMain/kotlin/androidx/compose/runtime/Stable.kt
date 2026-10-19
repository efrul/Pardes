package androidx.compose.runtime

/**
 * Compose's @Stable, which SeforimLibrary core's models carry for the main app's Compose UI. This edition has no Compose
 * (it needs Android 6.0), so the annotation is declared here for the shared sources to compile; nothing reads it.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class Stable
