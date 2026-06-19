package es.schsebastian.foodrats.feature.meal.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.RatingEntryDto
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Host-test (JVM) coverage of [MealSyncEngine] over the REAL SQLDelight store: a controllable
 * [FakeSyncFirestore] emits server snapshots, the engine folds them into [MealLocalStore], and we
 * assert (1) rows land, (2) delete-by-absence is scoped to the 30-day window (older rows persist),
 * and (3) a `PERMISSION_DENIED` throw stops the sync WITHOUT wiping the cached rows.
 *
 * "today" is fixed to 2026-06-19, so the rolling 30-day window is 2026-05-21..2026-06-19.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealSyncEngineTest {

    private val zone = TimeZone.UTC
    private val clock = FixedClock(Instant.parse("2026-06-19T12:00:00Z"))
    private val crew = (CrewId.of("c1") as Result.Ok).value

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: MealLocalStore
    private lateinit var appScope: CoroutineScope

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            execute(null, "PRAGMA foreign_keys = ON", 0)
            FoodRatsDatabase.Schema.create(this)
        }
        store = MealLocalStore(FoodRatsDatabase(driver), dispatchers)
        appScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun engine(
        firestore: FakeSyncFirestore,
        activeCrew: ActiveCrewProvider = FakeActiveCrewProvider(crew),
    ) = MealSyncEngine(
        firestore = firestore,
        local = store,
        activeCrew = activeCrew,
        clock = clock,
        zone = zone,
        appScope = appScope,
    )

    private fun dto(
        id: String,
        dayKey: String,
        crewId: String = "c1",
        publishedAtEpochMs: Long = 1L,
        ratings: Map<String, RatingEntryDto> = emptyMap(),
    ) = MealDto(
        id = id,
        authorId = "author-$id",
        authorName = "Author $id",
        crewId = crewId,
        dayKey = dayKey,
        slot = "lunch",
        platePath = "crews/$crewId/meals/$id.jpg",
        dishName = "Lasagna",
        publishedAtEpochMs = publishedAtEpochMs,
        ratings = ratings,
        ratingSum = ratings.values.sumOf { it.score },
        voterCount = ratings.size,
    )

    @Test fun syncCrew_mirrors_a_server_snapshot_into_the_local_window() = runTest {
        val firestore = FakeSyncFirestore()
        engine(firestore).syncCrew(crew)

        firestore.emit(
            listOf(
                dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 300L),
                dto("m2", dayKey = "2026-06-18", publishedAtEpochMs = 200L),
            ),
        )

        store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            assertEquals(setOf("m1", "m2"), awaitItem().map { it.mealId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncCrew_deletes_by_absence_within_window_only_older_rows_persist() = runTest {
        // Seed an OLD row OUTSIDE the window plus two inside it (one of which the next snapshot drops).
        store.upsertAll(
            listOf(
                dto("old", dayKey = "2026-04-01", publishedAtEpochMs = 1L),
                dto("inside-keep", dayKey = "2026-06-10", publishedAtEpochMs = 2L),
                dto("inside-gone", dayKey = "2026-06-11", publishedAtEpochMs = 3L),
            ),
        )

        val firestore = FakeSyncFirestore()
        engine(firestore).syncCrew(crew)
        // The snapshot contains only inside-keep → inside-gone deleted (in-window absence),
        // old retained (outside the window).
        firestore.emit(listOf(dto("inside-keep", dayKey = "2026-06-10", publishedAtEpochMs = 2L)))

        store.observeRange("c1", "2026-04-01", "2026-06-19").test {
            assertEquals(setOf("old", "inside-keep"), awaitItem().map { it.mealId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun permission_denied_throw_stops_sync_without_wiping_rows() = runTest {
        val firestore = FakeSyncFirestore()
        engine(firestore).syncCrew(crew)

        // A first snapshot lands rows, then the listener throws PERMISSION_DENIED (sign-out).
        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
        firestore.fail(RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions."))

        // The cached row SURVIVES the benign sign-out throw — the engine stopped, it didn't wipe.
        store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun start_drives_syncCrew_off_active_crew_and_skips_a_null_selection() = runTest {
        val firestore = FakeSyncFirestore()
        val active = FakeActiveCrewProvider(initial = null)
        engine(firestore, active).start()

        // No active crew yet → no sync job, no listener subscribed.
        assertEquals(0, firestore.subscriptions)

        active.set(crew)
        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))

        assertEquals(1, firestore.subscriptions)
        store.observeFeed("c1", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncCrew_is_idempotent_for_an_already_running_crew() = runTest {
        val firestore = FakeSyncFirestore()
        val engine = engine(firestore)

        engine.syncCrew(crew)
        engine.syncCrew(crew)

        // The second call is a no-op: only ONE listener is subscribed.
        assertEquals(1, firestore.subscriptions)
    }
}

/**
 * Controllable [MealFirestore] for the sync engine: `observeForRange` returns a hot flow the test
 * drives via [emit] (a server snapshot) and [fail] (an upstream throw, e.g. PERMISSION_DENIED on
 * sign-out). Counts live [subscriptions] so a test can assert exactly one listener per active crew.
 */
private class FakeSyncFirestore : es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore {
    private val emissions = MutableSharedFlow<Signal>(replay = 1, extraBufferCapacity = 16)
    var subscriptions = 0
        private set

    private sealed interface Signal {
        data class Snapshot(val dtos: List<MealDto>) : Signal
        data class Throw(val error: Throwable) : Signal
    }

    suspend fun emit(dtos: List<MealDto>) = emissions.emit(Signal.Snapshot(dtos))
    suspend fun fail(error: Throwable) = emissions.emit(Signal.Throw(error))

    override fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>> {
        subscriptions++
        return emissions.map { signal ->
            when (signal) {
                is Signal.Snapshot -> signal.dtos
                is Signal.Throw -> throw signal.error
            }
        }
    }

    override suspend fun mealExists(
        crewId: CrewId,
        authorId: es.schsebastian.foodrats.core.domain.model.AccountId,
        dayKey: String,
        slot: es.schsebastian.foodrats.core.domain.meal.MealSlot,
    ): Boolean = false

    override suspend fun takenSlots(
        crewId: CrewId,
        authorId: es.schsebastian.foodrats.core.domain.model.AccountId,
        dayKey: String,
    ): Set<es.schsebastian.foodrats.core.domain.meal.MealSlot> = emptySet()

    override suspend fun deleteMeal(crewId: CrewId, mealId: String) = Unit

    override suspend fun write(
        dto: MealDto,
        docId: String,
    ) = Unit

    override suspend fun rateMeal(
        crewId: CrewId,
        mealId: String,
        raterUid: String,
        score: Int,
        nowEpochMs: Long,
    ): es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore.RateOutcome =
        es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore.RateOutcome.Ok
}

/** Minimal [ActiveCrewProvider] backed by a [MutableStateFlow] the test flips. */
private class FakeActiveCrewProvider(initial: CrewId?) : ActiveCrewProvider {
    private val state = MutableStateFlow(initial)
    override val current: Flow<CrewId?> = state
    override suspend fun set(crewId: CrewId) { state.value = crewId }
    override suspend fun clear() { state.value = null }
}
