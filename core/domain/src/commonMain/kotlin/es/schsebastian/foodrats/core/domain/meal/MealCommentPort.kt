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

        /** Blocked by the on-device text filter before reaching Firestore or the outbox (UGC §3). */
        data object Objectionable : Write
        data object Unavailable  : Write
    }
    sealed interface Delete : CommentError {
        /** The caller is neither the comment's author nor the crew owner. */
        data object NotAuthorOrOwner : Delete
        data object NotFound         : Delete
        data object Unavailable      : Delete
    }
    /** Editing the text of an already-posted comment. Author-only (unlike [Delete], the owner cannot edit). */
    sealed interface Edit : CommentError {
        /** The caller is not the comment's author (only the author may edit). */
        data object NotAuthor    : Edit
        data object NotFound     : Edit
        data object Blank        : Edit
        data object TooLong      : Edit

        /** Blocked by the on-device text filter before reaching Firestore or the outbox (UGC §3). */
        data object Objectionable : Edit
        data object Unavailable   : Edit
    }
}

interface MealCommentPort {
    /**
     * Live comments for a meal, oldest first. [limit] bounds the listener to the newest [limit] docs
     * (FIREST-2) so a popular meal doesn't stream its entire comment subcollection; raise it to page
     * in older comments. Chronological (ascending) display order is unchanged regardless of [limit].
     */
    fun observe(crewId: CrewId, mealId: MealId, limit: Int): Flow<Result<List<MealComment>, CommentError.Read>>
    suspend fun post(crewId: CrewId, mealId: MealId, commentId: MealCommentId, text: CommentText): Result<Unit, CommentError.Write>
    suspend fun edit(crewId: CrewId, mealId: MealId, commentId: MealCommentId, text: CommentText): Result<Unit, CommentError.Edit>
    suspend fun delete(crewId: CrewId, mealId: MealId, commentId: MealCommentId): Result<Unit, CommentError.Delete>
}
