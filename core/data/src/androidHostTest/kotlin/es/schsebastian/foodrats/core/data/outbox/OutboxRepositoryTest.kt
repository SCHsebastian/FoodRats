package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxRepositoryTest {

    private lateinit var db: OutboxTestDb

    @BeforeTest fun setUp() { db = OutboxTestDb() }
    @AfterTest fun tearDown() = db.close()

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value
    private val rater = (AccountId.of("acc-1") as Result.Ok).value

    private fun rateCommand(score: Int = 4) = PendingCommand.RateMeal(
        crewId = crew,
        mealId = meal,
        raterId = rater,
        score = Score.of(score).getOrNull()!!,
    )

    /** A fresh repo + store over the SAME in-memory table — the process-restart proxy. */
    private fun repo(clock: FixedClock) =
        OutboxRepository(db.store(), clock, db.dispatchers)

    @Test
    fun enqueue_persists_and_survives_a_fresh_store_instance() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))

        val repo1 = repo(clock)
        val enqueued = repo1.enqueue(rateCommand())
        assertTrue(enqueued is Result.Ok)
        val entry = enqueued.value

        // Process-death proxy: a brand-new store + repo over the SAME backing table.
        val repo2 = repo(clock)
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
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = repo(clock)

        repo.enqueue(rateCommand(score = 3))
        clock.set(Instant.parse("2026-06-19T11:00:00Z"))
        repo.enqueue(rateCommand(score = 5)) // same crew/meal/rater → same key

        val all = repo.observePending().first()
        assertEquals(1, all.size)
        assertEquals(rateCommand(score = 5), all.single().command) // last-write-wins
    }

    @Test
    fun markUploading_then_markFailed_increments_attempt_and_sets_failed() = runTest {
        val repo = repo(FixedClock(Instant.parse("2026-06-19T10:00:00Z")))
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
        val repo = repo(FixedClock(Instant.parse("2026-06-19T10:00:00Z")))
        val id = (repo.enqueue(rateCommand()) as Result.Ok).value.id

        repo.remove(id)
        assertTrue(repo.observePending().first().isEmpty())
        // No-op-safe on an already-removed id.
        assertTrue(repo.remove(id) is Result.Ok)
    }

    @Test
    fun requeue_resets_attempt_count_and_status_to_pending() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = repo(clock)
        val id = (repo.enqueue(rateCommand()) as Result.Ok).value.id

        // Simulate the runner exhausting the budget: 5 failed attempts → terminal.
        repeat(5) { _ ->
            repo.markUploading(id)
            repo.markFailed(id, errorKey = "rate.offline", retryable = false)
        }
        val terminal = repo.observePending().first().single()
        assertEquals(5, terminal.attemptCount, "sanity: should have 5 failed attempts")
        assertTrue(
            terminal.status.let { it is OutboxEntryStatus.Failed && !it.retryable },
            "sanity: terminal before requeue",
        )

        // User-initiated retry: requeue must reset the budget.
        val result = repo.requeue(id)
        assertTrue(result is Result.Ok, "requeue must succeed")

        val requeued = repo.observePending().first().single()
        assertEquals(0, requeued.attemptCount, "requeue resets attemptCount to 0")
        assertNull(requeued.lastAttemptAt, "requeue clears lastAttemptAt")
        assertEquals(
            OutboxEntryStatus.Pending, requeued.status,
            "requeue returns status to Pending",
        )
    }

    @Test
    fun observePending_orders_by_createdAt() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = repo(clock)

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
