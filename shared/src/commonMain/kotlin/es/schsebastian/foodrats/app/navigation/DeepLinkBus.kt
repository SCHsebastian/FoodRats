package es.schsebastian.foodrats.app.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot conduit for external URIs (Android App Links / custom scheme; iOS Universal Links /
 * custom scheme). Platform entry points publish the raw URI string; [RootNavViewModel] is the
 * single consumer — it parses the URI via [parseDeepLink] and decides whether to navigate now or
 * stash it until the user clears the auth gate.
 *
 * Backed by a [Channel.CONFLATED] so a link delivered at cold start (before the consumer starts
 * collecting) survives until then, while a recreated consumer never replays a stale link.
 */
class DeepLinkBus {
    private val channel = Channel<String>(Channel.CONFLATED)

    /** Cold flow of incoming URIs. Single-consumer (the root navigation ViewModel). */
    val uris: Flow<String> = channel.receiveAsFlow()

    /** Called from platform entry points with the raw incoming URI string. Never suspends. */
    fun publish(uri: String) {
        channel.trySend(uri)
    }
}
