package es.schsebastian.foodrats.feature.meal.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentDto
import es.schsebastian.foodrats.feature.meal.data.firebase.CommentFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class FirebaseCommentRepository(
    private val ds: CommentFirestoreDataSource,
    private val auth: FirebaseAuth,
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
        text: CommentText,
    ): Result<Unit, CommentError.Write> = withContext(dispatchers.io) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(CommentError.Write.Unauthorized)
        runCatching {
            ds.create(
                crewId, mealId,
                CommentDto(
                    authorId = user.uid,
                    text = text.value,
                    createdAtEpochMs = clock.now().toEpochMilliseconds(),
                ),
            )
            Result.success(Unit) as Result<Unit, CommentError.Write>
        }.getOrElse { t ->
            val msg = t.message.orEmpty().lowercase()
            val mapped = if ("permission-denied" in msg) CommentError.Write.Unauthorized
                         else CommentError.Write.Unavailable
            Result.failure(mapped)
        }
    }
}
