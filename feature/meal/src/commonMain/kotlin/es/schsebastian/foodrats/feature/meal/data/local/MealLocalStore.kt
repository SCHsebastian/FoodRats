package es.schsebastian.foodrats.feature.meal.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.database.Meal as MealRow
import es.schsebastian.foodrats.core.database.MealRating as MealRatingRow
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The local read source-of-truth for the meal feed (offline-first P3a §3.1). Wraps the SQLDelight
 * `meal` + `mealRating` queries; the [FirebaseMealRepository][es.schsebastian.foodrats.feature.meal.data.repository.FirebaseMealRepository]
 * now sources its enriched crew stream from here (not Firestore), and the
 * [MealSyncEngine][es.schsebastian.foodrats.feature.meal.data.sync] writes server snapshots in.
 *
 * READS are reactive [Flow]s of [LocalMeal] joined with their ratings; the IO boundary for a flow
 * is `mapToList(dispatchers.io)`. SYNC WRITES ([upsertAll]/[replaceCrewWindow]) each own exactly one
 * `withContext(dispatchers.io)`. The store never mints signed URLs or resolves display names — it
 * holds Storage PATHS only; the repository's `toMealWithRatings(lookup)` enrichment does both live
 * at read time off the rebuilt [MealDto][es.schsebastian.foodrats.feature.meal.data.firebase.MealDto].
 */
