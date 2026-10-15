package io.github.kdroidfilter.seforimlibrary.deltaupdater

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Android variant of SeforimLibrary (381fbea) `delta-updater/.../DeltaUpdaterClient.kt`: the facade over
 * [DeltaApplierClient], [DeltaDownloader], [LuceneUpdater], [CatalogUpdater] and [UpdateOrchestrator], with desktop's
 * surface ([recoverIfNeeded], [checkForUpdate], [applyChain]) and the Android needs around it:
 *  - the downloader is passed in (it carries the app's resumable, CA-aware HTTP stream) and so are the progress and
 *    cancellation hooks of each component;
 *  - [fetchReleaseMeta] tells "no release_meta.json on the server" (null) apart from "up to date", so the app can fall
 *    back to the GitHub releases (desktop's [checkForUpdate] folds a 404 into [UpdatePath.UpToDate], still does here);
 *  - [choosePath] prefers a direct patch: the release workflow publishes a fan of patches from 1, 2, 4, 8 and 16
 *    versions back, which desktop's chain chooser (one version per step) cannot use;
 *  - [pendingChain] resumes a delta an earlier run left half done (no backup to restore on Android).
 */
class DeltaUpdaterClient(
    private val seforimDb: Path,
    private val catalogPb: Path,
    private val workDir: Path,
    private val releaseMetaUrl: String,
    private val indexSinks: () -> LuceneUpdater.SinkSession,
    private val localVersionProvider: () -> Int,
    private val downloader: DeltaDownloader,
    private val httpGet: (String) -> String,
    private val isNotFound: (Throwable) -> Boolean = { it is FileNotFoundException },
    private val urlForPatchFile: (DeltaEntry, String) -> String = { entry, file ->
        // Default: manifestUrl is "<base>/<from>-<to>.json"; resolve siblings.
        val base = entry.manifestUrl.substringBeforeLast('/')
        "$base/$file"
    },
    private val logger: Logger = Logger.withTag("DeltaUpdaterClient"),
    hashProgress: (rowsDone: Long, rowsTotal: Long, afterApply: Boolean) -> Unit = { _, _, _ -> },
    onDecompress: (written: Long, total: Long) -> Unit = { _, _ -> },
    checkCancelled: () -> Unit = {},
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val applier = DeltaApplierClient(logger, hashProgress, checkCancelled)
    private val luceneUpdater = LuceneUpdater(logger, checkCancelled)
    private val catalogUpdater = CatalogUpdater(logger)
    private val orchestrator = UpdateOrchestrator(applier, downloader, luceneUpdater, catalogUpdater, workDir, logger, onDecompress, checkCancelled)

    init {
        Files.createDirectories(workDir)
    }

    /** Desktop restores a backup here; on Android: whether a half-applied delta waits to be resumed ([pendingChain]). */
    fun recoverIfNeeded(): Boolean = applier.recoverIfNeeded(seforimDb)

    /** The unfinished delta as a one-step chain, with the release tag recorded for it, or null. */
    fun pendingChain(): Pair<List<DeltaEntry>, String?>? {
        val pending = applier.pending(seforimDb) ?: return null
        val manifestUrl = pending.manifestUrl ?: return null
        return listOf(DeltaEntry(pending.fromVersion, pending.toVersion, manifestUrl, totalSize = 0L)) to pending.releaseTag
    }

    /** `release_meta.json`, or null when the server has none (HTTP 404). Other failures throw. */
    fun fetchReleaseMeta(): ReleaseMeta? {
        val body = try {
            httpGet(releaseMetaUrl)
        } catch (e: Exception) {
            if (isNotFound(e)) {
                logger.i { "release_meta.json not found at $releaseMetaUrl" }
                return null
            }
            throw e
        }
        return json.decodeFromString<ReleaseMeta>(body)
    }

    /**
     * Polls the server's `release_meta.json` and picks an update path (desktop). A 404 is treated as
     * [UpdatePath.UpToDate]: the server has never published one, which is not a user-facing error.
     */
    fun checkForUpdate(): UpdatePath {
        val meta = fetchReleaseMeta() ?: return UpdatePath.UpToDate
        return choosePath(localVersionProvider(), meta)
    }

    /** Applies a chain of deltas onto the live `seforim.db`. */
    fun applyChain(
        chain: List<DeltaEntry>,
        progress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> },
        releaseTagFor: (DeltaEntry) -> String? = { null },
        onDeltaApplied: (DeltaEntry) -> Unit = {},
    ) {
        orchestrator.applyChain(
            seforimDb = seforimDb,
            catalogPb = catalogPb,
            chain = chain,
            fetchManifest = { entry -> fetchManifest(entry) },
            baseUrlForEntry = urlForPatchFile,
            luceneSinks = indexSinks,
            progress = progress,
            releaseTagFor = releaseTagFor,
            onDeltaApplied = onDeltaApplied,
        )
    }

    fun fetchManifest(entry: DeltaEntry): DeltaManifest = json.decodeFromString(httpGet(entry.manifestUrl))

    companion object {
        /**
         * Desktop's [chooseUpdatePath], after looking for one patch straight from [localVersion] to the latest version
         * (the release workflow's patch fan), taken when it is cheaper than [fallbackRatio] of the full bundle.
         */
        fun choosePath(localVersion: Int, meta: ReleaseMeta, fallbackRatio: Double = 0.7): UpdatePath {
            if (localVersion >= meta.latestVersion) return UpdatePath.UpToDate
            val direct = meta.deltas.firstOrNull { it.fromVersion == localVersion && it.toVersion == meta.latestVersion }
            val bundleSize = meta.fullBundle.size.takeIf { it > 0 } ?: Long.MAX_VALUE
            if (direct != null && direct.totalSize <= bundleSize * fallbackRatio) return UpdatePath.Chain(listOf(direct))
            // An unknown bundle size (0) must not make every chain "more expensive than the bundle".
            return when (val path = chooseUpdatePath(localVersion, meta.copy(fullBundle = meta.fullBundle.copy(size = bundleSize)), fallbackRatio)) {
                is UpdatePath.FullBundle -> UpdatePath.FullBundle(meta.fullBundle)
                else -> path
            }
        }
    }
}
