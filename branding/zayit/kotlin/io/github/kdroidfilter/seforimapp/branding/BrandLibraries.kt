package io.github.kdroidfilter.seforimapp.branding

import java.io.File

/**
 * Upstream Zayit (branding/zayit, -Pseforimapp.brand=zayit): no other app's library is offered or kept, so the
 * onboarding and the library cleanup behave as upstream's.
 */
object BrandLibraries {
    /** The app whose library is offered: none. */
    const val OTHER_APP_NAME = ""

    /** Where another app keeps the library this app offers to use in place: nowhere. */
    fun otherAppDatabases(): List<File> = emptyList()
}
