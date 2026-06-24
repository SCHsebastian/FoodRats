package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentDto
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.FirebaseFault
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.firebase.toFirebaseFault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [MealCommentPort] over the `crews/{crewId}/meals/{mealId}/comments` subcollection.
 *
 * Mirrors [FirebaseReactionRepository]: observe maps the DTO stream into the [MealComment] read
 * model (malformed docs are dropped — forward-compat), and the two write methods do their work
 * inside exactly one `withContext(dispatchers.io)` (CHARTER rule 4). Vendor failures are bucketed
 * via [toFirebaseFault] and mapped to the typed [CommentError] tree — never inspected by message
 * anywhere but that one seam.
 *
 * Depends on the data-layer [CommentFirestore] + [MealAuthorIdentity] seams (not the concrete
 * Firestore data source / GitLive `FirebaseAuth`) so the orchestration is fakeable in `commonTest`
 * and the Firebase→own-server swap re-binds those two seams, not the repository.
 */
internal class FirebaseCommentRepository(
    private val ds: CommentFirestore,
    private val authorIdentity: MealAuthorIdentity,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : MealCommentPort {

    override fun observe(
        crewId: CrewId,
        mealId: MealId,
    ): Flow<Result<List<MealComment>, CommentError.Read>> =
        ds.observe(crewId, mealId)
            .map<List<CommentDto>, Result<List<MealComment>, CommentError.Read>> { dtos ->
                Result.success(
                    dtos.mapNotNull { dto -> (dto.toDomain(crewId, mealId) as? Result.Ok)?.value }
                )
            }
            .catch { emit(Result.failure(CommentError.Read.Unavailable)) }
            .flowOn(dispatchers.io)

    override suspend fun post(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
    ): Result<Unit, CommentError.Write> = withContext(dispatchers.io) {
        val uid = authorIdentity.current()?.uid
            ?: return@withContext Result.failure(CommentError.Write.Unauthorized)
        runCatching {
            ds.create(
                crewId, mealId,
                CommentDto(
                    id = commentId.value,
                    authorId = uid,
                    text = text.value,
                    createdAtEpochMs = clock.now().toEpochMilliseconds(),
                ),
            )
            Result.success(Unit) as Result<Unit, CommentError.Write>
        }.getOrElse { t ->
            val mapped = when (t.toFirebaseFault()) {
                FirebaseFault.PermissionDenied,
                FirebaseFault.Unauthenticated -> CommentError.Write.Unauthorized
                else -> CommentError.Write.Unavailable
            }
            Result.failure(mapped)
        }
    }

    override suspend fun edit(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
    ): Result<Unit, CommentError.Edit> = withContext(dispatchers.io) {
        authorIdentity.current()?.uid
            ?: return@withContext Result.failure(CommentError.Edit.NotAuthor)
        runCatching {
            ds.update(
                crewId, mealId, commentId.value,
                text = text.value,
                editedAtEpochMs = clock.now().toEpochMilliseconds(),
            )
            Result.success(Unit) as Result<Unit, CommentError.Edit>
        }.getOrElse { t ->
            val mapped = when (t.toFirebaseFault()) {
                // The rule denies a non-author edit → permission denied.
                FirebaseFault.PermissionDenied,
                FirebaseFault.Unauthenticated -> CommentError.Edit.NotAuthor
                FirebaseFault.NotFound -> CommentError.Edit.NotFound
                else -> CommentError.Edit.Unavailable
            }
            Result.failure(mapped)
        }
    }

    override suspend fun delete(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
    ): Result<Unit, CommentError.Delete> = withContext(dispatchers.io) {
        authorIdentity.current()?.uid
            ?: return@withContext Result.failure(CommentError.Delete.NotAuthorOrOwner)
        runCatching {
            ds.delete(crewId, mealId, commentId.value)
            Result.success(Unit) as Result<Unit, CommentError.Delete>
        }.getOrElse { t ->
            val mapped = when (t.toFirebaseFault()) {
                FirebaseFault.PermissionDenied,
                FirebaseFault.Unauthenticated -> CommentError.Delete.NotAuthorOrOwner
                FirebaseFault.NotFound -> CommentError.Delete.NotFound
                else -> CommentError.Delete.Unavailable
            }
            Result.failure(mapped)
        }
    }
}
