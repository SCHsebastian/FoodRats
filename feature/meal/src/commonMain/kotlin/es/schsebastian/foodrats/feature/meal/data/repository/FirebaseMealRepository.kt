package es.schsebastian.foodrats.feature.meal.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.model.AccountId
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
    private val auth: FirebaseAuth,
) : MealRepository {

    private fun currentAccountId(): AccountId? {
        val uid = auth.currentUser?.uid ?: return null
        val result = AccountId.of(uid)
        return if (result is Result.Ok) result.value else null
    }

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> =
        withContext(dispatchers.io) {
            runCatching {
                val plate = draft.plate
                    ?: return@runCatching Result.failure(MealError.Validation.NoPhoto)
                val slot = draft.slot
                    ?: return@runCatching Result.failure(MealError.Publish.NoSlotSelected)
                val mealId = MealId.forDaySlot(draft.crewId, draft.authorId, draft.day, slot)
                val photoUrl = storage.upload(draft.crewId, mealId.value, plate)
                val dto = MealDto(
                    id = mealId.value,
                    authorId = draft.authorId.value,
                    authorName = "",
                    authorAvatarUrl = null,
                    crewId = draft.crewId.value,
                    dayKey = draft.day.toKey(),
                    slot = slot.key(),
                    photoUrl = photoUrl,
                    score = draft.score?.value,
                    dishName = draft.dish?.value,
                    tags = draft.tags.map { it.label },
                    publishedAtEpochMs = clock.now().toEpochMilliseconds(),
                )
                firestore.write(dto, mealId.value)
                @Suppress("UNCHECKED_CAST")
                dto.toDomain() as Result<Meal, MealError>
            }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override suspend fun hasMealForSlot(
        crewId: CrewId,
        day: MealDay,
        slot: MealSlot,
    ): Result<Boolean, MealError.Read> = withContext(dispatchers.io) {
        runCatching<Result<Boolean, MealError.Read>> {
            val authorId = currentAccountId()
                ?: return@runCatching Result.failure(MealError.Read.Unauthorized)
            Result.success(firestore.mealExists(crewId, authorId, day.toKey(), slot))
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(MealError.Read.NotFound) },
        )
    }

    override suspend fun takenSlotsFor(
        crewId: CrewId,
        day: MealDay,
    ): Result<Set<MealSlot>, MealError.Read> = withContext(dispatchers.io) {
        runCatching<Result<Set<MealSlot>, MealError.Read>> {
            val authorId = currentAccountId()
                ?: return@runCatching Result.failure(MealError.Read.Unauthorized)
            Result.success(firestore.takenSlots(crewId, authorId, day.toKey()))
        }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(MealError.Read.NotFound) },
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
