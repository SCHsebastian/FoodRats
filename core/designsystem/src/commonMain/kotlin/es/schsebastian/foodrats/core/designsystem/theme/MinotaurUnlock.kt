package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Secret unlock for Minotaur mode: hold THREE fingers down together for ~1.5s anywhere.
 * Observes pointer events on the Initial pass and never consumes them, so normal taps,
 * scrolls and clicks still reach children. Dropping below three fingers cancels the hold.
 */
fun Modifier.minotaurUnlockGesture(onUnlock: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 3) {
                val released = withTimeoutOrNull(1500L) {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.count { it.pressed } < 3) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") true
                }
                if (released == null) {
                    onUnlock()
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                    } while (e.changes.any { it.pressed })
                }
            }
        }
    }
}
