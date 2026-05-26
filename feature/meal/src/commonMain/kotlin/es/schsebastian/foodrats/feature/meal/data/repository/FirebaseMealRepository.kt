package es.schsebastian.foodrats.feature.meal.data.repository

import dev.gitlive.firebase.auth.FirebaseAuth
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.DraftIngredients
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.CrewMemberLookup
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestoreDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorageDataSource
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.firebase.toMealWithRatings
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

internal class FirebaseMealRepository(
    private val firestore: MealFirestoreDataSource,
    private val storage: PlateStorageDataSource,
    private val drafts: MealDraftLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
    private val auth: FirebaseAuth,
    private val zone: TimeZone,
    private val accountRead: AccountReadPort,
) : MealRepository {

    // CoroutineExceptionHandler is mandatory: when sign-out revokes the auth token,
    // Firestore listeners inside `shareIn` upstreams below fire PERMISSION_DENIED.
    // Without a handler the exception escapes repoScope's parent → Android FATAL
    // EXCEPTION / iOS Kotlin/Native terminateWithUnhandledException → SIGABRT.
    // The handler is the safety net; each `shareIn` also has its own upstream
    // `.catch { emit(emptyList()) }` so consumers see a benign empty list rather
    // than a crash window.
    private val repoScope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
            dispatchers.default +
            CoroutineExceptionHandler { _, t ->
                FrLog.w("MealRepo", t) { "repoScope uncaught: ${t.message}" }
            },
    )

    private val streamsLock = Mutex()
    private val streams = mutableMapOf<CrewId, SharedFlow<List<MealWithRatings>>>()

    // Identity for authors and raters is sourced from the live `accounts/{id}` doc via
    // AccountReadPort, not from the denormalized snapshots baked into the meal document
    // or the crew members cache. A profile rename in :feature:auth propagates to the
    // feed and the meal detail vote breakdown without a republish or rejoin.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun crewStream(crewId: CrewId): SharedFlow<List<MealWithRatings>> =
        streamsLock.withLock {
            streams.getOrPut(crewId) {
                val today = MealDay.today(clock, zone)
                val from = MealDay(today.date.minus(DatePeriod(days = STATS_WINDOW_DAYS - 1)), zone)
                firestore.observeForRange(crewId, from, today)
                    .flatMapLatest { dtos ->
                        val ids = dtos.flatMap { dto ->
                            listOfNotNull(dto.authorId) + dto.ratings.keys
                        }.mapNotNull { (AccountId.of(it) as? Result.Ok)?.value }.toSet()
                        accountRead.observeMany(ids).map { identities ->
                            val lookup = identities.entries.mapNotNull { (id, acc) ->
                                acc?.let { id.value to CrewMemberLookup(acc.displayName, acc.avatarUrl) }
                            }.toMap()
                            dtos.mapNotNull { dto ->
                                (dto.toMealWithRatings(lookup) as? Result.Ok)?.value
                            }
                        }
                    }
                    // PERMISSION_DENIED-on-signout becomes an empty list, which downstream
                    // observers already render gracefully (no meals → empty state).
                    .catch { t ->
                        FrLog.w("MealRepo", t) { "crewStream upstream throw: ${t.message}" }
                        emit(emptyList())
                    }
                    .shareIn(
                        scope = repoScope,
                        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS),
                        replay = 1,
                    )
            }
        }

    private companion object {
        const val STATS_WINDOW_DAYS = 30
        const val SHARE_STOP_TIMEOUT_MS = 5_000L
        const val MAX_INGREDIENTS = 30
    }

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
                if (draft.ingredients.size > MAX_INGREDIENTS ||
                    draft.detectedIngredients.size > MAX_INGREDIENTS
                ) {
                    return@runCatching Result.failure(MealError.Validation.TooManyIngredients)
                }
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
                    description = draft.description.value,
                    latitude = draft.coordinates?.latitude,
                    longitude = draft.coordinates?.longitude,
                    publishedAtEpochMs = clock.now().toEpochMilliseconds(),
                    ingredients = draft.ingredients.map { it.value },
                    detectedIngredients = draft.detectedIngredients.map { it.value },
                    classifierVersion = draft.classifierVersion,
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

    override suspend fun delete(
        crewId: CrewId,
        mealId: MealId,
    ): Result<Unit, MealDeleteError> = withContext(dispatchers.io) {
        runCatching {
            firestore.deleteMeal(crewId, mealId.value)
            Result.success(Unit) as Result<Unit, MealDeleteError>
        }.getOrElse { t ->
            val msg = t.message.orEmpty().lowercase()
            val mapped = when {
                "permission" in msg -> MealDeleteError.NotAuthorOrOwner
                "not-found" in msg || "not found" in msg -> MealDeleteError.NotFound
                else -> MealDeleteError.Unavailable
            }
            Result.failure(mapped)
        }
    }

    override suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError> =
        withContext(dispatchers.io) {
            runCatching { drafts.save(draft) }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override fun observeDraft(): Flow<MealDraft?> = drafts.observe()

    override suspend fun clearDraft() = drafts.clear()

    override fun observeDraftIngredients(): Flow<DraftIngredients?> =
        drafts.observe().map { draft ->
            draft?.let { DraftIngredients(selected = it.ingredients, detected = it.detectedIngredients) }
        }

    /** Composes existing draft read + [saveDraft] — the single IO boundary lives in `saveDraft`. */
    override suspend fun setIngredients(slugs: List<IngredientSlug>) {
        val current = drafts.observe().first() ?: return
        saveDraft(current.copy(ingredients = slugs))
    }

    override fun observeFeed(
        crewId: CrewId,
        day: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> = flow {
        val dayKey = day.toKey()
        emitAll(
            crewStream(crewId)
                .map { all -> all.filter { it.meal.day.toKey() == dayKey } }
                .distinctUntilChanged()
                .map<List<MealWithRatings>, Result<List<MealWithRatings>, MealReadError>> { Result.success(it) }
                .catch { t -> emit(Result.failure(errorMapper.mapRead(t))) }
        )
    }

    override fun observeRange(
        crewId: CrewId,
        from: MealDay,
        to: MealDay,
    ): Flow<Result<List<MealWithRatings>, MealReadError>> = flow {
        val fromKey = from.toKey()
        val toKey = to.toKey()
        emitAll(
            crewStream(crewId)
                .map { all -> all.filter { val k = it.meal.day.toKey(); k in fromKey..toKey } }
                .distinctUntilChanged()
                .map<List<MealWithRatings>, Result<List<MealWithRatings>, MealReadError>> { Result.success(it) }
                .catch { t -> emit(Result.failure(errorMapper.mapRead(t))) }
        )
    }

    override suspend fun rate(
        crewId: CrewId,
        mealId: MealId,
        score: Score,
    ): Result<Unit, RateError> = withContext(dispatchers.io) {
        val raterUid = auth.currentUser?.uid
            ?: return@withContext Result.failure(RateError.Unauthorized)
        runCatching<Result<Unit, RateError>> {
            val outcome = firestore.rateMeal(
                crewId = crewId,
                mealId = mealId.value,
                raterUid = raterUid,
                score = score.value,
                nowEpochMs = clock.now().toEpochMilliseconds(),
            )
            when (outcome) {
                MealFirestoreDataSource.RateOutcome.Ok -> Result.success(Unit)
                MealFirestoreDataSource.RateOutcome.MealNotFound -> Result.failure(RateError.RateUnavailable)
                MealFirestoreDataSource.RateOutcome.SelfRating -> Result.failure(RateError.CannotRateOwnMeal)
                MealFirestoreDataSource.RateOutcome.AlreadyRated -> Result.failure(RateError.AlreadyRated)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { t -> Result.failure(errorMapper.mapRate(t)) },
        )
    }
}
