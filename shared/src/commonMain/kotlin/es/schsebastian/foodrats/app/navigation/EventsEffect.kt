package es.schsebastian.foodrats.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.flow.Flow

/**
 * Collect one-shot [events] (a [kotlinx.coroutines.channels.Channel]-backed flow of effects) and
 * run [onEvent] only while the host is at least [Lifecycle.State.RESUMED].
 *
 * Uses [repeatOnLifecycle], which **cancels** collection when the lifecycle drops below RESUMED and
 * **restarts** it on the way back up. While collection is suspended the upstream Channel keeps
 * buffering, so an effect emitted while the screen is paused is **deferred and delivered on resume,
 * never dropped**. Each effect is still consumed exactly once (Channel semantics), so resuming does
 * not replay or double-fire.
 *
 * This matters for [RootNavViewModel]: top-level navigation effects routinely land right at the
 * resume boundary after an external flow returns (Google Sign-In, the OS notification-permission
 * dialog, system Settings). An earlier version *dropped* effects seen below RESUMED — a dropped
 * `NavigateTopLevel` left the user stranded on SignIn/NotificationPermission with no re-emit, because
 * the stage machine had already advanced its state and would not fire again. Deferring fixes that.
 */
@Composable
fun <T> EventsEffect(events: Flow<T>, onEvent: suspend (T) -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(events, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            events.collect { event ->
                FrLog.d(FrLog.Tags.Lifecycle) { "EventsEffect deliver event=$event" }
                onEvent(event)
            }
        }
    }
}
