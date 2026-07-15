package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.preferences.MealReminderPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeSchedulePort(
    initial: List<LocalTime> = listOf(LocalTime(hour = 14, minute = 0)),
) : MealReminderSchedulePort {
    val flow = MutableStateFlow(initial)
    override val times: Flow<List<LocalTime>> get() = flow
    override suspend fun set(times: List<LocalTime>): Result<Unit, MealReminderPreferenceError> {
        flow.value = times
        return Result.success(Unit)
    }
}

private class FakeNotificationsPref(initial: Boolean) : NotificationsPreferencePort {
    val enabledFlow = MutableStateFlow(initial)
    override val enabled: Flow<Boolean> get() = enabledFlow
    override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> {
        enabledFlow.value = enabled
        return Result.success(Unit)
    }
    override val prompted: Flow<Boolean> get() = MutableStateFlow(true)
    override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
}

private class RecordingLocalScheduler : LocalReminderScheduler {
    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<String>()
    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        scheduled += reminder
        return Result.success(Unit)
    }
    override suspend fun cancel(reminderId: String) {
        cancelled += reminderId
    }
}

class MealReminderSchedulerTest {

    private val clock = FixedClock(Instant.parse("2026-07-15T09:00:00Z"))

    private fun useCase(local: LocalReminderScheduler) =
        ScheduleDailyInactivityReminderUseCase(local, clock, TimeZone.UTC)

    private fun scheduler(
        scope: kotlinx.coroutines.CoroutineScope,
        local: RecordingLocalScheduler,
        pref: FakeNotificationsPref,
        schedule: FakeSchedulePort,
    ) = MealReminderScheduler(
        scope = scope,
        schedulePort = schedule,
        notificationsPref = pref,
        useCase = useCase(local),
        localScheduler = local,
    )

    @Test fun does_not_schedule_before_copy_arrives_even_when_enabled_with_times() = runTest {
        val local = RecordingLocalScheduler()
        scheduler(backgroundScope, local, FakeNotificationsPref(initial = true), FakeSchedulePort())

        testScheduler.runCurrent()

        assertTrue(local.scheduled.isEmpty())
        assertTrue(local.cancelled.isEmpty())
    }

    @Test fun schedules_with_the_resolved_copy_once_it_arrives() = runTest {
        val local = RecordingLocalScheduler()
        val sut = scheduler(backgroundScope, local, FakeNotificationsPref(initial = true), FakeSchedulePort())

        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()

        assertEquals(1, local.scheduled.size)
        assertEquals("Hungry?", local.scheduled.single().title)
        assertEquals("Snap one before dinner.", local.scheduled.single().body)
        assertEquals("${MealReminderScheduler.REMINDER_ID_PREFIX}0", local.scheduled.single().id)
    }

    @Test fun copy_change_reschedules_the_same_slots_with_the_new_text() = runTest {
        val local = RecordingLocalScheduler()
        val sut = scheduler(backgroundScope, local, FakeNotificationsPref(initial = true), FakeSchedulePort())

        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()
        sut.onCopyResolved(title = "¿Hambre?", body = "Saca una antes de la cena.")
        testScheduler.runCurrent()

        assertEquals(2, local.scheduled.size)
        assertEquals(local.scheduled[0].id, local.scheduled[1].id)
        assertEquals("¿Hambre?", local.scheduled[1].title)
        assertEquals("Saca una antes de la cena.", local.scheduled[1].body)
    }

    @Test fun identical_copy_does_not_reapply() = runTest {
        val local = RecordingLocalScheduler()
        val sut = scheduler(backgroundScope, local, FakeNotificationsPref(initial = true), FakeSchedulePort())

        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()
        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()

        assertEquals(1, local.scheduled.size)
    }

    @Test fun disabled_cancels_every_slot_regardless_of_copy() = runTest {
        val local = RecordingLocalScheduler()
        val sut = scheduler(backgroundScope, local, FakeNotificationsPref(initial = false), FakeSchedulePort())

        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()

        assertTrue(local.scheduled.isEmpty())
        assertEquals(
            (0 until MealReminderSchedulePort.MAX_REMINDERS).map { "${MealReminderScheduler.REMINDER_ID_PREFIX}$it" },
            local.cancelled,
        )
    }

    @Test fun disabling_after_copy_cancels_the_scheduled_slots() = runTest {
        val local = RecordingLocalScheduler()
        val pref = FakeNotificationsPref(initial = true)
        val sut = scheduler(backgroundScope, local, pref, FakeSchedulePort())

        sut.onCopyResolved(title = "Hungry?", body = "Snap one before dinner.")
        testScheduler.runCurrent()
        pref.set(enabled = false)
        testScheduler.runCurrent()

        assertTrue(local.cancelled.contains("${MealReminderScheduler.REMINDER_ID_PREFIX}0"))
    }
}
