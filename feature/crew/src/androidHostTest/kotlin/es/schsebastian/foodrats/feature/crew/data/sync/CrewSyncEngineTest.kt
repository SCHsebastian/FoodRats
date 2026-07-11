package es.schsebastian.foodrats.feature.crew.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDataSource
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import es.schsebastian.foodrats.feature.crew.data.firebase.MemberDto
import es.schsebastian.foodrats.feature.crew.data.local.CrewLocalStore
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.domain.model.CrewCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host-test (JVM) coverage of [CrewSyncEngine] over the REAL SQLDelight store: a controllable
 * [FakeCrewListSource] emits server crew-list snapshots, the engine folds them into
 * [CrewLocalStore], and we assert (1) rows land, (2) a later snapshot full-replaces the set, (3) a
 * `PERMISSION_DENIED` throw stops the sync WITHOUT wiping the cached rows, and (4) [CrewSyncEngine.start]
 * drives the sync off the session and skips a signed-out (`null`) session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewSyncEngineTest {

    private val account = (AccountId.of("acct-1") as Result.Ok).value

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: CrewLocalStore
    private lateinit var appScope: CoroutineScope

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            FoodRatsDatabase.Schema.create(this)
        }
        store = CrewLocalStore(FoodRatsDatabase(driver), dispatchers)
        appScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun engine(
        source: FakeCrewListSource,
        session: SessionProvider = FakeSessionProvider(Session(account, activeCrewId = null)),
    ) = CrewSyncEngine(session = session, dataSource = source, local = store, appScope = appScope)

    private fun dto(id: String, name: String = "Crew $id", createdAtEpochMs: Long) = CrewDto(
        id = id,
        name = name,
        code = "ABCD23",
        ownerId = "acct-1",
        createdAtEpochMs = createdAtEpochMs,
        memberIds = listOf("acct-1"),
        members = mapOf("acct-1" to MemberDto(joinedAtEpochMs = createdAtEpochMs)),
    )

    @Test fun syncAccount_mirrors_a_server_snapshot_into_the_local_store() = runTest {
        val source = FakeCrewListSource()
        engine(source).syncAccount(account)

        source.emit(listOf(dto("c1", createdAtEpochMs = 200L), dto("c2", createdAtEpochMs = 100L)))

        store.observeMyCrews().test {
            assertEquals(listOf("c1", "c2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun a_later_snapshot_full_replaces_the_set() = runTest {
        val source = FakeCrewListSource()
        engine(source).syncAccount(account)

        source.emit(listOf(dto("c1", createdAtEpochMs = 1L), dto("c2", createdAtEpochMs = 2L)))
        // The member left c2 → next snapshot omits it → full replace drops it.
        source.emit(listOf(dto("c1", createdAtEpochMs = 1L)))

        store.observeMyCrews().test {
            assertEquals(listOf("c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun permission_denied_throw_stops_sync_without_wiping_rows() = runTest {
        val source = FakeCrewListSource()
        engine(source).syncAccount(account)

        source.emit(listOf(dto("c1", createdAtEpochMs = 1L)))
        source.fail(RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions."))

        // The cached crew SURVIVES the benign sign-out throw — the engine stopped, it didn't wipe.
        store.observeMyCrews().test {
            assertEquals(listOf("c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun start_drives_syncAccount_off_session_and_skips_a_null_session() = runTest {
        val source = FakeCrewListSource()
        val session = FakeSessionProvider(initial = null)
        engine(source, session).start()

        // No session yet → no sync job, no listener subscribed.
        assertEquals(0, source.subscriptions)

        session.set(Session(account, activeCrewId = null))
        source.emit(listOf(dto("c1", createdAtEpochMs = 1L)))

        assertEquals(1, source.subscriptions)
        store.observeMyCrews().test {
            assertEquals(listOf("c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun syncAccount_is_idempotent_for_an_already_running_account() = runTest {
        val source = FakeCrewListSource()
        val engine = engine(source)

        engine.syncAccount(account)
        engine.syncAccount(account)

        // The second call is a no-op: only ONE listener is subscribed.
        assertEquals(1, source.subscriptions)
    }

    @Test fun a_local_write_failure_is_contained_not_retried_forever() = runTest {
        val source = FakeCrewListSource()
        val failingStore = ThrowingOnceCrewLocalStore(FoodRatsDatabase(driver), dispatchers)
        val engine = CrewSyncEngine(
            session = FakeSessionProvider(Session(account, activeCrewId = null)),
            dataSource = source,
            local = failingStore,
            appScope = appScope,
        )
        engine.syncAccount(account)

        // First snapshot: the local write throws (e.g. disk I/O). If uncontained, retryWhen would
        // treat this as transient and loop forever redriving the SAME snapshot into the SAME
        // failing write. Contained: it's reported and skipped, the collector stays alive, and a
        // later, successful snapshot lands normally with no unbounded retry storm.
        source.emit(listOf(dto("c1", createdAtEpochMs = 1L)))
        advanceUntilIdle()
        assertEquals(1, failingStore.attempts, "the failing write must not be retried")

        source.emit(listOf(dto("c2", createdAtEpochMs = 2L)))
        advanceUntilIdle()

        failingStore.observeMyCrews().test {
            assertEquals(listOf("c2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- H3: Mutex guards jobs map -----------------------------------------------

    @Test fun syncAccount_while_job_is_active_does_not_corrupt_jobs_map() = runTest {
        val source = FakeCrewListSource()
        val engine = engine(source)
        engine.syncAccount(account)
        advanceUntilIdle()
        assertEquals(1, source.subscriptions)

        // A concurrent syncAccount call for the same account is idempotent under the mutex:
        // the idempotency check holds and there is still exactly 1 subscription.
        engine.syncAccount(account)
        advanceUntilIdle()
        assertEquals(1, source.subscriptions)
    }
}

/**
 * Controllable [CrewDataSource] for the sync engine: `observeMyCrews` returns a hot flow the test
 * drives via [emit] (a crew-list snapshot) and [fail] (an upstream throw, e.g. PERMISSION_DENIED on
 * sign-out). Counts live [subscriptions] so a test can assert exactly one listener per account. All
 * other members are unused by the engine and stubbed.
 */
private class FakeCrewListSource : CrewDataSource {
    private val emissions = MutableSharedFlow<Signal>(replay = 1, extraBufferCapacity = 16)
    var subscriptions = 0
        private set

    private sealed interface Signal {
        data class Snapshot(val dtos: List<CrewDto>) : Signal
        data class Throw(val error: Throwable) : Signal
    }

    suspend fun emit(dtos: List<CrewDto>) = emissions.emit(Signal.Snapshot(dtos))
    suspend fun fail(error: Throwable) = emissions.emit(Signal.Throw(error))

    override fun observeMyCrews(accountId: AccountId): Flow<List<CrewDto>> {
        subscriptions++
        return emissions.map { signal ->
            when (signal) {
                is Signal.Snapshot -> signal.dtos
                is Signal.Throw -> throw signal.error
            }
        }
    }

    override suspend fun createCrew(name: String, founder: AccountId, nowMs: Long): CrewDto = error("unused")
    override suspend fun requestToJoin(code: CrewCode, requester: AccountId, nowMs: Long) = Unit
    override fun observeJoinRequests(crewId: CrewId): Flow<List<es.schsebastian.foodrats.feature.crew.data.firebase.JoinRequestDto>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun approveJoinRequest(crewId: CrewId, requester: AccountId, nowMs: Long) = Unit
    override suspend fun declineJoinRequest(crewId: CrewId, requester: AccountId): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun transferOwnership(crewId: CrewId, newOwner: AccountId): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun leave(crewId: CrewId, leaver: AccountId, successor: AccountId?) = Unit
    override suspend fun removeMember(crewId: CrewId, target: AccountId) = Unit
    override fun observeCrew(crewId: CrewId): Flow<CrewDto?> = error("unused")
    override suspend fun fetchOnce(crewId: CrewId): Crew? = null
    override suspend fun fetchByCode(code: CrewCode): Crew = error("unused")
    override suspend fun renameCrew(crewId: CrewId, newName: String): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun deleteCrew(crewId: CrewId, code: CrewCode): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setBlindVoting(crewId: CrewId, enabled: Boolean): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setTagline(crewId: CrewId, tagline: String?): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setWelcomeMessage(crewId: CrewId, message: String?): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setWeeklyChallenge(crewId: CrewId, challenge: String?, setAtMillis: Long?): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setScoreStyle(crewId: CrewId, style: String): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setBannerPath(crewId: CrewId, path: String, token: String): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun clearBannerPath(crewId: CrewId): Result<Unit, CrewError> = Result.success(Unit)
    override suspend fun setBannerFocalY(crewId: CrewId, focalY: Float): Result<Unit, CrewError> = Result.success(Unit)
}

/**
 * Throws on the FIRST [replaceAll] call (simulating a one-off local persistence fault) and delegates
 * to the real implementation thereafter, so a test can assert the engine contains the failure instead
 * of retrying it forever.
 */
private class ThrowingOnceCrewLocalStore(
    database: FoodRatsDatabase,
    dispatchers: DispatcherProvider,
) : CrewLocalStore(database, dispatchers) {
    var attempts = 0
        private set

    override suspend fun replaceAll(crews: List<CrewDto>) {
        attempts++
        if (attempts == 1) throw RuntimeException("disk I/O error")
        super.replaceAll(crews)
    }
}

/** Minimal [SessionProvider] backed by a [MutableStateFlow] the test flips. */
private class FakeSessionProvider(initial: Session?) : SessionProvider {
    private val state = MutableStateFlow(initial)
    override val current: Flow<Session?> = state
    fun set(session: Session?) { state.value = session }
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        state.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}