open class MealLocalStore(
    private val database: FoodRatsDatabase?,
    private val dispatchers: DispatcherProvider?,
) {
    /**
     * No-DB constructor for the read-path test double ONLY: a commonTest fake overrides every read
     * method and never touches the SQLDelight queries. feature:meal commonTest has no cross-platform
     * SQLDelight driver, so the real JVM-backed store is exercised in androidHostTest; the repository's
     * read-path enrichment is unit-tested here against a fake that returns canned [LocalMeal]s.
     * Production always supplies a real [FoodRatsDatabase] via the primary constructor (Koin binds
     * it non-null), so the `!!` getters below never fire outside the override-only fake.
     */
    protected constructor() : this(null, null)

    private val queries get() = database!!.mealQueries
    private val io get() = dispatchers!!.io

    /** One crew's meals for a single [dayKey], newest first, each with its denormalized ratings. */
    open fun observeFeed(crewId: String, dayKey: String): Flow<List<LocalMeal>> =
        queries.selectFeedByCrewDay(crewId, dayKey)
            .asFlow()
            .mapToList(io)
            .joinRatings()

    /** One crew's meals across an inclusive [fromKey]..[toKey] range, newest first, with ratings. */
    open fun observeRange(crewId: String, fromKey: String, toKey: String): Flow<List<LocalMeal>> =
        queries.selectRangeByCrew(crewId, fromKey, toKey)
            .asFlow()
            .mapToList(io)
            .joinRatings()

    /**
     * Joins each meal-rows emission with a reactive ratings flow for exactly those meal ids, so a
     * new vote (which touches only `mealRating`) re-emits without re-querying the meal table. The
     * meal rows are captured per emission; `flatMapLatest` cancels the prior ratings subscription
     * whenever the meal set changes. An empty meal list short-circuits to `emptyList` (an empty
     * `IN ()` is meaningless).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<MealRow>>.joinRatings(): Flow<List<LocalMeal>> =
        flatMapLatest { rows ->
            val ids = rows.map { it.mealId }
            if (ids.isEmpty()) {
                flowOf(emptyList())
            } else {
                queries.selectRatingsForMeals(ids)
                    .asFlow()
                    .mapToList(io)
                    .map { ratingRows ->
                        val byMeal = ratingRows.groupBy { it.mealId }
                        rows.map { row -> row.toLocalMeal(byMeal[row.mealId].orEmpty()) }
                    }
            }
        }

    /**
     * Upserts every [dtos] row (server-confirmed) into the `meal` table plus its ratings, in ONE
     * transaction. Used for an incremental sync that must not drop meals outside the touched set.
     */
    open suspend fun upsertAll(dtos: List<MealDto>) = withContext(io) {
        val upserts = dtos.map { it.toLocalUpsert() }
        queries.transaction {
            writeAll(upserts)
        }
    }

    /**
     * Replaces a crew's [fromKey]..[toKey] window with exactly [dtos]: upsert the present rows and
     * delete any meal currently held in that window but absent from [dtos] (delete-by-absence),
     * all in ONE transaction. Rows OUTSIDE the window are untouched (older history persists for
     * stats). Ratings of deleted meals cascade away via the FK.
     */
    open suspend fun replaceCrewWindow(
        crewId: String,
        fromKey: String,
        toKey: String,
        dtos: List<MealDto>,
    ) = withContext(io) {
        val upserts = dtos.map { it.toLocalUpsert() }
        val incomingIds = upserts.map { it.mealId }.toSet()
        queries.transaction {
            val existing = queries.mealIdsForCrewInRange(crewId, fromKey, toKey).executeAsList()
            val toDelete = existing.filterNot { it in incomingIds }
            if (toDelete.isNotEmpty()) queries.deleteMealsByIds(toDelete)
            writeAll(upserts)
        }
    }

    /**
     * Bounds local DB growth (offline-first P4-T1): deletes every meal whose [dayKey] is strictly
     * older than [beforeDayKey] (a `YYYY-MM-DD` key — lexicographic order matches chronological
     * order). The sync engine's delete-by-absence is window-scoped (30 days), so rows that age out
     * of the window would otherwise accumulate forever; the [CachePruner][es.schsebastian.foodrats.feature.meal.data.sync.CachePruner]
     * calls this once at app start with a retention cutoff. Ratings of pruned meals cascade away via
     * the `mealRating` FK (foreign_keys is PRAGMA-enabled in the production drivers); the explicit
     * [deleteRatingsForAbsentMeals] is a defensive sweep in case a connection ever lacks the PRAGMA.
     * Both run in ONE transaction.
     */
    open suspend fun pruneOlderThan(beforeDayKey: String) = withContext(io) {
        queries.transaction {
            queries.deleteMealsBeforeDay(beforeDayKey)
            queries.deleteRatingsForAbsentMeals()
        }
    }

    /**
     * Optimistic RATE (offline-first P3b §P3b-T5): records [raterId]'s [score] for [mealId] as a
     * PENDING local row so the feed (which reads this store) shows the star instantly, before the
     * network write. In ONE transaction: upsert `mealRating(pending = 1)`, recompute the meal's
     * denormalized `ratingSum`/`voterCount` from the resulting rating set, and stamp the meal
     * `pending = 1` + [idempotencyKey]. A no-op if the meal isn't held locally (nothing to render
     * against). The next server snapshot of this meal overwrites the row with `pending = 0` (see
     * [writeAll]), auto-clearing the pending flag — no explicit reconcile call is needed.
     */
    open suspend fun applyRate(
        mealId: String,
        raterId: String,
        score: Int,
        atMs: Long,
        idempotencyKey: String,
    ) = withContext(io) {
        queries.transaction {
            val meal = queries.selectMealById(mealId).executeAsOneOrNull()
                ?: return@transaction // meal not cached locally → nothing to show optimistically
            // A pre-existing local rating with a DIFFERENT score means this optimistic write is the
            // one allowed CHANGE — stamp edited = 1 so the UI locks the "change my vote" affordance
            // immediately (mirrors the server transaction). A first vote, or a re-pick of the same
            // score (an idempotent no-op server-side), stays edited = 0 and doesn't consume it.
            val isEdit = queries.selectRatingsForMeals(listOf(mealId)).executeAsList()
                .any { it.raterId == raterId && it.score != score.toLong() }
            queries.upsertRating(
                mealId = mealId,
                raterId = raterId,
                score = score.toLong(),
                atMs = atMs,
                pending = 1L,
                edited = if (isEdit) 1L else 0L,
            )
            val ratings = queries.selectRatingsForMeals(listOf(mealId)).executeAsList()
            queries.setMealOptimisticRate(
                ratingSum = ratings.sumOf { it.score },
                voterCount = ratings.size.toLong(),
                idempotencyKey = idempotencyKey,
                mealId = mealId,
            )
        }
    }

    /**
     * Rolls back the optimistic bookkeeping for the meal carrying [idempotencyKey] (offline-first
     * P3b §P3b-T5): clears the meal `pending`/`idempotencyKey` and drops its still-unconfirmed
     * (`pending = 1`) rating rows, recomputing the denormalized totals from what remains, all in ONE
     * transaction. A no-op if no meal carries that key (the server snapshot already reconciled it).
     *
     * Uses the dedicated [rollbackMealRate][es.schsebastian.foodrats.core.database.MealQueries.rollbackMealRate]
     * query (M3) to recompute totals WITHOUT first re-setting `pending = 1` (as the former
     * [setMealOptimisticRate] would have done), avoiding a wasted intermediate write whose intent
     * was confusing. Sequence: drop pending ratings → recompute totals (rollbackMealRate) → clear
     * pending flag + key (clearMealPending). Result: `pending = 0`, `idempotencyKey = NULL`, totals
     * reflect only the server-confirmed rating rows.
     */
    open suspend fun clearPending(idempotencyKey: String) = withContext(io) {
        queries.transaction {
            val mealId = queries.mealIdForIdempotencyKey(idempotencyKey).executeAsOneOrNull()
                ?: return@transaction // already reconciled by a server snapshot
            queries.deletePendingRatings(mealId)
            val ratings = queries.selectRatingsForMeals(listOf(mealId)).executeAsList()
            // M3: dedicated rollback recompute — does NOT touch pending/idempotencyKey, so there
            // is no wasted intermediate write that re-stamps pending = 1 before clearMealPending.
            queries.rollbackMealRate(
                ratingSum = ratings.sumOf { it.score },
                voterCount = ratings.size.toLong(),
                mealId = mealId,
            )
            queries.clearMealPending(mealId)
        }
    }

    /**
     * Upserts every meal row in [upserts] and re-syncs their ratings, in ONE set-based pass over the
     * whole batch. Called inside an open transaction. A SINGLE [selectRatingsForMeals][es.schsebastian.foodrats.core.database.MealQueries.selectRatingsForMeals]
     * over all incoming meal ids replaces the former one-query-per-meal reconcile — a 30-meal snapshot
     * used to run ~30 ratings queries inside the replace-window transaction. Behaviour is identical to
     * the old per-meal loop: meal ids are unique within a snapshot, so reconciling against the union of
     * incoming `(mealId, raterId)` keys deletes/keeps exactly the same rating rows.
     */
    private fun writeAll(upserts: List<MealUpsert>) {
        if (upserts.isEmpty()) return
        upserts.forEach { it.upsertMealRow() }
        // Re-sync the denormalized ratings across the WHOLE snapshot: a vote retracted server-side must
        // disappear locally. ONE selectRatingsForMeals over every incoming meal id (the query takes a
        // collection), then delete exactly the rating rows whose meal is in this batch but whose
        // (mealId, raterId) is ABSENT from the incoming snapshot. INSERT OR REPLACE keyed on
        // (mealId, raterId) is idempotent, so we only delete the stragglers.
        val incomingMealIds = upserts.map { it.mealId }
        val incomingRatingKeys = upserts.flatMapTo(HashSet()) { upsert ->
            upsert.ratings.map { upsert.mealId to it.raterId }
        }
        queries.selectRatingsForMeals(incomingMealIds).executeAsList()
            .filterNot { (it.mealId to it.raterId) in incomingRatingKeys }
            .forEach { queries.deleteRating(it.mealId, it.raterId) }
        upserts.forEach { it.upsertRatings() }
    }

    /** Upsert one meal row (no rating reconcile — see [writeAll]). Called inside an open transaction. */
    private fun MealUpsert.upsertMealRow() {
        queries.upsertMeal(
            mealId = mealId,
            crewId = crewId,
            authorId = authorId,
            authorName = authorName,
            dayKey = dayKey,
            slot = slot,
            platePath = platePath,
            thumbnailPath = thumbnailPath,
            thumbHash = thumbHash,
            dishName = dishName,
            description = description,
            latitude = latitude,
            longitude = longitude,
            publishedAtEpochMs = publishedAtEpochMs,
            ratingSum = ratingSum,
            voterCount = voterCount,
            ingredientsCsv = ingredientsCsv,
            classifierVersion = classifierVersion,
            cuisine = cuisine,
            kind = kind,
            pending = pending,
            idempotencyKey = idempotencyKey,
        )
    }

    /** Upsert this meal's incoming rating rows (INSERT OR REPLACE, idempotent). Inside a transaction. */
    private fun MealUpsert.upsertRatings() {
        ratings.forEach { r ->
            queries.upsertRating(
                mealId = mealId,
                raterId = r.raterId,
                score = r.score.toLong(),
                atMs = r.atMs,
                pending = if (r.pending) 1L else 0L,
                edited = if (r.edited) 1L else 0L,
            )
        }
    }
}

private fun MealRow.toLocalMeal(ratings: List<MealRatingRow>): LocalMeal = LocalMeal(
    mealId = mealId,
    crewId = crewId,
    authorId = authorId,
    authorName = authorName,
    dayKey = dayKey,
    slot = slot,
    platePath = platePath,
    thumbnailPath = thumbnailPath,
    thumbHash = thumbHash,
    dishName = dishName,
    description = description,
    latitude = latitude,
    longitude = longitude,
    publishedAtEpochMs = publishedAtEpochMs,
    ratingSum = ratingSum,
    voterCount = voterCount,
    ingredientsCsv = ingredientsCsv,
    classifierVersion = classifierVersion,
    cuisine = cuisine,
    kind = kind,
    pending = pending,
    idempotencyKey = idempotencyKey,
    ratings = ratings.map {
        LocalRating(raterId = it.raterId, score = it.score.toInt(), atMs = it.atMs, pending = it.pending != 0L, edited = it.edited != 0L)
    },
)
