package io.github.kdroidfilter.seforimapp.android.search

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Android only: whether this device can run Pardes's semantic search (SEMANTIC_SEARCH.md). ONNX Runtime ships in the
 * APK for the 64-bit ABIs alone (app/build.gradle.kts leaves its 32-bit libraries out), so a 32-bit process has no
 * runtime for the model; and a device Android counts as low on memory (Android Go) is left without it too: once loaded,
 * the model and its tokenizer take some 240 MB (185 MB native, 45 MB of Java heap, measured on the emulator on
 * 2026-10-29). There the settings hide the pack, and the search stays lexical even when a pack was copied in.
 */
object SemanticSearchSupport {
    /** The ABIs the APK carries ONNX Runtime for. */
    val ABIS = setOf("arm64-v8a", "x86_64")

    private val abiSupported: Boolean by lazy {
        Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.firstOrNull() in ABIS
    }

    @Volatile
    private var lowRam = false

    /** Reads whether the device is low on memory; ZayitApplication.onCreate calls it before anything asks [supported]. */
    fun init(context: Context) {
        lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }

    val supported: Boolean get() = abiSupported && !lowRam
}
