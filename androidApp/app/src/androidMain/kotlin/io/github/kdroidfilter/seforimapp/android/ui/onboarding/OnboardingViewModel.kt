package io.github.kdroidfilter.seforimapp.android.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.android.ZayitApplication
import io.github.kdroidfilter.seforimapp.android.data.InstallError
import io.github.kdroidfilter.seforimapp.android.data.InstallPurpose
import io.github.kdroidfilter.seforimapp.android.data.InstallState
import io.github.kdroidfilter.seforimapp.android.data.LibraryInstaller
import io.github.kdroidfilter.seforimapp.android.data.LibraryLocation
import io.github.kdroidfilter.seforimapp.android.data.worldPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Steps of the desktop onboarding (OnBoardingDestination), with the top progress bar value of each. */
internal enum class OnboardingStep(val anchor: Float) {
    Init(0f),
    Licence(0.1f),
    DiskSpace(0.2f),
    InstallType(0.3f),
    Download(0.3f),
    OfflineFiles(0.5f),
    Extract(0.7f),
    Profile(0.85f),
    Region(0.9f),
    Finish(1f),
}

/** Communities of the desktop profile step; [code] is the value desktop stores (AppSettings user community code). */
internal enum class Community(val code: String, val label: String) {
    Sepharade("SEPHARADE", DesktopStrings.onboardingUserCommunitySepharade),
    Ashkenaze("ASHKENAZE", DesktopStrings.onboardingUserCommunityAshkenaze),
    Sefard("SEFARD", DesktopStrings.onboardingUserCommunitySefard),
}

/** A place the library can go (the phone or a memory card), with its free space now. Android only. */
internal data class LocationChoice(val location: LibraryLocation, val space: LibraryInstaller.DiskSpace) {
    val fits: Boolean get() = space.availableBytes >= LibraryInstaller.REQUIRED_SPACE_BYTES

    val usable: Boolean get() = fits
}

internal data class DiskState(
    val loading: Boolean = true,
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    /** Android: the phone and the memory cards, when there is a card to choose. */
    val choices: List<LocationChoice> = emptyList(),
    val selectedKey: String = LibraryLocation.PHONE,
) {
    val requiredBytes: Long get() = LibraryInstaller.REQUIRED_SPACE_BYTES
    val selected: LocationChoice? get() = choices.firstOrNull { it.location.key == selectedKey }
    val enough: Boolean get() = selected?.usable ?: (availableBytes >= requiredBytes)
}

/** What the install-type and offline steps found on the device. */
internal data class DeviceState(
    val existingLibraryBytes: Long? = null,
    val existingLibraryError: InstallError? = null,
    val checkingExisting: Boolean = false,
    val localBundle: List<File> = emptyList(),
    val metered: Boolean = false,
    val pickedFiles: Int = 0,
)

internal data class ProfileState(val firstName: String = "", val lastName: String = "", val communityIndex: Int = -1) {
    val canProceed: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank() && communityIndex >= 0
}

internal data class RegionState(val countryIndex: Int = -1, val cityIndex: Int = -1) {
    val canProceed: Boolean get() = countryIndex >= 0 && cityIndex >= 0
}

/** Navigation and form state of the first-run flow; the install itself lives in [LibraryInstaller]. */
internal class OnboardingViewModel(app: ZayitApplication) : ViewModel() {
    val installer: LibraryInstaller = LibraryInstaller.get(app)
    private val prefs = app.prefs
    private val store = app.libraryStore

    private val _stack = MutableStateFlow(initialStack(installer.state.value))
    val stack: StateFlow<List<OnboardingStep>> = _stack.asStateFlow()
    private val step: OnboardingStep get() = _stack.value.last()

    private val _licenceAccepted = MutableStateFlow(false)
    val licenceAccepted: StateFlow<Boolean> = _licenceAccepted.asStateFlow()

    private val _disk = MutableStateFlow(DiskState())
    val disk: StateFlow<DiskState> = _disk.asStateFlow()

    private val _device = MutableStateFlow(DeviceState())
    val device: StateFlow<DeviceState> = _device.asStateFlow()

    private val _profile = MutableStateFlow(
        ProfileState(
            firstName = prefs.firstName,
            lastName = prefs.lastName,
            communityIndex = Community.entries.indexOfFirst { it.code.equals(prefs.community, ignoreCase = true) },
        ),
    )
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()

    /** Countries and cities of desktop's worldPlaces table, in its order (RegionConfigUseCase). */
    val countries: List<String> = worldPlaces.keys.toList()

