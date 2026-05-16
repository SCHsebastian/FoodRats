package es.schsebastian.foodrats.feature.notifications.domain.bus

import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-scoped event bus the OS-side push receivers post to. Observed by the root composable
 * to show in-app banners; bound as a Koin single so emitters and observers share the flow.
 */
class NotificationBus {
    private val _stream = MutableSharedFlow<Reminder>(extraBufferCapacity = 8)
    val stream: SharedFlow<Reminder> = _stream.asSharedFlow()
    suspend fun publish(reminder: Reminder) = _stream.emit(reminder)
}
