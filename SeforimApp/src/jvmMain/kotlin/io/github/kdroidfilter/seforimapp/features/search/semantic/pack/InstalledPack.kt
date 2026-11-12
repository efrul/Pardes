package io.github.kdroidfilter.seforimapp.features.search.semantic.pack

import io.github.kdroidfilter.seforimapp.features.search.semantic.OpenModelSpec
import io.github.kdroidfilter.seforimapp.features.search.semantic.VectorIndexBuilder
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
                when {
                    index.model != spec.id || index.dimension != spec.dimension -> OtherModel
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

        /**
         * [installed] with the number of [newest], the newest pack released, when it carries none of its own and is that
         * pack: the same model over as many lines of the same library release (packs may share a model, and differ by
         * their lines). A pack installed from folders that held no manifest is so; what was found is kept beside the
         * model in [libraryDir], as an install from the pack's own files leaves it.
         */
        fun named(
            libraryDir: File,
            installed: InstalledPack,
            newest: PackInfo,
        ): InstalledPack {
            if (installed !is InUse || installed.pack != null) return installed
            val sameLibrary = installed.library?.let { SemanticPack.sameLibrary(it, newest.library) } != false
            val sameLines = newest.lines != null && newest.lines == installed.lines
            if (newest.model != installed.model || !sameLines || !sameLibrary) return installed
            runCatching { File(File(libraryDir, SemanticPack.MODEL_DIR), SemanticPack.INFO_NAME).writeText(newest.toJson()) }
            return installed.copy(pack = newest.pack)
        }
    }
}
