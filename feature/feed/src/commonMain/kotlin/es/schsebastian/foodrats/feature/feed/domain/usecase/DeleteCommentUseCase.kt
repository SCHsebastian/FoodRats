package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Deletes a comment. Authorization (author OR crew owner) is enforced server-side by the
 * Firestore rules; a rejection surfaces as [CommentError.Delete.NotAuthorOrOwner]. The UI
 * gates the affordance with the same check.
 */
class DeleteCommentUseCase(private val comments: MealCommentPort) {
    suspend operator fun invoke(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
    ): Result<Unit, CommentError.Delete> = comments.delete(crewId, mealId, commentId)
}
