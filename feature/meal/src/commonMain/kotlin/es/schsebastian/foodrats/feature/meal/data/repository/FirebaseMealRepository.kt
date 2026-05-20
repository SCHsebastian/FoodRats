package es.schsebastian.foodrats.feature.meal.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.MealRatingDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealRatingsFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorageDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal class FirebaseMealRepository(
    private val firestore: MealFirestoreDataSource,
    private val ratings: MealRatingsFirestoreDataSource,
    private val storage: PlateStorageDataSource,
    private val drafts: MealDraftLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
    private val auth: FirebaseAuth,
    private val zone: TimeZone,
) : MealRepository {

    private fun currentAccountId(): AccountId? {
        val uid = auth.currentUser?.uid ?: return null
        val result = AccountId.of(uid)
        return if (result is Result.Ok) result.value else null
    }

    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> =
        withContext(dispatchers.io) {
            runCatching<Result<Meal, MealError>> {
                val plate = draft.plate
                    ?: return@runCatching Result.failure(MealError.Validation.NoPhoto)
                val slot = draft.slot
                    ?: return@runCatching Result.failure(MealError.Publish.NoSlotSelected)
                val mealId = MealId.forDaySlot(draft.crewId, draft.authorId, draft.day, slot)
                val photoUrl = storage.upload(draft.crewId, mealId.value, plate)
                val currentUser = auth.currentUser
                val dto = MealDto(
                    id = mealId.value,
                    authorId = draft.authorId.value,
                    authorName = currentUser?.displayName.orEmpty(),
                    authorAvatarUrl = currentUser?.photoURL,
                    crewId = draft.crewId.value,
                    dayKey = draft.day.toKey(),
                    slot = slot.key(),
                    photoUrl = photoUrl,
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

    override suspend fun delete(id: MealId): Result<Unit, MealError> = Result.success(Unit)

    override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> =
        withContext(dispatchers.io) {
            runCatching { drafts.save(draft) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override fun observeDraft(): Flow<MealDraft?> = drafts.observe()

    override suspend fun clearDraft() = drafts.clear()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFeed(
        crewId: CrewId,
        day: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> =
        firestore.observeForDay(crewId, day).joinRatings(crewId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRange(
        crewId: CrewId,
        from: MealDay,
        to: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> =
        firestore.observeForRange(crewId, from, to).joinRatings(crewId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<MealDto>>.joinRatings(
        crewId: CrewId,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> =
        flatMapLatest { dtos ->
            if (dtos.isEmpty()) flowOf(emptyList<MealWithRatings>())
            else combine(
                dtos.map { dto ->
                    ratings.observe(crewId, dto.id ?: "")
                        .map { pairs -> dto to pairs.toMealRatings() }
                }
            ) { it.toList() }.map { combined ->
                combined.mapNotNull { (dto, rs) ->
                    val mealResult = dto.toDomain()
                    if (mealResult is Result.Ok) MealWithRatings(mealResult.value, rs) else null
                }
            }
        }
            .map<List<MealWithRatings>, Result<List<MealWithRatings>, MealReadError>> { Result.success(it) }
            .catch { t -> emit(Result.failure(errorMapper.mapRead(t))) }
            .flowOn(dispatchers.io)

    override suspend fun rate(
        crewId: CrewId,
        mealId: MealId,
        score: Score,
    ): Result<Unit, RateError> = withContext(dispatchers.io) {
        val raterUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(RateError.Unauthorized)

        runCatching<Result<Unit, RateError>> {
            val mealDto: MealDto = firestore.readById(crewId, mealId.value)
                ?: return@runCatching Result.failure(RateError.RateUnavailable)
            if (mealDto.authorId == raterUid) return@runCatching Result.failure(RateError.CannotRateOwnMeal)

            val mealDay = MealDay(LocalDate.parse(mealDto.dayKey ?: ""), zone)
            val today = MealDay.today(clock, zone)
            if (today.daysSince(mealDay) !in 0..1) {
                return@runCatching Result.failure(RateError.RatingWindowClosed)
            }

            val dto = MealRatingDto(
                score = score.value,
                ratedAtEpochMs = clock.now().toEpochMilliseconds(),
                raterName = auth.currentUser?.displayName ?: "",
                raterAvatarUrl = auth.currentUser?.photoURL,
            )
            ratings.write(crewId, mealId.value, raterUid, dto)
            Result.success(Unit)
        }.fold(
            onSuccess = { it },
            onFailure = { t ->
                val msg = t.message.orEmpty().lowercase()
                val mapped = when {
                    "already-exists" in msg || "already_exists" in msg -> RateError.AlreadyRated
                    else -> errorMapper.mapRate(t)
                }
                Result.failure(mapped)
            },
        )
    }

    private fun List<Pair<String, MealRatingDto>>.toMealRatings(): List<MealRating> =
        mapNotNull { (raterUid, dto) ->
            val raterId = AccountId.of(raterUid)
            if (raterId is Result.Ok) {
                val r = dto.toDomain(raterId.value)
                if (r is Result.Ok) r.value else null
            } else null
        }
}
