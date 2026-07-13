package es.schsebastian.foodrats.feature.meal.data.repository

import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.image.ImageUrlPort
import es.schsebastian.foodrats.core.domain.meal.DraftIngredients
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealKind
import es.schsebastian.foodrats.core.domain.meal.MealPlate
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.PlateSource
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
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateEntryDto
import es.schsebastian.foodrats.feature.meal.data.firebase.PlateStorage
import es.schsebastian.foodrats.feature.meal.data.firebase.toDiscriminator
import es.schsebastian.foodrats.feature.meal.data.firebase.toDomain
import es.schsebastian.foodrats.feature.meal.data.firebase.toMealWithRatings
import es.schsebastian.foodrats.feature.meal.data.local.MealDraftLocalStore
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import es.schsebastian.foodrats.feature.meal.data.local.toMealDto
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
    private val local: MealLocalStore,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: MealErrorMapper,
    private val clock: Clock,
    private val authorIdentity: MealAuthorIdentity,
    private val zone: TimeZone,
    private val accountRead: AccountReadPort,
    private val imageUrls: ImageUrlPort,
    private val cuisineRead: CuisineReadPort,
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

    // Read-path inversion (offline-first P3a-T4): the enriched per-crew stream is now sourced from
    // the local SQLDelight store (MealLocalStore.observeRange) — NOT Firestore. The MealSyncEngine
    // is the sole Firestore-listener consumer; it mirrors the server's rolling 30-day window into
    // the local DB, and this stream reads it back. The enrichment (signed-URL minting + live
    // identity resolution), the per-crew memoization (streamsLock/streams + shareIn), and the
    // benign-empty `.catch` are all preserved verbatim — only the upstream source flow changed.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun crewStream(crewId: CrewId): SharedFlow<List<MealWithRatings>> =
        streamsLock.withLock {
            streams.getOrPut(crewId) {
                val today = MealDay.today(clock, zone)
                val from = MealDay(today.date.minus(DatePeriod(days = STATS_WINDOW_DAYS - 1)), zone)
                enrichedStream(crewId, from, today)
                    .shareIn(
                        scope = repoScope,
                        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS),
                        replay = 1,
                    )
            }
        }

    /**
     * The enriched local feed for one crew over [from]..[to]: reads the SQLDelight rows via
     * [MealLocalStore.observeRange], rebuilds each [MealDto] (paths, never URLs), then runs the EXACT
     * enrichment the feed has always used — signed URLs minted at read time via [imageUrls], live
     * identity resolved via [accountRead.observeMany] → `toMealWithRatings`. Identity for authors and
     * raters is sourced from the live `accounts/{id}` doc, not the denormalized snapshots baked into
     * the row, so a profile rename in :feature:auth propagates to the feed and the meal-detail vote
     * breakdown without a republish or rejoin. The benign-empty `.catch` survives a signed-out read.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun enrichedStream(
        crewId: CrewId,
        from: MealDay,
        to: MealDay,
    ): Flow<List<MealWithRatings>> =
        local.observeRange(crewId.value, from.toKey(), to.toKey())
            // SQLDelight query listeners are table-scoped: selectRangeByCrew re-emits an EQUAL
            // List<LocalMeal> on ANY meal-table write (a different crew's sync, an optimistic rate
            // elsewhere, the pruner). LocalMeal/LocalRating are data classes (exact structural
            // equality), so deduping at the source drops the redundant enrichment below
            // (flatMapLatest restart → observeMany cancel + imageUrls.resolve re-issue) AND keeps
            // the existing observeMany subscription alive — strictly better than a terminal dedupe.
            .distinctUntilChanged()
            .map { rows -> rows.map { it.toMealDto() } }
            .flatMapLatest { dtos ->
                val ids = dtos.flatMap { dto ->
                    listOfNotNull(dto.authorId) + dto.ratings.keys
                }.mapNotNull { (AccountId.of(it) as? Result.Ok)?.value }.toSet()
                // Resolve plate AND thumbnail PATHS → signed URLs once per dto change
                // (avatars are resolved upstream by AccountReadPort, so the lookup below
                // already carries signed avatar URLs). Thumbnails share the crew prefix, so
                // `mintPlateUrls` authorizes them in the same batch — no callable change
                // (roadmap §5.1 handoff). plates[1..]'s paths ride in the SAME batch (one
                // callable call regardless of path count); plates[0].path == platePath by
                // construction, so `.distinct()` avoids asking the resolver for it twice.
                // Cache absorbs re-resolution on scroll.
                val signedUrls = imageUrls
                    .resolve(
                        crewId,
                        dtos.flatMap { dto ->
                            listOfNotNull(dto.platePath, dto.thumbnailPath) + dto.plates.mapNotNull { it.path }
                        }.distinct(),
                    )
                    .getOrNull().orEmpty()
                accountRead.observeMany(ids).map { identities ->
                    val lookup = identities.entries.mapNotNull { (id, acc) ->
                        acc?.let { id.value to CrewMemberLookup(acc.displayName, acc.avatarUrl, acc.bio, acc.badgeId) }
                    }.toMap()
                    dtos.mapNotNull { dto ->
                        (dto.toMealWithRatings(lookup) as? Result.Ok)?.value?.let { mwr ->
                            val plateUrl = dto.platePath?.let { signedUrls[it] } ?: ""
                            val thumbUrl = dto.thumbnailPath?.let { signedUrls[it] } ?: ""
                            // A legacy row (dto.plates empty) yields an empty list here — readers
                            // fall back to photoUrl/plateSource, exactly like the domain contract.
                            // An entry whose signed URL failed to resolve is DROPPED, never
                            // surfaced with a blank URL.
                            val enrichedPlates = dto.plates.mapNotNull { entry ->
                                val path = entry.path?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val signed = signedUrls[path] ?: return@mapNotNull null
                                MealPlate(photoUrl = signed, source = PlateSource.fromKey(entry.source))
                            }
                            mwr.copy(
                                meal = mwr.meal.copy(
                                    photoUrl = plateUrl,
                                    thumbnailUrl = thumbUrl,
                                    plates = enrichedPlates,
                                ),
                            )
                        }
                    }
                }
            }
            // PERMISSION_DENIED-on-signout (or any upstream throw) becomes an empty list, which
            // downstream observers already render gracefully (no meals → empty state).
            .catch { t ->
                FrLog.w("MealRepo", t) { "enrichedStream upstream throw: ${t.message}" }
                emit(emptyList())
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
                if (draft.plates.isEmpty()) {
                    return@runCatching Result.failure(MealError.Validation.NoPhoto)
                }
                if (draft.plates.size > MealPublishPolicy.MAX_PHOTOS_PER_MEAL) {
                    return@runCatching Result.failure(MealError.Validation.TooManyPhotos)
                }
                // Slot is optional now — no NoSlotSelected check; "" persists as "no slot".
                if (draft.ingredients.size > MAX_INGREDIENTS) {
                    return@runCatching Result.failure(MealError.Validation.TooManyIngredients)
                }
                if (draft.audienceCrewIds.isEmpty()) {
                    return@runCatching Result.failure(MealError.Publish.NoCrewSelected)
                }
                val author = draft.authorId
                val dayKey = draft.day.toKey()
                val slotKey = draft.slot?.key() ?: ""
                // Stable per-draft idempotency token: identical on every retry (same plates, same
                // order) and across the per-crew copies of one logical post, so the deterministic
                // MealId.forDayToken keeps re-publishing idempotent and lets "delete my post"
                // reconstruct each crew's copy. A single photo keeps the EXACT legacy formula (so
                // ids minted before multi-photo existed are unaffected); reordering photos changes
                // the fold's result, which is intentional — a reorder is a different logical post.
                val token = if (draft.plates.size == 1) {
                    draft.plates[0].photoBytes.contentHashCode().toUInt().toString(16)
                } else {
                    draft.plates.fold(17) { acc, p -> 31 * acc + p.photoBytes.contentHashCode() }.toUInt().toString(16)
                }
                val currentAuthor = authorIdentity.current()
                // Resolve the cuisine ONCE for the whole fan-out from the detected dish. Advisory:
                // a missing/unmapped dish OR a lookup fault yields null (cuisine just stays
                // unstamped) — it must NEVER block publishing. Stays inside this publish
                // withContext (the single IO boundary).
                val cuisineSlug = draft.detectedDishSlug
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { cuisineRead.loadDishCuisine(it) }.getOrNull() }
                var representative: Meal? = null
                var anyFailed = false
                var lastFault: Throwable? = null
                for (crewId in draft.audienceCrewIds) {
                    val existing = firestore.existingMealIds(crewId, author, dayKey)
                    val mealId = MealId.forDayToken(crewId, author, draft.day, token)
                    val dto = MealDto(
                        id = mealId.value,
                        authorId = author.value,
                        authorName = currentAuthor?.displayName.orEmpty(),
                        crewId = crewId.value,
                        dayKey = dayKey,
                        slot = slotKey,
                        platePath = "crews/${crewId.value}/meals/${mealId.value}.jpg",
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
                        cuisine = cuisineSlug?.value,
                        // Stamp Solo — the draft carries no kind yet (spec §4.3); every published
                        // meal is Solo. Branch on draft.kind only when the Together build ships.
                        kind = MealKind.Solo.toDiscriminator(),
                        // Provenance marker: mirrors plates[0] — every per-crew copy carries the
                        // PRIMARY photo's capture source (the legacy single-photo readers' signal).
                        plateSource = draft.plates[0].source.key(),
                    )
                    // Idempotent retry / double-fire: this exact post already reached this crew.
                    // Don't set a representative — a skip is not a fresh publish; if NOTHING is
                    // written we report AlreadyPostedToday (the queue treats that as success).
                    if (mealId.value in existing) continue
                    // Daily cap reached for this crew — skip it (the rule has no count, so this
                    // client-side guard is the cap). Other selected crews may still have room.
                    if (existing.size >= MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY) continue
                    // Upload every photo IN ORDER (index 0 = primary → the legacy path; n >= 1 →
                    // the extras). Each call returns its own deterministic path; rebuild the dto's
                    // `plates` list from exactly what landed in Storage — never a guessed path. A
                    // mid-loop storage failure best-effort deletes whatever THIS attempt already
                    // uploaded (indices 0 until the failing one) before rethrowing — without this,
                    // those objects would never be referenced by any Firestore doc and would leak
                    // forever once the retry budget exhausts on a photo that fails every attempt. A
                    // retry then re-uploads from index 0, an idempotent overwrite at the same
                    // deterministic paths, so cleaning up here is safe.
                    val uploaded = mutableListOf<String>()
                    val uploadedPaths = try {
                        draft.plates.forEachIndexed { index, p -> uploaded += storage.upload(crewId, mealId.value, index, p) }
                        uploaded
                    } catch (t: Throwable) {
                        FrLog.w("MealRepo", t) { "upload failed for crew ${crewId.value} at plate ${uploaded.size}: ${t.message}" }
                        uploaded.indices.forEach { index ->
                            runCatching { storage.delete(crewId, mealId.value, index) }
                                .onFailure { FrLog.w("MealRepo", it) { "orphan plate cleanup failed after upload failure (index $index): ${it.message}" } }
                        }
                        throw t
                    }
                    val dtoWithPaths = dto.copy(
                        platePath = uploadedPaths[0],
                        plates = uploadedPaths.mapIndexed { index, path ->
                            PlateEntryDto(path = path, source = draft.plates[index].source.key())
                        },
                    )
                    try {
                        firestore.write(dtoWithPaths, mealId.value)
                        if (representative == null) representative = (dtoWithPaths.toDomain() as? Result.Ok)?.value
                    } catch (t: Throwable) {
                        // `.set()` OVERWRITES — it never throws ALREADY_EXISTS — so uniqueness is
                        // enforced by the create rule's `!exists(...)`, whose rejection surfaces as
                        // PERMISSION_DENIED. A concurrent / retried / double-fired publish of the
                        // SAME draft lands here with a LIVE doc already owning this deterministic
                        // blob (our upload just overwrote it). Reclaiming it would strip the image
                        // off a published meal — the "image vanishes" bug. So confirm no live doc
                        // exists before treating the blob(s) as orphans.
                        val docExists = t.toFirebaseFault() == FirebaseFault.AlreadyExists ||
                            runCatching { firestore.existingMealIds(crewId, author, dayKey).contains(mealId.value) }
                                .getOrDefault(true)
                        // When a live doc already backs this blob (our prior attempt or a concurrent
                        // publish) leave it intact and treat as already-posted (no representative). Only
                        // a genuine fault with NO live doc is an orphan to clean up + a real failure.
                        if (!docExists) {
                            anyFailed = true
                            lastFault = t
                            FrLog.w("MealRepo", t) { "fan-out write failed for crew ${crewId.value}: ${t.message}" }
                            // Best-effort per-object: delete EVERY photo uploaded in this attempt,
                            // not just the primary — a partial cleanup would leave extras orphaned.
                            uploadedPaths.indices.forEach { index ->
                                runCatching { storage.delete(crewId, mealId.value, index) }
                                    .onFailure { FrLog.w("MealRepo", it) { "orphan plate cleanup failed (index $index): ${it.message}" } }
                            }
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
                    // PERMISSION_DENIED / UNAVAILABLE classification matches the single-crew path.
                    anyFailed                 -> throw lastFault!!
                    // Every targeted crew was at the cap (or raced) → daily limit reached.
                    else                      -> Result.failure(MealError.Publish.AlreadyPostedToday)
                }
            }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(errorMapper.mapPublish(it)) },
            )
        }

    override suspend fun mealCountsPerCrew(
        crewIds: Set<CrewId>,
        day: MealDay,
    ): Result<Map<CrewId, Int>, MealError.Read> = withContext(dispatchers.io) {
        runCatching<Result<Map<CrewId, Int>, MealError.Read>> {
            val authorId = currentAccountId()
                ?: return@runCatching Result.failure(MealError.Read.Unauthorized)
            val dayKey = day.toKey()
            Result.success(crewIds.associateWith { crewId -> firestore.existingMealIds(crewId, authorId, dayKey).size })
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
        token: String,
    ): Result<Unit, MealDeleteError> = withContext(dispatchers.io) {
        if (crewIds.isEmpty()) return@withContext Result.success(Unit)
        // Deleting a non-existent doc is a no-op success in Firestore, so a crew that never
        // held a copy (e.g. it wasn't in the publish audience) is harmlessly skipped. We only
        // fail the whole action if a real error (permission / unavailable) hits a crew — for the
        // author path that should never be permission, leaving transient Unavailable as retryable.
        var lastError: MealDeleteError? = null
        for (crewId in crewIds) {
            // Each crew's copy of this logical post shares the same token suffix, so the id is
            // reconstructible per crew from the token alone.
            val mealId = MealId.forDayToken(crewId, authorId, day, token).value
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
        // Stats' Historic tab asks for a 365-day range that extends BEFORE the memoized 30-day
        // window's lower bound. Filtering the memoized stream would silently cap it at 30 days (the
        // pre-inversion behavior). The local store retains older rows beyond the synced window, so a
        // range that reaches further back reads MealLocalStore directly (non-memoized, freshly
        // enriched) and surfaces the full retained history.
        val windowFrom = MealDay(
            MealDay.today(clock, zone).date.minus(DatePeriod(days = STATS_WINDOW_DAYS - 1)),
            zone,
        )
        val source = if (fromKey < windowFrom.toKey()) {
            enrichedStream(crewId, from, to)
        } else {
            crewStream(crewId)
        }
        emitAll(
            source
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
