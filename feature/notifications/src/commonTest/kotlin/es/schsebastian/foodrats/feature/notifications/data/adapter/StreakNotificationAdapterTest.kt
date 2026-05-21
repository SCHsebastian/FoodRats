package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private class RecordingScheduler(
    var scheduleResult: Result<Unit, NotificationError.Schedule> = Result.success(Unit),
) : LocalReminderScheduler {
    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<String>()
    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        scheduled += reminder
        return scheduleResult
    }
    override suspend fun cancel(reminderId: String) {
        cancelled += reminderId
    }
}

class StreakNotificationAdapterTest {

    private val clock = FixedClock(Instant.parse("2026-05-21T12:00:00Z"))
    private val zone  = TimeZone.UTC

    /**
     * Compose Resources are not bundled in commonTest, so `getString(...)` throws.
     * The adapter swallows that into `Result.failure(Unavailable)` — that is the
     * documented behavior and the previous inline implementation in
     * PublishMealViewModel. Asserting this lock the fallback in place.
     */
    @Test fun returns_Unavailable_when_resources_missing_with_succeeding_scheduler() = runTest {
        val scheduler = RecordingScheduler(scheduleResult = Result.success(Unit))
        val adapter = StreakNotificationAdapter(
            ScheduleStreakNudgeUseCase(scheduler, clock, zone),
        )

        assertEquals(
            Result.failure(StreakNotificationError.Unavailable),
            adapter.scheduleStreakNudge(),
        )
    }

    @Test fun returns_Unavailable_when_resources_missing_with_failing_scheduler() = runTest {
        val scheduler = RecordingScheduler(scheduleResult = Result.failure(NotificationError.Schedule.Failed))
        val adapter = StreakNotificationAdapter(
            ScheduleStreakNudgeUseCase(scheduler, clock, zone),
        )

        assertEquals(
            Result.failure(StreakNotificationError.Unavailable),
            adapter.scheduleStreakNudge(),
        )
    }
}
