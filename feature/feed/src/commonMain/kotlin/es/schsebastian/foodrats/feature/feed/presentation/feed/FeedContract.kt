package es.schsebastian.foodrats.feature.feed.presentation.feed

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.domain.model.FeedDay
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi

data class FeedState(
    val day: FeedDay? = null,
    val meals: List<FeedMealUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val canGoPrev: Boolean = false,
    val canGoNext: Boolean = false,
    val pendingRateMealId: String? = null,
    val rateError: RateError? = null,
) : MviState

sealed interface FeedIntent : MviIntent {
    data object PrevDay : FeedIntent
    data object NextDay : FeedIntent
    data object DismissError : FeedIntent
    data class RateMeal(val mealId: String, val score: Int) : FeedIntent
}

sealed interface FeedEffect : MviEffect
