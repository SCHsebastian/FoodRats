package es.schsebastian.foodrats.feature.achievements.data.repository

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementErrorMapper
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementUnlockStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun acct(id: String): AccountId = (AccountId.of(id) as Result.Ok).value

private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val main = d
    override val default = d
    override val io = d
}

/** Records writes; emits whatever unlock map (or error) the test seeds. */
private class FakeUnlockStore : AchievementUnlockStore {
    val emitted = MutableStateFlow<Map<String, Long>>(emptyMap())
    var failObserveWith: Throwable? = null
    var failRecordWith: Throwable? = null
    val recordedWrites = mutableListOf<Pair<String, Map<String, Long>>>()

    override fun observeUnlocks(uid: String): Flow<Map<String, Long>> =
        failObserveWith?.let { t -> flow { throw t } } ?: emitted

    override suspend fun recordUnlocks(uid: String, unlocks: Map<String, Long>) {
        failRecordWith?.let { throw it }
        recordedWrites += uid to unlocks
        emitted.value = emitted.value + unlocks
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAchievementRepositoryTest {

    private fun repo(store: FakeUnlockStore): FirebaseAchievementRepository {
        val dispatcher = UnconfinedTestDispatcher()
        return FirebaseAchievementRepository(store, TestDispatchers(dispatcher), AchievementErrorMapper())
    }

    @Test
    fun observe_maps_snapshot_to_ok_map() = runTest {
        val store = FakeUnlockStore().apply { emitted.value = mapOf("first_plate" to 100L) }
        repo(store).observeUnlocks(acct("alice")).test {
            assertEquals(Result.success(mapOf("first_plate" to 100L)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observe_maps_permission_failure_to_unauthorized() = runTest {
        val store = FakeUnlockStore().apply { failObserveWith = RuntimeException("PERMISSION_DENIED: nope") }
        repo(store).observeUnlocks(acct("alice")).test {
            assertEquals(Result.failure(AchievementProgressError.Unauthorized), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun observe_maps_other_failure_to_unavailable() = runTest {
        val store = FakeUnlockStore().apply { failObserveWith = RuntimeException("network down") }
        repo(store).observeUnlocks(acct("alice")).test {
            assertEquals(Result.failure(AchievementProgressError.Unavailable), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun record_writes_newly_unlocked_and_returns_ok() = runTest {
        val store = FakeUnlockStore()
        val result = repo(store).recordUnlocks(acct("alice"), mapOf("meals_10" to 555L))
        assertEquals(Result.success(Unit), result)
        assertEquals(listOf("alice" to mapOf("meals_10" to 555L)), store.recordedWrites)
    }

    @Test
    fun record_is_a_noop_when_nothing_newly_unlocked() = runTest {
        val store = FakeUnlockStore()
        val result = repo(store).recordUnlocks(acct("alice"), emptyMap())
        assertEquals(Result.success(Unit), result)
        assertTrue(store.recordedWrites.isEmpty(), "empty newlyUnlocked must not hit the store")
    }

    @Test
    fun record_maps_failure_to_typed_error() = runTest {
        val store = FakeUnlockStore().apply { failRecordWith = RuntimeException("unavailable: backend") }
        val result = repo(store).recordUnlocks(acct("alice"), mapOf("meals_10" to 1L))
        assertEquals(Result.failure(AchievementProgressError.Unavailable), result)
    }

    @Test
    fun newly_persisted_unlock_appears_in_the_next_observe_emission() = runTest {
        // End-to-end reconcile shape exercised against the port: a write surfaces on the read stream,
        // which is how the ViewModel's "earned" flip happens after recordUnlocks (spec §6.3).
        val store = FakeUnlockStore().apply { emitted.value = mapOf("first_plate" to 100L) }
        val repository = repo(store)
        repository.observeUnlocks(acct("alice")).test {
            assertEquals(Result.success(mapOf("first_plate" to 100L)), awaitItem())
            repository.recordUnlocks(acct("alice"), mapOf("meals_10" to 999L))
            assertEquals(
                Result.success(mapOf("first_plate" to 100L, "meals_10" to 999L)),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
