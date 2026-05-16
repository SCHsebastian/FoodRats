package es.schsebastian.foodrats.feature.feed.presentation.feed

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
    val canGoNext: Boolean = false,   // never lets you navigate past today
) : MviState

sealed interface FeedIntent : MviIntent {
    data object PrevDay : FeedIntent
    data object NextDay : FeedIntent
    data object CaptureClicked : FeedIntent
    data object DismissError : FeedIntent
}

sealed interface FeedEffect : MviEffect {
    data object NavigateToCapture : FeedEffect
}
