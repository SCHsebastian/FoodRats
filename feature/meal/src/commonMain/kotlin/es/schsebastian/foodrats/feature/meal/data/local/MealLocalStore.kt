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
            upserts.forEach { it.write() }
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
            upserts.forEach { it.write() }
        }
    }

    /** Upsert one meal row + replace its ratings. Called inside an open transaction. */
    private fun MealUpsert.write() {
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
        // Re-sync the denormalized ratings: a vote retracted server-side must disappear locally.
        // selectRatingsForMeals + diff would be cheaper, but the snapshot count is tiny and INSERT
        // OR REPLACE keyed on (mealId, raterId) is idempotent; we only delete the stragglers.
        val incomingRaterIds = ratings.map { it.raterId }.toSet()
        queries.selectRatingsForMeals(listOf(mealId)).executeAsList()
            .filterNot { it.raterId in incomingRaterIds }
            .forEach { queries.deleteRating(mealId, it.raterId) }
        ratings.forEach { r ->
            queries.upsertRating(
                mealId = mealId,
                raterId = r.raterId,
                score = r.score.toLong(),
                atMs = r.atMs,
                pending = if (r.pending) 1L else 0L,
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
        LocalRating(raterId = it.raterId, score = it.score.toInt(), atMs = it.atMs, pending = it.pending != 0L)
    },
)
