package io.github.kdroidfilter.seforimapp.branding

import dev.nucleusframework.updater.provider.UpdateProvider

/**
 * Upstream Zayit's updates (branding/zayit, -Pseforimapp.brand=zayit): no provider of its own, so AppUpdateService builds
 * upstream's GitHubProvider(owner = "kdroidFilter", repo = "Zayit") as released.
 */
object BrandUpdates {
    val provider: UpdateProvider? = null
}
