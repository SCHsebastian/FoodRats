package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort.Companion.MAX_REMINDERS
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** The localized reminder text, resolved in composition and pushed via [MealReminderScheduler.onCopyResolved]. */
data class ReminderCopy(val title: String, val body: String)

/**
 * Keeps the registered OS reminders in sync with the user's configuration. Started eagerly at app
 * launch (Koin `createdAtStart`), it observes the persisted [MealReminderSchedulePort.times], the
 * notifications opt-in ([NotificationsPreferencePort.enabled]), and the localized [ReminderCopy]
 * and re-applies on any change:
 *
 *  - notifications ON  → schedule slot `i` (`meal-reminder-i`) at `times[i]` for each configured time,
 *    and cancel any unused slot up to [MAX_REMINDERS].
 *  - notifications OFF → cancel every slot.
 *
 * The title/body are NOT resolved here: a non-composable `getString` in this collector races the
 * composition frames that apply the in-app locale (`LocalAppLocale.provides` mutates the default
 * locale per frame — see `NotificationChannels` in androidMain for the same bug class), so it could
 * capture the device language instead of the user's chosen one, and it would never re-resolve on an
 * in-app language change. Instead `SyncMealReminderCopy` in `:shared` resolves the strings inside
 * `ProvideAppLocale` — where the locale is authoritative — and pushes them via [onCopyResolved],
 * re-firing on every language change so the scheduled slots are replaced with translated text.
 *
 * Nothing is scheduled until composition has pushed the first copy: re-establishment happens on
 * every UI launch, which covers reboot/upgrade (WorkManager itself persists scheduled work across
 * reboots, so headless process starts don't need re-scheduling), and any pref or language change
 * necessarily happens with the UI alive.
 *
 * This is the only place reminders are scheduled — callers just persist via the port and this reacts.
 */
class MealReminderScheduler(
    scope: CoroutineScope,
    schedulePort: MealReminderSchedulePort,
    notificationsPref: NotificationsPreferencePort,
    private val useCase: ScheduleDailyInactivityReminderUseCase,
    private val localScheduler: LocalReminderScheduler,
) {
    private val copy = MutableStateFlow<ReminderCopy?>(null)

    /** Called from composition (`SyncMealReminderCopy`) with the locale-correct reminder text. */
    fun onCopyResolved(title: String, body: String) {
        copy.value = ReminderCopy(title = title, body = body)
    }

    init {
        scope.launch {
            combine(
                notificationsPref.enabled.distinctUntilChanged(),
                schedulePort.times.distinctUntilChanged(),
                copy.filterNotNull().distinctUntilChanged(),
            ) { enabled, times, resolved -> Triple(enabled, times, resolved) }
                .collect { (enabled, times, resolved) -> apply(enabled, times, resolved) }
        }
    }

    private suspend fun apply(
        enabled: Boolean,
        times: List<kotlinx.datetime.LocalTime>,
        resolved: ReminderCopy,
    ) {
        for (slot in 0 until MAX_REMINDERS) {
            val id = "$REMINDER_ID_PREFIX$slot"
            if (enabled && slot < times.size) {
                useCase(title = resolved.title, body = resolved.body, time = times[slot], id = id)
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
