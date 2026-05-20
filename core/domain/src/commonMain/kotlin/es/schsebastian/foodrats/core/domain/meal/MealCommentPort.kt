package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

sealed interface CommentError {
    sealed interface Read : CommentError {
        data object Unauthorized : Read
        data object Unavailable  : Read
    }
    sealed interface Write : CommentError {
        data object Unauthorized : Write
        data object Blank        : Write
        data object TooLong      : Write
        data object Unavailable  : Write
    }
}

interface MealCommentPort {
    fun observe(crewId: CrewId, mealId: MealId): Flow<Result<List<MealComment>, CommentError.Read>>
    suspend fun post(crewId: CrewId, mealId: MealId, text: CommentText): Result<Unit, CommentError.Write>
}
