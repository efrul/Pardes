package io.github.kdroidfilter.seforimapp.logger

// The app's logger, without Sentry: enough for the semantic package outside the app
inline fun infoln(message: () -> String) = System.err.println("[info] " + message())

inline fun warnln(message: () -> String) = System.err.println("[warn] " + message())

inline fun warnln(
    throwable: Throwable,
    message: () -> String = { throwable.message ?: "Warning" },
) = System.err.println("[warn] " + message() + ": " + throwable)
