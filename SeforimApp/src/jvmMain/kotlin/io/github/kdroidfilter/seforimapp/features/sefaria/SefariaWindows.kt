package io.github.kdroidfilter.seforimapp.features.sefaria

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup
import io.github.kdroidfilter.seforimapp.features.home.widgets.ToolWindows
import io.github.kdroidfilter.seforimapp.features.sefaria.sages.SagesTool
import io.github.kdroidfilter.seforimapp.features.sefaria.topics.TopicsTool
import io.github.kdroidfilter.seforimapp.framework.di.AppScope

/**
 * Whether the reader shows the topics of its line in a pane beside the text (LineTopicsPane), one setting for every
 * tab; and whether a search shows the topic its query names beside its results (SearchTopicCard), until it is hidden.
 */
object SefariaSettings : SettingsGroup("sefaria") {
    val topicsPane by boolean(false)
    val searchTopicCard by boolean(true)
}

/**
 * The windows of Sefaria's topics and persons, opened on a topic or a person from outside them (the reader's pane, a
 * name in the Talmud, the search): what was asked for waits here, with a count of the asks, for the window to take it.
 */
@Inject
@SingleIn(AppScope::class)
class SefariaWindows(
    private val toolWindows: ToolWindows,
) {
    internal var topic by mutableStateOf<Int?>(null)
    internal var topicAsks by mutableIntStateOf(0)
    internal var sage by mutableStateOf<Int?>(null)
    internal var sageAsks by mutableIntStateOf(0)

    /** Opens the topics' window on topic [id]. */
    fun openTopic(id: Int) {
        topic = id
        topicAsks++
        toolWindows.open(TopicsTool)
    }

    /** Opens the persons' window on person [id] (or a name Sefaria could not tell between persons). */
    fun openSage(id: Int) {
        sage = id
        sageAsks++
        toolWindows.open(SagesTool)
    }

    internal companion object {
        /** A name's link in the Talmud (`seforim://sage/<id>`, SageLinks), as the reader's links are written. */
        const val SAGE_LINK = "seforim://sage/"

        /** The person a name's link points to, or null for any other URI. */
        fun sageOf(uri: String): Int? = uri.takeIf { it.startsWith(SAGE_LINK) }?.removePrefix(SAGE_LINK)?.toIntOrNull()
    }
}