    // RegionConfigViewModel: starts from the saved region when there is one, else with nothing chosen.
    private val _region = MutableStateFlow(
        countries.indexOf(prefs.savedCountry).let { country ->
            RegionState(country, if (country < 0) -1 else citiesOf(country).indexOf(prefs.savedCity))
        },
    )
    val region: StateFlow<RegionState> = _region.asStateFlow()

    init {
        viewModelScope.launch { installer.state.collect { onInstallState(it) } }
        if (!installer.isBusy) refreshDevice()
        // A memory card put in or taken out while the disk step is open: list the places again.
        viewModelScope.launch {
            app.mediaChanges.drop(1).collect { if (step == OnboardingStep.DiskSpace) refreshDisk() }
        }
    }

    fun citiesOf(countryIndex: Int): List<String> {
        val country = countries.getOrNull(countryIndex) ?: return emptyList()
        return worldPlaces[country]?.keys?.toList().orEmpty()
    }

    // ------------------------------------------------------------ navigation

    fun go(next: OnboardingStep) {
        // A double tap must not push the same step twice.
        if (_stack.value.last() == next) return
        _stack.value = _stack.value + next
        when (next) {
            OnboardingStep.DiskSpace -> refreshDisk()
            OnboardingStep.InstallType, OnboardingStep.OfflineFiles -> refreshDevice()
            else -> Unit
        }
    }

    fun back(): Boolean {
        val current = _stack.value
        if (current.size <= 1) return false
        // Leaving a failed download or import: forget it, so the next step does not show its error.
        if (current.last() in InstallSteps && !installer.isBusy) installer.reset()
        _stack.value = current.dropLast(1)
        return true
    }

    /** Moves along with the installer, as desktop's Download/Extract screens navigate on completion. */
    private fun onInstallState(state: InstallState) {
        val current = step
        when {
            state is InstallState.Extracting && current == OnboardingStep.OfflineFiles -> replaceCurrent(OnboardingStep.Extract)
            state is InstallState.Verifying && (current == OnboardingStep.Download || current == OnboardingStep.OfflineFiles) ->
                replaceCurrent(OnboardingStep.Extract)
            state is InstallState.Done && current in InstallSteps -> {
                _stack.value = listOf(OnboardingStep.Profile)
            }
            // Cancelled (e.g. from the notification) while the download or extraction was on screen.
            state is InstallState.Idle && (current == OnboardingStep.Download || current == OnboardingStep.Extract) -> backToInstallType()
            else -> Unit
        }
    }

    private fun replaceCurrent(next: OnboardingStep) {
        _stack.value = _stack.value.dropLast(1) + next
    }

    // ------------------------------------------------------------ steps

    fun setLicenceAccepted(accepted: Boolean) {
        _licenceAccepted.value = accepted
    }

    /**
     * Desktop LicenceScreen: a library that is already there and compatible (DatabaseVersionManager) skips the install
     * steps; anything else, missing or outdated, goes through the disk check and a new install.
     */
    fun licenceNext() {
        viewModelScope.launch {
            val compatible = withContext(Dispatchers.IO) { runCatching { installer.checkInstalledLibrary() == null }.getOrDefault(false) }
            go(if (compatible) OnboardingStep.Profile else OnboardingStep.DiskSpace)
        }
    }

    fun refreshDisk() {
        viewModelScope.launch {
            _disk.update { it.copy(loading = true) }
            _disk.value = withContext(Dispatchers.IO) {
                installer.discardStaleDownload()
                store.refreshLocations()
                val choices = store.locations.map { LocationChoice(it, it.space()) }
                // The chosen place, unless it cannot take the library and another one can (a phone that is full, and a card).
                val chosen = choices.firstOrNull { it.location.key == store.preferredKey } ?: choices.first()
                val selected = if (!chosen.usable) choices.firstOrNull { it.usable } ?: chosen else chosen
                store.setPreferred(selected.location)
                diskState(choices, selected)
            }
        }
    }

    /** The library goes to [key] (the phone, or a memory card's UUID). */
    fun selectLocation(key: String) {
        val choices = _disk.value.choices
        val choice = choices.firstOrNull { it.location.key == key } ?: return
        store.setPreferred(choice.location)
        _disk.value = diskState(choices, choice)
    }

    private fun diskState(choices: List<LocationChoice>, selected: LocationChoice) = DiskState(
        loading = false,
        totalBytes = selected.space.totalBytes,
        availableBytes = selected.space.availableBytes,
        choices = choices.takeIf { it.size > 1 }.orEmpty(),
        selectedKey = selected.location.key,
    )

