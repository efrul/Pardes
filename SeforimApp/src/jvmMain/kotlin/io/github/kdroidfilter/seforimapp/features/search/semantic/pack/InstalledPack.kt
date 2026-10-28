package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
import io.github.kdroidfilter.seforimapp.framework.database.LibraryLocation
import java.io.File

/** The semantic pack beside a library, as the settings show it, checked as IndependentSemanticSearch opens it. */
sealed interface InstalledPack {
    data object None : InstalledPack

    /** What the search uses: the pack's number when it is known, the model's id, the index's lines and library. */
    data class InUse(
        val pack: Int?,
        val model: String,
        val lines: Long?,
        val library: String?,
    ) : InstalledPack

    /** There, but not used: the search finds the words only. */
    sealed interface NotUsed : InstalledPack

    data object NoModel : NotUsed

    data object NoIndex : NotUsed

    data object OtherModel : NotUsed

    data class OtherLibrary(
        val pack: String,
        val installed: String,
    ) : NotUsed

    data class Unreadable(
        val message: String,
    ) : NotUsed

    companion object {
        fun read(libraryDir: File): InstalledPack {
            val modelDir = File(libraryDir, SemanticPack.MODEL_DIR)
            val indexDir = File(libraryDir, SemanticPack.INDEX_DIR)
            if (!modelDir.isDirectory && !indexDir.isDirectory) return None
            return runCatching {
                val spec = OpenModelSpec.read(modelDir.toPath()) ?: return NoModel
                val index = VectorIndexBuilder.readInfo(indexDir.toPath()) ?: return NoIndex
                val library = LibraryLocation.version(libraryDir)
                when {
                    index.model != spec.id || index.dimension != spec.dimension -> OtherModel
                    index.dbRelease != null && library != null && !SemanticPack.sameLibrary(index.dbRelease, library) ->
                        OtherLibrary(index.dbRelease, library)
                    else -> {
                        val info =
                            File(modelDir, SemanticPack.INFO_NAME)
                                .takeIf { it.isFile }
                                ?.let { PackInfo.parseOrNull(it.readText()) }
                                ?.takeIf { it.model == spec.id }
                        InUse(info?.pack, spec.id, index.lines ?: info?.lines, index.dbRelease ?: info?.library)
                    }
                }
            }.getOrElse { Unreadable(it.message ?: it.toString()) }
        }
    }
}
