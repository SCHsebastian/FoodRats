package es.schsebastian.foodrats.feature.feed.presentation.detail

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.presentation.components.CommentRowUi
import es.schsebastian.foodrats.feature.feed.presentation.components.FeedMealUi

data class MealDetailState(
    val meal: FeedMealUi? = null,
    val isLoading: Boolean = true,
    val error: FeedError? = null,
    val notFound: Boolean = false,
    val pendingRate: Boolean = false,
    val rateError: RateError? = null,
    val commentRows: List<CommentRowUi> = emptyList(),
    val commentsLoading: Boolean = true,
    val commentReadError: CommentError.Read? = null,
    val commentInput: String = "",
    val isPostingComment: Boolean = false,
    val commentWriteError: CommentError.Write? = null,
    val canDeleteMeal: Boolean = false,
    val isDeletingMeal: Boolean = false,
    val mealDeleted: Boolean = false,
    val mealDeleteError: MealDeleteError? = null,
    val commentDeleteError: CommentError.Delete? = null,
) : MviState

sealed interface MealDetailIntent : MviIntent {
    data class RateMeal(val score: Int) : MealDetailIntent
    data object DismissError : MealDetailIntent
    data class CommentInputChanged(val value: String) : MealDetailIntent
    data object PostComment : MealDetailIntent
    data object DeleteMeal : MealDetailIntent
    data class DeleteComment(val id: MealCommentId) : MealDetailIntent
}

sealed interface MealDetailEffect : MviEffect
