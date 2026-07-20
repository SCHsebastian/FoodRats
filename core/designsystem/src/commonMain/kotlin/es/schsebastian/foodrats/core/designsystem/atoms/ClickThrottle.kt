package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Re-tap suppression window for action buttons. Wide enough to swallow an accidental double-tap,
 * short enough that a deliberate second press (e.g. retry after an error banner) still lands.
 */
internal val ClickThrottleWindow: Duration = 500.milliseconds

/**
 * Leading-edge click throttle: the first tap fires [onClick] immediately; further taps within
 * [ClickThrottleWindow] are dropped. This is the atom-level backstop against double-submits —
 * `MviViewModel.onIntent` launches a coroutine per intent, so without it a double-tap races two
 * concurrent `handle()` calls (double join, double publish, double navigation push).
 *
 * Deliberately a throttle, not a debounce: debouncing would delay the first tap and make every
 * button feel laggy. [timeSource] is injectable for tests only.
 */
@Composable
internal fun rememberThrottledClick(
    onClick: () -> Unit,
    timeSource: TimeSource = TimeSource.Monotonic,
): () -> Unit {
    val latest by rememberUpdatedState(onClick)
    var lastFired: TimeMark? by remember { mutableStateOf(null) }
    return remember(timeSource) {
        {
            val last = lastFired
            if (last == null || last.elapsedNow() >= ClickThrottleWindow) {
                lastFired = timeSource.markNow()
                latest()
            }
        }
    }
}
