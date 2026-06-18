@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort.Companion.MAX_REMINDERS
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Keeps the registered OS reminders in sync with the user's configuration. Started eagerly at app
 * launch (Koin `createdAtStart`), it observes the persisted [MealReminderSchedulePort.times] and the
 * notifications opt-in ([NotificationsPreferencePort.enabled]) and re-applies on any change:
 *
 *  - notifications ON  → schedule slot `i` (`meal-reminder-i`) at `times[i]` for each configured time,
 *    and cancel any unused slot up to [MAX_REMINDERS].
 *  - notifications OFF → cancel every slot.
 *
 * This is the only place reminders are scheduled — callers just persist via the port and this reacts,
 * which also re-establishes the schedule after reboot/upgrade (the collect runs on every launch).
 */
class MealReminderScheduler(
    scope: CoroutineScope,
    schedulePort: MealReminderSchedulePort,
    notificationsPref: NotificationsPreferencePort,
    private val useCase: ScheduleDailyInactivityReminderUseCase,
    private val localScheduler: LocalReminderScheduler,
) {
    init {
        scope.launch {
            combine(
                notificationsPref.enabled.distinctUntilChanged(),
                schedulePort.times.distinctUntilChanged(),
            ) { enabled, times -> enabled to times }
                .collect { (enabled, times) -> apply(enabled, times) }
        }
    }

    private suspend fun apply(enabled: Boolean, times: List<kotlinx.datetime.LocalTime>) {
        // Resolved off any Composable context; wrapped so a missing-resources environment (unit tests,
        // very early boot) degrades to a no-op instead of crashing the app.
        val title: String
        val body: String
        try {
            title = getString(NotificationStringKey.InactivityTitle.resourceId)
            body = getString(NotificationStringKey.InactivityBody.resourceId)
        } catch (t: Throwable) {
            FrLog.w(FrLog.Tags.Notifications) { "[MealReminderScheduler] could not resolve strings: ${t.message}" }
            return
        }
        for (slot in 0 until MAX_REMINDERS) {
            val id = "$REMINDER_ID_PREFIX$slot"
            if (enabled && slot < times.size) {
                useCase(title = title, body = body, time = times[slot], id = id)
            } else {
                localScheduler.cancel(id)
            }
        }
        FrLog.d(FrLog.Tags.Notifications) {
            "[MealReminderScheduler] applied enabled=$enabled times=$times"
        }
    }

    companion object {
        const val REMINDER_ID_PREFIX = "meal-reminder-"
    }
}
