package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * M5 age-out (offline-first): [OutboxTerminalPruner] must drop terminally-failed
 * ([es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus.Failed] with `retryable = false`)
 * entries older than its 30-day retention window, while leaving younger terminal entries and any
 * non-terminal entry (pending, uploading, retryable-failed) — regardless of age — untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxTerminalPrunerTest {

    private lateinit var db: OutboxTestDb

    @BeforeTest fun setUp() { db = OutboxTestDb() }
    @AfterTest fun tearDown() = db.close()

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value

    private fun rateCommand(rater: String, score: Int = 4) = PendingCommand.RateMeal(
        crewId = crew,
        mealId = meal,
        raterId = (AccountId.of(rater) as Result.Ok).value,
        score = Score.of(score).getOrNull()!!,
    )

    /** Runs the pruner's fire-and-forget [CoroutineScope] on the same Unconfined dispatcher the
     * in-memory store uses, so `start()` completes synchronously before the assertions run. */
    private fun pruner(repo: OutboxRepository, clock: FixedClock) =
        OutboxTerminalPruner(repo, clock, CoroutineScope(db.dispatchers.io))

    @Test
    fun prunes_terminal_entries_older_than_30_days_but_keeps_younger_and_non_terminal_ones() = runTest {
        val now = Instant.parse("2026-06-19T10:00:00Z")
        val clock = FixedClock(now)
        val repo = OutboxRepository(db.store(), clock, db.dispatchers)

        // Old terminal (41 days old) — must be pruned.
        clock.set(now - 41.days)
        val oldTerminalId = (repo.enqueue(rateCommand("acc-old-terminal")) as Result.Ok).value.id
        repo.markUploading(oldTerminalId)
        repo.markFailed(oldTerminalId, errorKey = "rate.unauthorized", retryable = false)

        // Recent terminal (5 days old) — younger than the retention window, must be kept.
        clock.set(now - 5.days)
        val recentTerminalId = (repo.enqueue(rateCommand("acc-recent-terminal")) as Result.Ok).value.id
        repo.markUploading(recentTerminalId)
        repo.markFailed(recentTerminalId, errorKey = "rate.unauthorized", retryable = false)

        // Old but still Pending — non-terminal, must be kept regardless of age.
        clock.set(now - 41.days)
        val oldPendingId = (repo.enqueue(rateCommand("acc-old-pending")) as Result.Ok).value.id

        // Old but retryable-failed — not yet terminal, must be kept regardless of age.
        clock.set(now - 41.days)
        val oldRetryableId = (repo.enqueue(rateCommand("acc-old-retryable")) as Result.Ok).value.id
        repo.markUploading(oldRetryableId)
        repo.markFailed(oldRetryableId, errorKey = "rate.offline", retryable = true)

        clock.set(now)
        pruner(repo, clock).start()

        val remainingIds = repo.observePending().first().map { it.id }.toSet()
        assertTrue(oldTerminalId !in remainingIds, "old terminal entry must be pruned")
        assertTrue(recentTerminalId in remainingIds, "recent terminal entry must be kept (younger than retention)")
        assertTrue(oldPendingId in remainingIds, "old pending entry must be kept (non-terminal)")
        assertTrue(oldRetryableId in remainingIds, "old retryable-failed entry must be kept (not yet terminal)")
        assertEquals(3, remainingIds.size, "only the old terminal entry should have been dropped")
    }

    @Test
    fun is_noop_when_the_outbox_is_empty() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val repo = OutboxRepository(db.store(), clock, db.dispatchers)

        pruner(repo, clock).start()

        assertTrue(repo.observePending().first().isEmpty())
    }
}
