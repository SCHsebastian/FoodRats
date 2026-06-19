package es.schsebastian.foodrats.core.data.outbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxRepositoryTest {

    /** Backing store shared across "process restarts" — a fresh store instance over the SAME data. */
    private class SharedDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private fun dispatchers(): DispatcherProvider {
        val d = UnconfinedTestDispatcher()
        return object : DispatcherProvider {
            override val main: CoroutineDispatcher = d
            override val io: CoroutineDispatcher = d
            override val default: CoroutineDispatcher = d
        }
    }

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value
    private val rater = (AccountId.of("acc-1") as Result.Ok).value

    private fun rateCommand(score: Int = 4) = PendingCommand.RateMeal(
        crewId = crew,
        mealId = meal,
        raterId = rater,
        score = Score.of(score).getOrNull()!!,
    )

    private fun repo(backing: DataStore<Preferences>, clock: FixedClock) =
        OutboxRepository(OutboxLocalStore(AppPreferences(backing)), clock, dispatchers())

    @Test
    fun enqueue_persists_and_survives_a_fresh_store_instance() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))

        val repo1 = repo(backing, clock)
        val enqueued = repo1.enqueue(rateCommand())
        assertTrue(enqueued is Result.Ok)
        val entry = enqueued.value

        // Process-death proxy: a brand-new store + repo over the SAME backing data.
        val repo2 = repo(backing, clock)
        val restored = repo2.observePending().first()
        assertEquals(1, restored.size)
        val r = restored.single()
        assertEquals(entry.id, r.id)
        assertEquals(OutboxEntryStatus.Pending, r.status)
        assertEquals(0, r.attemptCount)
        assertEquals(rateCommand(), r.command)
    }

    @Test
    fun enqueue_coalesces_on_idempotency_key() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = repo(backing, clock)

        repo.enqueue(rateCommand(score = 3))
        clock.set(Instant.parse("2026-06-19T11:00:00Z"))
        repo.enqueue(rateCommand(score = 5)) // same crew/meal/rater → same key

        val all = repo.observePending().first()
        assertEquals(1, all.size)
        assertEquals(rateCommand(score = 5), all.single().command) // last-write-wins
    }

    @Test
    fun markUploading_then_markFailed_increments_attempt_and_sets_failed() = runTest {
        val backing = SharedDataStore()
        val repo = repo(backing, FixedClock(Instant.parse("2026-06-19T10:00:00Z")))
        val id = (repo.enqueue(rateCommand()) as Result.Ok).value.id

        repo.markUploading(id)
        assertEquals(OutboxEntryStatus.Uploading, repo.observePending().first().single().status)

        repo.markFailed(id, errorKey = "rate.offline", retryable = true)
        val failed = repo.observePending().first().single()
        assertEquals(1, failed.attemptCount)
        assertTrue(failed.status is OutboxEntryStatus.Failed)
        assertEquals("rate.offline", (failed.status as OutboxEntryStatus.Failed).errorKey)
        assertTrue((failed.status as OutboxEntryStatus.Failed).retryable)
    }

    @Test
    fun remove_dequeues_the_entry_and_is_noop_safe() = runTest {
        val backing = SharedDataStore()
        val repo = repo(backing, FixedClock(Instant.parse("2026-06-19T10:00:00Z")))
        val id = (repo.enqueue(rateCommand()) as Result.Ok).value.id

        repo.remove(id)
        assertTrue(repo.observePending().first().isEmpty())
        // No-op-safe on an already-removed id.
        assertTrue(repo.remove(id) is Result.Ok)
    }

    @Test
    fun observePending_orders_by_createdAt() = runTest {
        val backing = SharedDataStore()
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = repo(backing, clock)

        // Two distinct commands (different raters → different idempotency keys, both survive).
        val firstCmd = PendingCommand.RateMeal(
            crewId = crew, mealId = meal,
            raterId = (AccountId.of("acc-A") as Result.Ok).value,
            score = Score.of(4).getOrNull()!!,
        )
        val secondCmd = PendingCommand.RateMeal(
            crewId = crew, mealId = meal,
            raterId = (AccountId.of("acc-B") as Result.Ok).value,
            score = Score.of(4).getOrNull()!!,
        )
        val first = (repo.enqueue(firstCmd) as Result.Ok).value
        clock.set(Instant.parse("2026-06-19T11:00:00Z"))
        val second = (repo.enqueue(secondCmd) as Result.Ok).value

        val ordered = repo.observePending().first()
        assertEquals(listOf(first.id, second.id), ordered.map { it.id })
        assertNull(ordered.first().lastAttemptAt)
    }
}
