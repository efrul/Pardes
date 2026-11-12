package io.github.kdroidfilter.seforimapp.logger

// The app's debug lines (Logger.kt's debugln), which the semantic package writes: nothing here
inline fun debugln(message: () -> String) = Unit
