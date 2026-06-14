package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
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
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.firebase.CrewMemberLookup
import es.schsebastian.foodrats.feature.meal.data.firebase.FirebaseFault
import es.schsebastian.foodrats.feature.meal.data.firebase.toFirebaseFault
import es.schsebastian.foodrats.feature.meal.data.firebase.MealAuthorIdentity
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.MealErrorMapper
import es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorage
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
    private val firestore: MealFirestore,
    private val storage: PlateStorage,
    private val drafts: MealDraftLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
    private val authorIdentity: MealAuthorIdentity,
    private val zone: TimeZone,
    private val accountRead: AccountReadPort,
    private val imageUrls: ImageUrlPort,
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
                        // Resolve plate PATHS → signed URLs once per dto change (avatars are
                        // resolved upstream by AccountReadPort, so the lookup below already
                        // carries signed avatar URLs). Cache absorbs re-resolution on scroll.
                        val plateUrls = imageUrls
                            .resolve(crewId, dtos.mapNotNull { it.platePath })
                            .getOrNull().orEmpty()
                        accountRead.observeMany(ids).map { identities ->
                            val lookup = identities.entries.mapNotNull { (id, acc) ->
                                acc?.let { id.value to CrewMemberLookup(acc.displayName, acc.avatarUrl) }
                            }.toMap()
                            dtos.mapNotNull { dto ->
                                (dto.toMealWithRatings(lookup) as? Result.Ok)?.value?.let { mwr ->
                                    val url = dto.platePath?.let { plateUrls[it] } ?: ""
                                    mwr.copy(meal = mwr.meal.copy(photoUrl = url))
                                }
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
        val uid = authorIdentity.current()?.uid ?: return null
        val result = AccountId.of(uid)
        return if (result is Result.Ok) result.value else null
    }

    /**
     * Fans a single composed plate out to every crew in [MealDraft.audienceCrewIds] —
     * one meal document plus one image copy per crew. Per-crew copies are mandatory, not
     * a convenience: a crew-member can only mint a signed read URL for a plate stored
     * under *their* crew (`mintPlateUrls`), so a shared cross-crew object would be
     * unviewable; and per-crew ownership keeps deletion ref-count-free (each crew reclaims
     * its own blob via `onMealDeleted`). The deterministic per-crew meal id makes the whole
     * fan-out idempotent: a crew already holding this (day, slot) is skipped, so a retry
     * after a partial failure writes only the crews it hadn't reached.
     */
    override suspend fun publish(draft: MealDraft): Result<Meal, MealError> =
        withContext(dispatchers.io) {
            runCatching<Result<Meal, MealError>> {
                val plate = draft.plate
                    ?: return@runCatching Result.failure(MealError.Validation.NoPhoto)
                val slot = draft.slot
                    ?: return@runCatching Result.failure(MealError.Publish.NoSlotSelected)
                if (draft.ingredients.size > MAX_INGREDIENTS) {
                    return@runCatching Result.failure(MealError.Validation.TooManyIngredients)
                }
                if (draft.audienceCrewIds.isEmpty()) {
                    return@runCatching Result.failure(MealError.Publish.NoCrewSelected)
                }
                val author = draft.authorId
                val dayKey = draft.day.toKey()
                // Skip crews where this (day, slot) is already posted — keeps a retry from
                // re-writing crews a prior attempt succeeded on (and the create rule would
                // reject the duplicate anyway).
                val freeCrews = draft.audienceCrewIds.filter { crewId ->
                    !firestore.mealExists(crewId, author, dayKey, slot)
                }
                if (freeCrews.isEmpty()) {
                    return@runCatching Result.failure(MealError.Publish.AlreadyPostedToday)
                }
                val currentAuthor = authorIdentity.current()
                var representative: Meal? = null
                var anyFailed = false
                var anyAlreadyExists = false
                var lastFault: Throwable? = null
                for (crewId in freeCrews) {
                    val mealId = MealId.forDaySlot(crewId, author, draft.day, slot)
                    // Upload OUTSIDE the try: a storage failure must surface as PhotoUploadFailed
                    // (mapped below), aborting the fan-out — a retry re-does the unwritten crews.
                    val platePath = storage.upload(crewId, mealId.value, plate)
                    val dto = MealDto(
                        id = mealId.value,
                        authorId = author.value,
                        authorName = currentAuthor?.displayName.orEmpty(),
                        crewId = crewId.value,
                        dayKey = dayKey,
                        slot = slot.key(),
                        platePath = platePath,
                        dishName = draft.dish?.value,
                        description = draft.description.value,
                        latitude = draft.coordinates?.latitude,
                        longitude = draft.coordinates?.longitude,
                        publishedAtEpochMs = clock.now().toEpochMilliseconds(),
                        // Only the user-confirmed ingredients are persisted. The raw
                        // classifier detection (`draft.detectedIngredients`) is a compose-time
                        // picker seed and is intentionally NOT written to the Meal.
                        ingredients = draft.ingredients.map { it.value },
                        classifierVersion = draft.classifierVersion,
                    )
                    try {
                        firestore.write(dto, mealId.value)
                        if (representative == null) {
                            representative = (dto.toDomain() as? Result.Ok)?.value
                        }
                    } catch (t: Throwable) {
                        if (t.toFirebaseFault() == FirebaseFault.AlreadyExists) {
                            // Raced the mealExists pre-check; this crew already holds the slot. The
                            // deterministic path means our upload just overwrote the existing blob,
                            // so DON'T delete it — it backs the live doc.
                            anyAlreadyExists = true
                        } else {
                            anyFailed = true
                            lastFault = t
                            FrLog.w("MealRepo", t) { "fan-out write failed for crew ${crewId.value}: ${t.message}" }
                            runCatching { storage.delete(crewId, mealId.value) }
                                .onFailure { FrLog.w("MealRepo", it) { "orphan plate cleanup failed: ${it.message}" } }
                        }
                    }
                }
                val rep = representative
                when {
                    rep != null && !anyFailed -> Result.success(rep)
                    // Partial success → keep the draft so the coordinator retries the failed crews
                    // (already-written ones are skipped above). Eventual full delivery beats
                    // silently dropping the plate from some of the chosen crews.
                    rep != null               -> Result.failure(MealError.Publish.PublishUnavailable)
                    // Nothing written and a real fault hit → surface it through the mapper so the
                    // PERMISSION_DENIED / UNAVAILABLE / ALREADY_EXISTS classification matches the
                    // single-crew path.
                    anyFailed                 -> throw lastFault!!
                    // Every targeted crew already had this slot (raced the pre-check) → already posted.
                    else                      -> Result.failure(MealError.Publish.AlreadyPostedToday)
                }
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

    override suspend fun takenSlotsPerCrew(
        crewIds: Set<CrewId>,
        day: MealDay,
    ): Result<Map<CrewId, Set<MealSlot>>, MealError.Read> = withContext(dispatchers.io) {
        runCatching<Result<Map<CrewId, Set<MealSlot>>, MealError.Read>> {
            val authorId = currentAccountId()
                ?: return@runCatching Result.failure(MealError.Read.Unauthorized)
            val dayKey = day.toKey()
            Result.success(crewIds.associateWith { crewId -> firestore.takenSlots(crewId, authorId, dayKey) })
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
            val mapped = when (t.toFirebaseFault()) {
                FirebaseFault.PermissionDenied,
                FirebaseFault.Unauthenticated -> MealDeleteError.NotAuthorOrOwner
                FirebaseFault.NotFound -> MealDeleteError.NotFound
                FirebaseFault.Unavailable,
                FirebaseFault.AlreadyExists,
                FirebaseFault.StorageFailure,
                is FirebaseFault.Unknown -> MealDeleteError.Unavailable
            }
            Result.failure(mapped)
        }
    }

    override suspend fun deleteFromAllCrews(
        crewIds: Set<CrewId>,
        authorId: AccountId,
        day: MealDay,
        slot: MealSlot,
    ): Result<Unit, MealDeleteError> = withContext(dispatchers.io) {
        if (crewIds.isEmpty()) return@withContext Result.success(Unit)
        // Deleting a non-existent doc is a no-op success in Firestore, so a crew that never
        // held a copy (e.g. it wasn't in the publish audience) is harmlessly skipped. We only
        // fail the whole action if a real error (permission / unavailable) hits a crew — for the
        // author path that should never be permission, leaving transient Unavailable as retryable.
        var lastError: MealDeleteError? = null
        for (crewId in crewIds) {
            val mealId = MealId.forDaySlot(crewId, authorId, day, slot).value
            runCatching { firestore.deleteMeal(crewId, mealId) }
                .onFailure { t ->
                    when (t.toFirebaseFault()) {
                        FirebaseFault.PermissionDenied,
                        FirebaseFault.Unauthenticated -> lastError = MealDeleteError.NotAuthorOrOwner
                        FirebaseFault.NotFound -> Unit
                        FirebaseFault.Unavailable,
                        FirebaseFault.AlreadyExists,
                        FirebaseFault.StorageFailure,
                        is FirebaseFault.Unknown -> lastError = MealDeleteError.Unavailable
                    }
                    FrLog.w("MealRepo", t) { "fan-out delete failed for crew ${crewId.value}: ${t.message}" }
                }
        }
        lastError?.let { Result.failure(it) } ?: Result.success(Unit)
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
        raterId: AccountId,
        score: Score,
    ): Result<Unit, RateError> = withContext(dispatchers.io) {
        // Defense in depth: the rater identity is the explicit [raterId] from the domain,
        // but a live auth token is still required (and the txn + rules re-check self-vote /
        // already-rated authoritatively).
        if (authorIdentity.current()?.uid == null) return@withContext Result.failure(RateError.Unauthorized)
        runCatching<Result<Unit, RateError>> {
            val outcome = firestore.rateMeal(
                crewId = crewId,
                mealId = mealId.value,
                raterUid = raterId.value,
                score = score.value,
                nowEpochMs = clock.now().toEpochMilliseconds(),
            )
            when (outcome) {
                MealFirestore.RateOutcome.Ok -> Result.success(Unit)
                MealFirestore.RateOutcome.MealNotFound -> Result.failure(RateError.RateUnavailable)
                MealFirestore.RateOutcome.SelfRating -> Result.failure(RateError.CannotRateOwnMeal)
                MealFirestore.RateOutcome.AlreadyRated -> Result.failure(RateError.AlreadyRated)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { t -> Result.failure(errorMapper.mapRate(t)) },
        )
    }
}
