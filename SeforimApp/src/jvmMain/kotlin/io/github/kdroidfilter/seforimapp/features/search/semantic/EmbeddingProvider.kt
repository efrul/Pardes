package io.github.kdroidfilter.seforimapp.features.search.semantic

/**
 * Interface decoupling the embedding model from search execution.
 * Allows seamless replacement of proprietary/closed models with open-source Hebrew embedding models.
 */
interface EmbeddingProvider : AutoCloseable {
    val dimension: Int

    fun embed(text: String): ByteArray

    /** [text] as a query, as the model's (normalised) float vector; null when the provider can't. */
    fun embedQuery(text: String): FloatArray? = null

    /**
     * [texts] (passages of found lines, plain) as the model embeds documents, (normalised) float vectors in order;
     * null when the provider can't.
     */
    fun embedPassages(texts: List<String>): List<FloatArray>? = null
}
