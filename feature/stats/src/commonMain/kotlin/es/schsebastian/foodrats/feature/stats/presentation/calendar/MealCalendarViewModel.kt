package es.schsebastian.foodrats.feature.stats.presentation.calendar

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.stats.domain.compute.startOfMonth
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveMyMealCalendarUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * MVI state machine for the "My plates" monthly calendar. The month cursor lives ONLY in
 * [MealCalendarState.month] (no parallel `MutableStateFlow` — `FeedViewModel` reference pattern);
 * the use case is fed a `(month, epoch)`-derived flow so [MealCalendarIntent.Retry] can
 * re-subscribe the same month after an error.
 */
class MealCalendarViewModel(
    observeMyMealCalendar: ObserveMyMealCalendarUseCase,
    clock: Clock,
    zone: TimeZone,
) : MviViewModel<MealCalendarState, MealCalendarIntent, MealCalendarEffect>(MealCalendarState()) {

    init {
        val today = clock.now().toLocalDateTime(zone).date
        update { it.copy(today = today, month = startOfMonth(today)) }

        val monthFlow = state
            .map { s -> s.month?.let { it to s.epoch } }
            .filterNotNull()
            .distinctUntilChanged()
            .map { it.first }

        viewModelScope.launch {
            observeMyMealCalendar(monthFlow).collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(calendar = r.value, isLoading = false, error = null) }
                    is Result.Err -> update { it.copy(error = r.error, isLoading = false) }
                }
            }
        }
    }

    override suspend fun handle(intent: MealCalendarIntent) {
        when (intent) {
            MealCalendarIntent.PreviousMonth -> {
                val month = currentState.month ?: return
                update {
                    it.copy(
                        month = month.minus(DatePeriod(months = 1)),
                        selectedDay = null,
                        calendar = null,
                        isLoading = true,
                    )
                }
            }
            MealCalendarIntent.NextMonth -> {
                val month = currentState.month ?: return
                val cap = currentState.today?.let(::startOfMonth) ?: return
                if (month < cap) {
                    update {
                        it.copy(
                            month = month.plus(DatePeriod(months = 1)),
                            selectedDay = null,
                            calendar = null,
                            isLoading = true,
                        )
                    }
                }
            }
            is MealCalendarIntent.DaySelected -> update {
                // Tapping the selected day again deselects it.
                it.copy(selectedDay = if (it.selectedDay == intent.day) null else intent.day)
            }
            MealCalendarIntent.Retry -> update {
                it.copy(error = null, isLoading = true, epoch = it.epoch + 1)
            }
        }
    }
}
