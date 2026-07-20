package es.schsebastian.foodrats.feature.stats.presentation.calendar

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.MealCalendarMonth
import kotlinx.datetime.LocalDate

data class MealCalendarState(
    /** First day of the displayed month — the single month cursor (MVI single source of truth). */
    val month: LocalDate? = null,
    /** Today in the device zone, seeded once from Clock; the NextMonth clamp anchor. */
    val today: LocalDate? = null,
    val selectedDay: LocalDate? = null,
    val calendar: MealCalendarMonth? = null,
    val isLoading: Boolean = true,
    val error: StatsError? = null,
    /** Bumped by [MealCalendarIntent.Retry] so an identical month cursor re-subscribes the read. */
    val epoch: Int = 0,
) : MviState

sealed interface MealCalendarIntent : MviIntent {
    data object PreviousMonth : MealCalendarIntent
    data object NextMonth : MealCalendarIntent
    data class DaySelected(val day: LocalDate) : MealCalendarIntent
    data object Retry : MealCalendarIntent
}

sealed interface MealCalendarEffect : MviEffect
