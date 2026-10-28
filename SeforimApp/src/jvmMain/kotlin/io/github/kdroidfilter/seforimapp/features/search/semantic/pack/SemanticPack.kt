package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import io.github.kdroidfilter.seforimapp.features.onboarding.extract.BundleParts
import io.github.kdroidfilter.seforimapp.features.search.semantic.IndependentSemanticSearch
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A pack of Pardes's semantic search: the open model (`pardes-model/`) and the vector index of one library release
 * (`pardes.vec.lucene/`), the two folders the search looks for next to the library (IndependentSemanticSearch).
 * `.github/workflows/semantic-release.yml` releases each pack in [REPOSITORY] as `semantic-<pack>-<library>`, never as
 * the Latest (docs/SEMANTIC_PLAN.md), in SeforimLibrary's bundle format ([BUNDLE_NAME], or its `.partNN` files, and
 * [MANIFEST_NAME], which also says what the pack holds: [PackInfo]) and as two zips to install by hand. The app installs
 * either ([SemanticPackInstaller]), at the next start ([PendingSemanticPack]).
 */
object SemanticPack {
    const val REPOSITORY = "efrul/Pardes"
    const val BUNDLE_NAME = "pardes-semantic.tar.zst"
    val MANIFEST_NAME = BundleParts.manifestNameFor(BUNDLE_NAME)
    const val MODEL_ZIP = "pardes-model.zip"
    const val INDEX_ZIP = "pardes.vec.lucene.zip"
    const val MODEL_DIR = IndependentSemanticSearch.OPEN_MODEL_DIR_NAME
    const val INDEX_DIR = VectorIndexBuilder.DIR_NAME

    /** The two folders of a pack, always put in place together: a model and an index of two packs don't work together. */
    val FOLDERS = listOf(MODEL_DIR, INDEX_DIR)

    /** What the manifest says of the installed pack ([PackInfo]), kept in its model's folder. */
    const val INFO_NAME = "pardes-pack.json"

    private val TAG = Regex("""^semantic-(\d+)-(.+)$""")

    /** A pack's release tag: its number and its library release. */
    data class Tag(
        val pack: Int,
        val library: String,
    )

    fun parseTag(tag: String): Tag? =
        TAG.matchEntire(tag)?.let { match -> match.groupValues[1].toIntOrNull()?.let { Tag(it, match.groupValues[2]) } }

    /**
     * A library release as packs name it (`v3-20261012193827`, SeforimLibrary's tag) and as the library does
     * (`20261012193827` in release_info.txt, which the library's delta updates leave as it is): its time, the last part.
     */
    fun libraryKey(release: String): String = release.trim().substringAfterLast('-').removePrefix("v")

    fun sameLibrary(
        a: String,
        b: String,
    ): Boolean = libraryKey(a) == libraryKey(b)

    /** A file of the bundle: the bundle itself, one of its parts or its manifest. */
    fun isBundleFile(name: String): Boolean =
        name.equals(BUNDLE_NAME, ignoreCase = true) ||
            name.equals(MANIFEST_NAME, ignoreCase = true) ||
            (BundleParts.isPart(name) && name.startsWith("$BUNDLE_NAME.part", ignoreCase = true))
}

/** What a pack holds, as its manifest says (semantic-model/package_pack.py), beyond BundleManifest's files. */
@Serializable
data class PackInfo(
    val pack: Int,
    val model: String,
    val dimension: Int? = null,
    val library: String,
    val lines: Long? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The pack fields of a manifest (or of [SemanticPack.INFO_NAME]), or null when it has none. */
        fun parseOrNull(text: String): PackInfo? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
