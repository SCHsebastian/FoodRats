package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.components.toFeedUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FeedViewModel(
    private val observeFeed: ObserveFeedUseCase,
    private val clock: Clock,
    private val zone: TimeZone,
) : MviViewModel<FeedState, FeedIntent, FeedEffect>(FeedState()) {

    private val today = clock.now().toLocalDateTime(zone).date
    private val day = MutableStateFlow(FeedDay.today(today, zone))

    init {
        update { it.copy(day = day.value, canGoNext = false, canGoPrev = true) }
        viewModelScope.launch {
            observeFeed(day).collect { r ->
                when (r) {
                    is Result.Ok -> update {
                        it.copy(
                            isLoading = false,
                            meals = r.value.map { m -> m.toFeedUi() },
                            error = null,
                        )
                    }
                    is Result.Err -> update { it.copy(isLoading = false, error = r.error) }
                }
            }
        }
    }

    override suspend fun handle(intent: FeedIntent) = when (intent) {
        FeedIntent.PrevDay        -> { navigatePrev(); Unit }
        FeedIntent.NextDay        -> { navigateNext(); Unit }
        FeedIntent.CaptureClicked -> emit(FeedEffect.NavigateToCapture)
        FeedIntent.DismissError   -> update { it.copy(error = null) }
    }

    private fun navigatePrev() {
        val candidate = day.value.previous()
        if (!FeedDay.isWithinWindow(candidate.day.date, today)) {
            update { it.copy(canGoPrev = false) }
            return
        }
        day.value = candidate
        update { it.copy(
            day = candidate,
            isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }

    private fun navigateNext() {
        val candidate = day.value.next()
        if (candidate.day.date > today) {
            update { it.copy(canGoNext = false) }
            return
        }
        day.value = candidate
        update { it.copy(
            day = candidate,
            isLoading = true,
            canGoNext = candidate.day.date < today,
            canGoPrev = FeedDay.isWithinWindow(candidate.previous().day.date, today),
        ) }
    }
}
