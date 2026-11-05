package io.github.kdroidfilter.seforimapp.features.settings.feedback

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.logger.warnln
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.NetworkInterface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Feedback sent by itself. Once, at a start of a build that has the service ([FeedbackService.available]), the user is
 * asked whether to send feedback and which kinds ([askVisible], FeedbackConsentDialog); then, while they agree, what is
 * new is sent at every start and again whenever the computer's network comes back ([NetworkWatch]). Without a network,
 * it is sent at the next connection, or tried again within [RETRY].
 */
@Inject
@SingleIn(AppScope::class)
class FeedbackAutoSender(
    private val appSettings: AppSettings,
    private val sender: FeedbackSender,
) {
    /** When the network comes back; the tests give their own. */
    internal var connections: Flow<Unit> = NetworkWatch().connections()

    private val _askVisible = MutableStateFlow(false)

    /** Whether the question is shown. */
    val askVisible: StateFlow<Boolean> = _askVisible.asStateFlow()

    // A sending asked for: at start, at a connection, by the user; one waiting is enough
    private val triggers = Channel<Unit>(Channel.CONFLATED)

    /** The kinds the question offers checked: those the user sends. */
    fun choice(): FeedbackChoice = sender.choice()

    /**
     * Runs while the app does (main.kt): asks once [ready] says nothing else is in the way, unless the user answered
     * already, then sends what is new while they agree: now, at every connection of the network, and on [kick].
     */
    suspend fun run(ready: suspend () -> Unit): Unit =
        coroutineScope {
            if (!sender.service.available) return@coroutineScope
            if (!appSettings.isFeedbackAsked()) {
                ready()
                _askVisible.value = true
            }
            launch { connections.collect { kick() } }
            kick()
            while (true) {
                triggers.receive()
                if (!appSettings.isFeedbackAutoSend()) continue
                val problem = runCatching { sender.sendNew() }.exceptionOrNull() ?: continue
                when {
                    problem is CancellationException -> throw problem
                    // No network: at the next connection, or in a while
                    problem is IOException -> launch { later(RETRY) }
                    problem is FeedbackServiceProblem && problem.tooMany -> launch { later(LATER) }
                    else -> warnln { "[FeedbackAutoSender] Feedback not sent: ${problem.message}" }
                }
            }
        }

    /** Sends what is new now, if the user agreed. */
    fun kick() {
        triggers.trySend(Unit)
    }

    private suspend fun later(wait: Duration) {
        delay(wait)
        kick()
    }

    /** Whether the question offers the grade buttons checked: yes, unless the user has them already. */
    fun buttonsOffered(): Boolean = !appSettings.isSearchFeedbackEnabled()

    /**
     * The user's answer: [agree] to send [choice]'s kinds by itself, or not, and with a yes, [buttons] to show the grade
     * buttons under the search results (feedback mode) from now on. Either way, not asked again.
     */
    fun answer(
        agree: Boolean,
        choice: FeedbackChoice,
        buttons: Boolean = false,
    ) {
        if (agree) sender.keep(choice)
        if (agree && buttons && choice.grades) appSettings.setSearchFeedbackEnabled(true)
        appSettings.setFeedbackAutoSend(agree)
        appSettings.setFeedbackAsked(true)
        appSettings.flush()
        _askVisible.value = false
        if (agree) kick()
    }

    /** The question closed without an answer: asked again at the next start. */
    fun dismiss() {
        _askVisible.value = false
    }

    companion object {
        val RETRY: Duration = 15.minutes
        val LATER: Duration = 1.hours
    }
}

/**
 * The computer's network, as its interfaces tell: a connection is an address on an interface that is up which was not
 * there [period] before (Wi-Fi joined, a cable plugged, a VPN up). Read every [period], off the UI thread.
 */
class NetworkWatch(
    private val period: Duration = 30.seconds,
    private val addresses: () -> Set<String> = ::upAddresses,
) {
    fun connections(): Flow<Unit> =
        flow {
            var last = addresses()
            while (true) {
                delay(period)
                val now = addresses()
                if ((now - last).isNotEmpty()) emit(Unit)
                last = now
            }
        }.flowOn(Dispatchers.IO)

    companion object {
        /** The addresses of the interfaces that are up, but the loopback's and the link-local ones. */
        fun upAddresses(): Set<String> =
            runCatching {
                NetworkInterface
                    .networkInterfaces()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { nic ->
                        nic
                            .inetAddresses()
                            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                            .map { "${nic.name}/${it.hostAddress}" }
                    }.toList()
                    .toSet()
            }.getOrDefault(emptySet())
    }
}
