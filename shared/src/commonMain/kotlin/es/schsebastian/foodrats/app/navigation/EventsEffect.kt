package es.schsebastian.foodrats.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                onEvent(event)
            }
        }
    }
}
