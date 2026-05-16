package es.schsebastian.foodrats.feature.meal.presentation.publish

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft

data class PublishMealState(
    val draft: MealDraft? = null,
    val isPublishing: Boolean = false,
    val error: MealError? = null,
) : MviState

sealed interface PublishMealIntent : MviIntent {
    data object Load : PublishMealIntent
    data object Publish : PublishMealIntent
}

sealed interface PublishMealEffect : MviEffect { data object Published : PublishMealEffect }
