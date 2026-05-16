package es.schsebastian.foodrats.feature.meal.presentation.compose

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.domain.error.MealError

data class ComposePlateState(
    val dish: String = "",
    val score: Int? = null,
    val selectedTags: Set<String> = emptySet(),
    val error: MealError? = null,
) : MviState

sealed interface ComposePlateIntent : MviIntent {
    data class DishChanged(val value: String) : ComposePlateIntent
    data class ScoreChanged(val value: Int) : ComposePlateIntent
    data class TagToggled(val tag: String) : ComposePlateIntent
    data object Continue : ComposePlateIntent
}

sealed interface ComposePlateEffect : MviEffect { data object NavigateToPublish : ComposePlateEffect }
