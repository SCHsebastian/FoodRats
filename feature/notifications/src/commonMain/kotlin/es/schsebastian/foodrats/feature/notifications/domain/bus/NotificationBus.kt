package es.schsebastian.foodrats.feature.notifications.domain.bus

import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App-scoped conduit the OS-side push receivers post to. Observed by the root composable
 * (`InAppPushBanner`) to show in-app banners; bound as a Koin single so emitters and the
 * observer share it.
 *
 * Backed by a buffered [Channel] (mirroring `DeepLinkBus`), NOT a replay-less `SharedFlow`:
 * the banner collector runs only while the UI is RESUMED, and a shared flow with no subscriber
 * **drops** emissions — so a push landing while the host was paused (system dialog, shade) or
 * during the cold-start window before first composition would silently vanish. The channel
 * buffers those reminders and delivers each exactly once when collection (re)starts; on
 * overflow the oldest is discarded (a stale banner is the right thing to lose). Single-consumer
 * by contract — the root `InAppPushBanner` is the only collector.
 */
class NotificationBus {
    private val channel = Channel<Reminder>(
        capacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Cold flow of incoming reminders. Single-consumer (the root in-app banner). */
    val stream: Flow<Reminder> = channel.receiveAsFlow()

    /** Never suspends in practice (DROP_OLDEST); kept suspending for call-site compatibility. */
    suspend fun publish(reminder: Reminder) {
        channel.send(reminder)
    }
}
