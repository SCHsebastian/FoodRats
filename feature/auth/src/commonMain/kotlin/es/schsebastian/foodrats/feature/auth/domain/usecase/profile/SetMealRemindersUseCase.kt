package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError
import kotlinx.datetime.LocalTime

/**
 * Persists the user's chosen meal-reminder times. The actual OS (re)scheduling is reactive: the
 * `MealReminderScheduler` in :feature:notifications observes the persisted times and re-applies — so
 * this use case only writes the preference (single source of truth).
 */
class SetMealRemindersUseCase(private val port: MealReminderSchedulePort) {
    suspend operator fun invoke(times: List<LocalTime>): Result<Unit, ProfileError> =
        when (val r = port.set(times)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
}
