package es.schsebastian.foodrats.feature.feed.presentation.feed

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi
import kotlinx.datetime.LocalDate

data class FeedState(
    val day: FeedDay? = null,
    /** Local "today" in the feed's zone; lets the day header label as Today/Yesterday. */
    val today: LocalDate? = null,
    val meals: List<FeedMealUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    val pendingRateMealId: String? = null,
    val rateError: RateError? = null,
    val isUploadActive: Boolean = false,
) : MviState

sealed interface FeedIntent : MviIntent {
    data object PrevDay : FeedIntent
    data object NextDay : FeedIntent
    data object DismissError : FeedIntent
    data class RateMeal(val mealId: String, val score: Int) : FeedIntent
}

sealed interface FeedEffect : MviEffect
