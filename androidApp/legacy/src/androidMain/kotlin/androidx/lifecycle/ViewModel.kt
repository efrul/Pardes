package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The part of AndroidX's ViewModel that the main app's search view models use (SearchViewModel, SearchBarViewModel,
 * shared through sharedAppSources), as Stable.kt stands in for Compose's annotation: AndroidX lifecycle needs a
 * higher minSdk than this edition's. An activity keeps the instance across a rotation (onRetainNonConfigurationInstance)
 * and calls [clear] when it finishes for good, which cancels [viewModelScope] and runs [onCleared].
 */
abstract class ViewModel {
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected open fun onCleared() = Unit

    fun clear() {
        scope.cancel()
        onCleared()
    }
}

/** As lifecycle-viewmodel-ktx's: Main.immediate with a SupervisorJob, cancelled by [ViewModel.clear]. */
val ViewModel.viewModelScope: CoroutineScope get() = scope

/** The factories the shared view models offer (their companions' factory()): [Factory.create] builds one. */
class ViewModelProvider private constructor() {
    interface Factory {
        fun <T : ViewModel> create(modelClass: Class<T>): T
    }
}
