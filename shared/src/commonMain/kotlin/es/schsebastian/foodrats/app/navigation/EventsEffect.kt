package es.schsebastian.foodrats.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Collect [events] only when the host's lifecycle is at least RESUMED.
 * Prevents duplicate navigations on resume.
 */
@Composable
fun <T> EventsEffect(events: Flow<T>, onEvent: suspend (T) -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(events, owner) {
        events.collectLatest { event ->
            val state = owner.lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                FrLog.d(FrLog.Channel.Lifecycle) { "EventsEffect deliver event=$event (state=$state)" }
                onEvent(event)
            } else {
                FrLog.d(FrLog.Channel.Lifecycle) {
                    "EventsEffect DROPPED event=$event (state=$state — not yet RESUMED)"
                }
            }
        }
    }
}
