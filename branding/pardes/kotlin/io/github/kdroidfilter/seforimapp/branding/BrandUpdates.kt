package io.github.kdroidfilter.seforimapp.branding

import dev.nucleusframework.updater.provider.UpdateProvider

/**
 * Where Pardes for desktop finds its updates (branding/zayit has upstream Zayit's). AppUpdateService's updater takes
 * [provider] after its SEFORIMAPP_UPDATE_FEED_URL override and before upstream's GitHubProvider of kdroidFilter/Zayit,
 * which a Pardes build must never reach: it would download Zayit's installer and run it over Pardes.
 */
object BrandUpdates {
    /** The `desktop-*` releases of efrul/Pardes. Nullable as Zayit's is, which leaves the updates to upstream's provider. */
    val provider: UpdateProvider? = PardesDesktopReleases()
}