    private fun refreshDevice() {
        viewModelScope.launch {
            // Only a library this version can use is offered (one pushed by adb while this step is open, say): an
            // outdated one is what brought the user here, as desktop's licence step decides.
            val existing = withContext(Dispatchers.IO) {
                runCatching { if (installer.checkInstalledLibrary() == null) installer.installedLibrarySize() else null }.getOrNull()
            }
            val local = withContext(Dispatchers.IO) { runCatching { installer.findLocalBundle() }.getOrDefault(emptyList()) }
            val metered = withContext(Dispatchers.IO) { installer.isMeteredNetwork() }
            _device.update { it.copy(existingLibraryBytes = existing, localBundle = local, metered = metered) }
        }
    }

    fun startOnline() {
        installer.purpose = InstallPurpose.Onboarding
        installer.startOnline()
        if (step != OnboardingStep.Download) go(OnboardingStep.Download)
    }

    fun useExistingLibrary() {
        viewModelScope.launch {
            _device.update { it.copy(checkingExisting = true, existingLibraryError = null) }
            val problem = withContext(Dispatchers.IO) { runCatching { installer.checkInstalledLibrary() }.getOrDefault(InstallError.Incompatible) }
            _device.update { it.copy(checkingExisting = false, existingLibraryError = problem) }
            if (problem == null) go(OnboardingStep.Profile)
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _device.update { it.copy(pickedFiles = uris.size) }
        // A picker result that comes back to a recreated activity finds a new view model on the welcome step: back to
        // the offline step (as initialStack does for a running import), so the flow follows the install it starts
        if (step != OnboardingStep.OfflineFiles && !installer.isBusy) {
            _stack.value = listOf(OnboardingStep.InstallType, OnboardingStep.OfflineFiles)
        }
        installer.purpose = InstallPurpose.Onboarding
        installer.importUris(uris)
    }

    fun importLocalBundle() {
        installer.purpose = InstallPurpose.Onboarding
        installer.importFiles(_device.value.localBundle)
    }

    fun retry() {
        installer.purpose = InstallPurpose.Onboarding
        installer.retry()
    }

    /** Stops the download and returns to the install type, which desktop does not offer. */
    fun cancelInstall() {
        installer.cancel()
        backToInstallType()
    }

    /** "התחל התקנה מחדש" after an incompatible database, like desktop's VersionVerificationScreen. */
    fun restartInstallation() {
        installer.reset()
        backToInstallType()
    }

    private fun backToInstallType() {
        val kept = _stack.value.takeWhile { it !in InstallSteps }
        _stack.value = if (kept.lastOrNull() == OnboardingStep.InstallType) kept else kept + OnboardingStep.InstallType
        refreshDevice()
    }

    fun setFirstName(value: String) = _profile.update { it.copy(firstName = value) }

    fun setLastName(value: String) = _profile.update { it.copy(lastName = value) }

    fun setCommunity(index: Int) = _profile.update { it.copy(communityIndex = index) }

    fun profileNext() {
        val state = _profile.value
        val community = Community.entries.getOrNull(state.communityIndex) ?: return
        prefs.firstName = state.firstName.trim()
        prefs.lastName = state.lastName.trim()
        prefs.community = community.code
        go(OnboardingStep.Region)
    }

    fun setCountry(index: Int) {
        // Desktop resets the city when the country changes.
        _region.value = RegionState(countryIndex = index, cityIndex = -1)
    }

    fun setCity(index: Int) = _region.update { it.copy(cityIndex = index) }

    fun regionNext() {
        val state = _region.value
        val country = countries.getOrNull(state.countryIndex) ?: return
        val city = citiesOf(state.countryIndex).getOrNull(state.cityIndex) ?: return
        prefs.setRegion(country, city)
        go(OnboardingStep.Finish)
    }

    companion object {
        /** Steps that belong to a running install: back does not leave them (desktop clears its back stack). */
        val InstallSteps = setOf(OnboardingStep.Download, OnboardingStep.OfflineFiles, OnboardingStep.Extract)

        /** Where to reopen when the activity comes back while the installer is still working (or done). */
        private fun initialStack(state: InstallState): List<OnboardingStep> = when (state) {
            InstallState.Idle -> listOf(OnboardingStep.Init)
            is InstallState.Preparing -> listOf(OnboardingStep.InstallType, if (state.online) OnboardingStep.Download else OnboardingStep.OfflineFiles)
            is InstallState.Downloading -> listOf(OnboardingStep.InstallType, OnboardingStep.Download)
            is InstallState.Extracting -> listOf(OnboardingStep.InstallType, OnboardingStep.Extract)
            InstallState.Verifying -> listOf(OnboardingStep.InstallType, OnboardingStep.Extract)
            InstallState.Done -> listOf(OnboardingStep.Profile)
            is InstallState.Failed -> listOf(OnboardingStep.InstallType, if (state.online) OnboardingStep.Download else OnboardingStep.Extract)
        }

        fun factory(app: ZayitApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(app) as T
        }
    }
}
