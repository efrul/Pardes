package io.github.kdroidfilter.seforimapp.features.siddur

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.e2e.E2eScenario

/**
 * Pardes's build: the open smart siddur, on SeforimOpenSiddur's engine (siddur-open) and the library's own siddurim.
 * Experimental, so it is there only once turned on in the settings ([OpenSiddurSwitch]); until then the app shows no
 * siddur, as a community build.
 */
val installedSiddur: SiddurFeature?
    get() = openSiddur.takeIf { OpenSiddurSwitch.shown }

private val openSiddur =
    object : SiddurFeature {
        @Composable
        override fun TabContent(
            tabId: String,
            destination: TabsDestination.Siddur,
        ) = SiddurTabContent(
            tabId = tabId,
            initialPart = destination.part,
            initialEpochDay = destination.epochDay,
            initialHeading = destination.heading,
        )

        // The siddur's own scenario walks the private package's templates
        override suspend fun runE2e(sc: E2eScenario) = Unit
    }
