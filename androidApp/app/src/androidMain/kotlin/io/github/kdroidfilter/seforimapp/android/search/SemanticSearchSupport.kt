package io.github.kdroidfilter.seforimapp.android.search

import android.os.Build
import android.os.Process

/**
 * Android only: whether this device can run Pardes's semantic search (SEMANTIC_SEARCH.md). ONNX Runtime ships in the
 * APK for the 64-bit ABIs alone (app/build.gradle.kts leaves its 32-bit libraries out), so a 32-bit process has no
 * runtime for the model: the settings hide the pack, and the search stays lexical even when a pack was copied in.
 */
object SemanticSearchSupport {
    /** The ABIs the APK carries ONNX Runtime for. */
    val ABIS = setOf("arm64-v8a", "x86_64")

    val supported: Boolean by lazy {
        Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.firstOrNull() in ABIS
    }
}
