package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

private const val TITLE = "Don't break your streak"
private const val BODY = "You haven't posted today"

/**
 * Records the order of scheduler calls and the last scheduled reminder so tests can assert both
 * the computed timing and the cancel-then-schedule ordering.
 */
private class RecordingScheduler : LocalReminderScheduler {
    val calls = mutableListOf<String>()
    var scheduled: Reminder? = null
    var nextResult: Result<Unit, NotificationError.Schedule> = Result.success(Unit)

    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        calls += "schedule"
        scheduled = reminder
        return nextResult
    }

    override suspend fun cancel(reminderId: String) {
        calls += "cancel:$reminderId"
    }
}

class ScheduleDailyInactivityReminderUseCaseTest {

    private fun instantAt(
        zone: TimeZone,
        date: LocalDate,
        time: LocalTime,
    ): Instant = LocalDateTime(date, time).toInstant(zone)

    private fun expectedDeliverAt(zone: TimeZone, date: LocalDate): Instant =
        instantAt(zone, date, LocalTime(hour = 14, minute = 0))

    @Test fun schedules_today_at_14h_when_now_is_before_14h() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler()
        // 13:30 local on 2026-06-14 → reminder same day at 14:00.
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(13, 30))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        val result = useCase(TITLE, BODY)

        assertIs<Result.Ok<Unit>>(result)
        assertEquals(expectedDeliverAt(zone, LocalDate(2026, 6, 14)), scheduler.scheduled?.deliverAt)
    }

    @Test fun schedules_tomorrow_at_14h_when_now_is_after_14h() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler()
        // 14:30 local on 2026-06-14 → reminder next day at 14:00.
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(14, 30))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        assertEquals(expectedDeliverAt(zone, LocalDate(2026, 6, 15)), scheduler.scheduled?.deliverAt)
    }

    @Test fun schedules_tomorrow_when_now_is_exactly_14h() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler()
        // Exactly 14:00 is NOT before the target, so it rolls to the next day.
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(14, 0))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        assertEquals(expectedDeliverAt(zone, LocalDate(2026, 6, 15)), scheduler.scheduled?.deliverAt)
    }

    @Test fun computes_local_14h_in_non_utc_zone() = runTest {
        // 13:30 instant in UTC is 15:30 in Berlin (summer, UTC+2) → already past 14:00 local,
        // so a naive UTC-based computation would wrongly schedule today.
        val zone = TimeZone.of("Europe/Berlin")
        val scheduler = RecordingScheduler()
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(15, 30))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        assertEquals(expectedDeliverAt(zone, LocalDate(2026, 6, 15)), scheduler.scheduled?.deliverAt)
    }

    @Test fun schedules_today_in_non_utc_zone_when_before_local_14h() = runTest {
        val zone = TimeZone.of("Europe/Berlin")
        val scheduler = RecordingScheduler()
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(9, 0))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        assertEquals(expectedDeliverAt(zone, LocalDate(2026, 6, 14)), scheduler.scheduled?.deliverAt)
    }

    @Test fun reminder_uses_the_stable_daily_id_and_streak_kind() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler()
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(9, 0))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        val scheduled = scheduler.scheduled
        assertEquals(
            ScheduleDailyInactivityReminderUseCase.DAILY_INACTIVITY_REMINDER_ID,
            scheduled?.id,
        )
        assertEquals(ReminderKind.StreakAtRisk, scheduled?.kind)
        assertEquals(TITLE, scheduled?.title)
        assertEquals(BODY, scheduled?.body)
    }

    @Test fun cancels_existing_reminder_before_scheduling() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler()
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(9, 0))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        useCase(TITLE, BODY)

        assertEquals(
            listOf(
                "cancel:${ScheduleDailyInactivityReminderUseCase.DAILY_INACTIVITY_REMINDER_ID}",
                "schedule",
            ),
            scheduler.calls,
        )
    }

    @Test fun propagates_scheduler_error() = runTest {
        val zone = TimeZone.UTC
        val scheduler = RecordingScheduler().apply {
            nextResult = Result.failure(NotificationError.Schedule.Failed)
        }
        val now = instantAt(zone, LocalDate(2026, 6, 14), LocalTime(9, 0))
        val useCase = ScheduleDailyInactivityReminderUseCase(scheduler, FixedClock(now), zone)

        assertEquals(Result.failure(NotificationError.Schedule.Failed), useCase(TITLE, BODY))
    }
}
