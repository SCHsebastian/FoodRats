package es.schsebastian.foodrats.feature.notifications.domain.model

import kotlin.time.Instant

data class Reminder(
    val id: String,
    val kind: ReminderKind,
    val deliverAt: Instant,
    val title: String,
    val body: String,
)
