package io.github.kdroidfilter.seforimlibrary.core.models

// Stand-in for SeforimLibrary core's Category (without its Compose and serialization annotations), for the harness build.
data class Category(
    val id: Long = 0,
    val parentId: Long? = null,
    val title: String,
    val level: Int = 0,
    val order: Int = 999,
)
