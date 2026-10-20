package androidx.compose.runtime

/**
 * Compose's @Immutable, which the main app's search models (SearchViewModel, SearchBarViewModel) carry for its Compose
 * UI. As [Stable], declared here for the shared sources to compile; nothing reads it.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class Immutable
