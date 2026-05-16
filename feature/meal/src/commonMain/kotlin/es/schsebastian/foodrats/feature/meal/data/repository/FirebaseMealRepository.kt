package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorageDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class FirebaseMealRepository(
    private val firestore: MealFirestoreDataSource,
    private val storage: PlateStorageDataSource,
    private val drafts: MealDraftLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
) : MealRepository {

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> =
        withContext(dispatchers.io) {
            runCatching {
                val plate = draft.plate
                    ?: return@runCatching Result.failure(MealError.Validation.NoPhoto)
                val mealId = firestore.newId(draft.crewId)
                val photoUrl = storage.upload(draft.crewId, mealId, plate)
                val dto = MealDto(
                    id = mealId,
                    authorId = draft.authorId.value,
                    authorName = "",
                    authorAvatarUrl = null,
                    crewId = draft.crewId.value,
                    dayKey = draft.day.toKey(),
                    photoUrl = photoUrl,
                    score = draft.score?.value,
                    dishName = draft.dish?.value,
                    tags = draft.tags.map { it.label },
                    publishedAtEpochMs = clock.now().toEpochMilliseconds(),
                )
                firestore.write(dto)
                @Suppress("UNCHECKED_CAST")
                dto.toDomain() as Result<Meal, MealError>
            }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override suspend fun delete(id: MealId): Result<Unit, MealError> =
        Result.success(Unit) // Firestore deletion deferred to a later phase.

    override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> =
        withContext(dispatchers.io) {
            runCatching { drafts.save(draft) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override fun observeDraft(): Flow<MealDraft?> = drafts.observe()

    override suspend fun clearDraft() = drafts.clear()

    override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<Meal>, MealReadError>> =
        firestore.observeForDay(crewId, day)
            .map<List<MealDto>, Result<List<Meal>, MealReadError>> { dtos ->
                Result.success(dtos.mapNotNull { (it.toDomain() as? Result.Ok)?.value })
            }
            .catch { t -> emit(Result.failure(errorMapper.mapRead(t))) }
            .flowOn(dispatchers.io)

    override fun observeRange(
        crewId: CrewId,
        from: MealDay,
        to: MealDay,
    ): Flow<Result<List<Meal>, MealReadError>> = observeFeed(crewId, from)
}
