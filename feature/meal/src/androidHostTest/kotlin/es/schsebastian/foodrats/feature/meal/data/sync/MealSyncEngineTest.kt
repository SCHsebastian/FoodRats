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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    // --- feed freshness + manual refresh (P4-T2) --------------------------------

    @Test fun lastSyncedAt_is_stamped_with_clock_after_a_window_write() = runTest {
        val firestore = FakeSyncFirestore()
        val engine = engine(firestore)
        engine.syncCrew(crew)

        engine.lastSyncedAt(crew).test {
            assertEquals(null, awaitItem()) // not synced yet
            firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
            // Stamped with the fixed clock once the window write commits.
            assertEquals(clock.now(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun refresh_re_subscribes_the_listener_for_the_crew() = runTest {
        val firestore = FakeSyncFirestore()
        val engine = engine(firestore)
        engine.syncCrew(crew)
        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
        assertEquals(1, firestore.subscriptions)

        // A manual refresh cancels the running job and re-subscribes a fresh listener.
        engine.refresh(crew)
        assertEquals(2, firestore.subscriptions)

        // Cached rows survive the cancel — refresh never wipes.
        store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- H3: Mutex guards jobs map -----------------------------------------------

    @Test fun refresh_while_sync_is_active_ends_with_exactly_one_job() = runTest {
        val firestore = FakeSyncFirestore()
        val engine = engine(firestore)
        engine.syncCrew(crew)
        advanceUntilIdle()
        assertEquals(1, firestore.subscriptions)

        // refresh() while the job is active must cancel the old job and start exactly one new one.
        engine.refresh(crew)
        advanceUntilIdle()

        // After refresh: exactly 2 total subscriptions (old cancelled, new started).
        assertEquals(2, firestore.subscriptions)

        // Send a snapshot on the new subscription — it must land (proves new job is active).
        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
        store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- H4: auto-retry on transient errors --------------------------------------

    @Test fun transient_error_auto_retries_and_data_flows_after_recovery() = runTest {
        // H4: a non-permission-denied error triggers retryWhen, not .catch (terminal). The engine
        // retries and, once the source recovers (emits a good snapshot), the new data lands.
        //
        // Root cause of the scheduling issue: the shared `appScope` uses a detached
        // UnconfinedTestDispatcher whose virtual clock is NOT the TestScope's scheduler.
        // advanceUntilIdle() only drains the TestScope's scheduler, so the 1s backoff delay in
        // retryWhen would be stuck forever. Fix: give this test an appScope that shares the
        // TestScope's scheduler (via UnconfinedTestDispatcher(testScheduler)) so advanceUntilIdle()
        // also advances the backoff delay.
        val sharedDispatcher = UnconfinedTestDispatcher(testScheduler)
        val sharedDispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = sharedDispatcher
            override val io: CoroutineDispatcher = sharedDispatcher
            override val default: CoroutineDispatcher = sharedDispatcher
        }
        val h4AppScope = CoroutineScope(SupervisorJob() + sharedDispatcher)
        val h4Store = MealLocalStore(FoodRatsDatabase(driver), sharedDispatchers)

        val expected = listOf(
            dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L),
            dto("m2", dayKey = "2026-06-18", publishedAtEpochMs = 2L),
        )
        val fakeOneFailFirestore = FakeOneFailFirestore(
            firstError = RuntimeException("network timeout"),
            recovery = expected,
        )
        val engine = MealSyncEngine(
            firestore = fakeOneFailFirestore,
            local = h4Store,
            activeCrew = FakeActiveCrewProvider(crew),
            clock = clock,
            zone = zone,
            appScope = h4AppScope,
        )
        engine.syncCrew(crew)
        advanceUntilIdle()

        // The engine must have retried after the transient error and written the recovery data.
        h4Store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            val ids = awaitItem().map { it.mealId }.toSet()
            assertEquals(setOf("m1", "m2"), ids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun permission_denied_stops_sync_without_retrying_and_rows_survive() = runTest {
        // Regression guard for H4: the terminal permission-denied path must still not wipe rows
        // and must NOT trigger a retry (subscriptions stays at 1).
        val firestore = FakeSyncFirestore()
        engine(firestore).syncCrew(crew)
        advanceUntilIdle()

        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
        firestore.fail(RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions."))
        advanceUntilIdle()

        // PERMISSION_DENIED must NOT trigger a retry → subscriptions stays at 1.
        assertEquals(1, firestore.subscriptions)

        store.observeRange("c1", "2026-05-21", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- L3: persist lastSyncedAt across process death ---------------------------

    @Test fun lastSyncedAt_stamp_round_trips_across_engine_restart() = runTest {
        val firestore = FakeSyncFirestore()
        val tsPort = InMemoryTimestampPort()
        val engine1 = MealSyncEngine(
            firestore = firestore,
            local = store,
            activeCrew = FakeActiveCrewProvider(crew),
            clock = clock,
            zone = zone,
            appScope = appScope,
            timestampStore = tsPort,
        )
        engine1.syncCrew(crew)
        firestore.emit(listOf(dto("m1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)))
        advanceUntilIdle()

        // Confirm stamp was persisted to the port.
        assertEquals(clock.now(), tsPort.store[crew.value])

        // Simulate process restart: new engine hydrates from the persisted port.
        val freshFirestore = FakeSyncFirestore()
        val engine2 = MealSyncEngine(
            firestore = freshFirestore,
            local = store,
            activeCrew = FakeActiveCrewProvider(crew),
            clock = clock,
            zone = zone,
            appScope = CoroutineScope(SupervisorJob() + dispatchers.default),
            timestampStore = tsPort,
        )
        engine2.start()
        advanceUntilIdle()

        // The freshly started engine should expose the persisted stamp immediately via lastSyncedAt.
        engine2.lastSyncedAt(crew).test {
            assertEquals(clock.now(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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

    override suspend fun existingMealIds(
        crewId: CrewId,
        authorId: es.schsebastian.foodrats.core.domain.model.AccountId,
        dayKey: String,
    ): Set<String> = emptySet()

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

/** In-memory [SyncTimestampPort] for testing L3 persistence logic without a real DataStore. */
private class InMemoryTimestampPort : SyncTimestampPort {
    var store: Map<String, Instant> = emptyMap()
    override suspend fun load(): Map<String, Instant> = store
    override suspend fun save(stamps: Map<String, Instant>) { store = stamps }
}

/**
 * A [MealFirestore] fake that throws [firstError] on the FIRST collect of [observeForRange], then
 * emits [recovery] on every subsequent collect. This allows testing H4's retryWhen behavior without
 * the infinite-retry-with-replay-Throw problem of [FakeSyncFirestore].
 */
private class FakeOneFailFirestore(
    private val firstError: Throwable,
    private val recovery: List<MealDto>,
) : es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore {
    private var hasThrown = false

    override fun observeForRange(crewId: CrewId, from: MealDay, to: MealDay): Flow<List<MealDto>> =
        flow {
            if (!hasThrown) {
                hasThrown = true
                throw firstError
            }
            // Emit recovery and complete (no awaitCancellation) so the test can observe the data.
            emit(recovery)
        }

    override suspend fun existingMealIds(
        crewId: CrewId,
        authorId: es.schsebastian.foodrats.core.domain.model.AccountId,
        dayKey: String,
    ): Set<String> = emptySet()

    override suspend fun deleteMeal(crewId: CrewId, mealId: String) = Unit

    override suspend fun write(dto: MealDto, docId: String) = Unit

    override suspend fun rateMeal(
        crewId: CrewId,
        mealId: String,
        raterUid: String,
        score: Int,
        nowEpochMs: Long,
    ): es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore.RateOutcome =
        es.schsebastian.foodrats.feature.meal.data.firebase.MealFirestore.RateOutcome.Ok
}
