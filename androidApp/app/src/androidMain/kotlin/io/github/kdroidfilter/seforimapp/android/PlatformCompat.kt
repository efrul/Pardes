package io.github.kdroidfilter.seforimapp.android

import android.app.Notification
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream

/*
 * Platform calls newer than Android 8.0 (API 26) that several places make. Where Android has them they are made as
 * before; below, the older call that does the same, or none where the older Android already behaves that way.
 */

/**
 * Android 12+ may hold a foreground service's notification back for up to 10 s: this one shows at once, as every one
 * does below Android 12. Notification.Builder.setForegroundServiceBehavior is API 31: below 12 it would throw while
 * the notification is built, before startForeground, and Android ends the app when a service started for the
 * foreground stops without it.
 */
internal fun Notification.Builder.showForegroundImmediately(): Notification.Builder = apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
}

/** getPackageInfo with [flags]: the PackageInfoFlags overload is API 33, the int one (deprecated since) does the same. */
@Suppress("DEPRECATION")
internal fun PackageManager.packageInfoCompat(packageName: String, flags: Long): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
    } else {
        getPackageInfo(packageName, flags.toInt())
    }

/** getPackageArchiveInfo with [flags], as [packageInfoCompat]: null when Android cannot read [path] as an APK. */
@Suppress("DEPRECATION")
internal fun PackageManager.packageArchiveInfoCompat(path: String, flags: Long): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags))
    } else {
        getPackageArchiveInfo(path, flags.toInt())
    }

/** InputStream.readNBytes(int), API 33: up to [limit] bytes, fewer only at the end of the stream. Below, the same loop. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return readNBytes(limit)
    val out = ByteArrayOutputStream(minOf(limit, READ_CHUNK))
    val chunk = ByteArray(minOf(limit, READ_CHUNK))
    var left = limit
    while (left > 0) {
        val n = read(chunk, 0, minOf(chunk.size, left))
        if (n < 0) break
        out.write(chunk, 0, n)
        left -= n
    }
    return out.toByteArray()
}

private const val READ_CHUNK = 8192
