package io.github.kdroidfilter.seforimapp.features.settings.feedback

import java.io.File

/** What the feedback page's checkboxes and buttons do. */
internal class FeedbackSendActions(
    val grades: (Boolean) -> Unit = {},
    val searches: (Boolean) -> Unit = {},
    val daf: (Boolean) -> Unit = {},
    // Another app's searches sent too, or not
    val otherApp: (OtherApp, Boolean) -> Unit = { _, _ -> },
    // Whether what is sent may be shown to everyone
    val publish: (Boolean) -> Unit = {},
    // A query kept in the package or left out of it
    val query: (String, Boolean) -> Unit = { _, _ -> },
    val all: (Boolean) -> Unit = {},
    val renewSender: () -> Unit = {},
    // The automatic sending on or off
    val auto: (Boolean) -> Unit = {},
    // The grade buttons under each search result, shown or not
    val buttons: (Boolean) -> Unit = {},
    val send: () -> Unit = {},
    val deleteSent: () -> Unit = {},
    val save: () -> Unit = {},
    val openFolder: (File) -> Unit = {},
)
