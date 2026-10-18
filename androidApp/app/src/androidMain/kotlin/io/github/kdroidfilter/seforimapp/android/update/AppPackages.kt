package io.github.kdroidfilter.seforimapp.android.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.kdroidfilter.seforimapp.android.packageArchiveInfoCompat
import io.github.kdroidfilter.seforimapp.android.packageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * The installed app and a downloaded APK as Android sees them: their package, version and signing certificates. The
 * certificate is what makes an APK an update of this app; the sha256 of the download only proves it arrived whole (the
 * app trusts the user's CAs, for the filtering proxies, so whoever the user let in could change both the release list
 * and the file).
 */
internal object AppPackages {
    /**
     * SHA-256 of the certificate that signs Pardes' releases (android-release.yml RELEASE_CERT_SHA256, which the
     * workflow checks every published APK against). An installed app signed with another key (the public debug key of
     * development builds) cannot take a release over it.
     */
    const val RELEASE_CERT_SHA256 = "293bf2a28ff954ef624cbee0292deb5b0d23819a93d4122a33483e2bc2d52772"

    /** What an APK is, as far as replacing the installed app goes. */
    class Identity(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        /** SHA-256 of each certificate that signs its contents now. */
        val signers: Set<String>,
        /** Its past and present certificates, oldest first (v3 key rotation), when it has one signer. */
        val lineage: List<String>,
        val minSdk: Int,
    )

    /** The installed app. */
    fun installed(context: Context): Identity? = runCatching {
        identityOf(context.packageManager.packageInfoCompat(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
    }.getOrNull()

    /**
     * A downloaded APK, or null when Android cannot read it as one (a truncated or foreign file). Android 13's first
     * releases (android-13.0.0_r1 to r13) collected an archive's certificates only for GET_SIGNATURES, leaving
     * signingInfo null for GET_SIGNING_CERTIFICATES alone: both are asked (no signers = no update).
     */
    @Suppress("DEPRECATION")
    fun archive(context: Context, apk: File): Identity? = runCatching {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES.toLong() or PackageManager.GET_SIGNATURES.toLong()
        context.packageManager.packageArchiveInfoCompat(apk.path, flags)?.let(::identityOf)
    }.getOrNull()

    /** This build is one of Pardes' published releases (not a development build, signed with the public debug key). */
    fun isReleaseBuild(context: Context): Boolean = installed(context)?.signers == setOf(RELEASE_CERT_SHA256)

    /**
     * [apk] signs as [installed] does: the same certificates, or, for a single signer, a lineage that vouches for the
     * installed one (a rotated key, which Android accepts as the same app).
     */
    fun sameSigner(installed: Identity, apk: Identity): Boolean {
        if (installed.signers.isEmpty() || apk.signers.isEmpty()) return false
        if (installed.signers == apk.signers) return true
        return installed.signers.size == 1 && apk.signers.size == 1 && installed.signers.single() in apk.lineage
    }

    private fun identityOf(info: PackageInfo): Identity {
        val signing = info.signingInfo
        val signers = signing?.apkContentsSigners.orEmpty().map(::sha256).toSet()
        val lineage = if (signing != null && !signing.hasMultipleSigners()) signing.signingCertificateHistory.orEmpty().map(::sha256) else emptyList()
        return Identity(
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            signers = signers,
            lineage = lineage,
            // Not known: left to PackageInstaller, which refuses an APK this Android cannot run (STATUS_FAILURE_INCOMPATIBLE)
            minSdk = info.applicationInfo?.minSdkVersion ?: Build.VERSION.SDK_INT,
        )
    }

    private fun sha256(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
}
