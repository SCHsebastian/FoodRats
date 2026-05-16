package es.schsebastian.foodrats.feature.notifications.domain.model

import kotlinx.datetime.LocalTime

/** When in the day a local nudge should fire. Default 19:00. */
data class DeliveryWindow(val localTime: LocalTime) {
    companion object {
        val Default: DeliveryWindow = DeliveryWindow(LocalTime(hour = 19, minute = 0))
    }
}
