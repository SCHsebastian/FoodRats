package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxError
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.feed.presentation.feed.FakeMealRatingPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [OutboxPort] that always fails [enqueue] with [OutboxError.PersistenceUnavailable]. */
private class FailingOutboxPort : OutboxPort {
    val enqueued = mutableListOf<PendingCommand>()
    override suspend fun enqueue(cmd: PendingCommand): Result<OutboxEntry, OutboxError> {
        enqueued += cmd
        return Result.failure(OutboxError.PersistenceUnavailable)
    }
    override fun observePending(): Flow<List<OutboxEntry>> = MutableStateFlow(emptyList())
    override suspend fun markUploading(id: OutboxEntryId): Result<Boolean, OutboxError> = Result.success(true)
    override suspend fun markFailed(id: OutboxEntryId, errorKey: String, retryable: Boolean): Result<Unit, OutboxError> = Result.success(Unit)
    override suspend fun updateStatus(id: OutboxEntryId, status: OutboxEntryStatus): Result<Unit, OutboxError> = Result.success(Unit)
    override suspend fun remove(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
    override suspend fun requeue(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
}

class RateMealUseCaseTest {
    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val mealId = (MealId.of("meal-1") as Result.Ok).value
    private val rater = (AccountId.of("rater-1") as Result.Ok).value
    private val score = (Score.of(4) as Result.Ok).value

    private fun useCase(
        port: FakeMealRatingPort = FakeMealRatingPort(),
        connectivity: FakeConnectivityPort = FakeConnectivityPort(online = true),
        outbox: RecordingOutboxPort = RecordingOutboxPort(),
        optimistic: RecordingOptimisticMealWritePort = RecordingOptimisticMealWritePort(),
    ) = RateMealUseCase(port, connectivity, outbox, optimistic)

    @Test fun delegates_to_port_with_raterId_when_online() = runTest {
        val port = FakeMealRatingPort()
        val outbox = RecordingOutboxPort()
        val optimistic = RecordingOptimisticMealWritePort()

        val result = useCase(port = port, outbox = outbox, optimistic = optimistic)(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size)
        val call = port.calls.first()
        assertEquals("crew-1", call.crewId)
        assertEquals("meal-1", call.mealId)
        assertEquals("rater-1", call.raterId)
        assertEquals(4, call.score)
        assertTrue(outbox.enqueued.isEmpty(), "online path must not touch the outbox")
        assertTrue(optimistic.applied.isEmpty(), "online path must not write optimistically — sync is the source of truth")
    }

    @Test fun surfaces_non_connectivity_port_error() = runTest {
        val port = FakeMealRatingPort().apply {
            nextResult = Result.failure(RateError.CannotRateOwnMeal)
        }
        val outbox = RecordingOutboxPort()
        val optimistic = RecordingOptimisticMealWritePort()

        val result = useCase(port = port, outbox = outbox, optimistic = optimistic)(crew, mealId, rater, score)

        assertEquals(Result.failure(RateError.CannotRateOwnMeal), result)
        assertTrue(outbox.enqueued.isEmpty())
        assertTrue(optimistic.applied.isEmpty(), "a rejected rate must not leave an optimistic row")
    }

    @Test fun offline_enqueues_and_returns_ok_without_calling_port() = runTest {
        val port = FakeMealRatingPort()
        val outbox = RecordingOutboxPort()
        val optimistic = RecordingOptimisticMealWritePort()

        val result = useCase(
            port = port,
            connectivity = FakeConnectivityPort(online = false),
            outbox = outbox,
            optimistic = optimistic,
        )(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertTrue(port.calls.isEmpty(), "offline must not hit the direct port")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RateMeal(crew, mealId, rater, score)),
            outbox.enqueued,
        )
        // The star is rendered optimistically (the feed reads the local store), keyed by the same
        // idempotency token the enqueued command carries so sync can reconcile it.
        assertEquals(1, optimistic.applied.size)
        val applied = optimistic.applied.single()
        assertEquals(crew, applied.crewId)
        assertEquals(mealId, applied.mealId)
        assertEquals(rater, applied.raterId)
        assertEquals(score, applied.score)
        assertEquals(PendingCommand.RateMeal(crew, mealId, rater, score).idempotencyKey, applied.idempotencyKey)
    }

    @Test fun connectivity_class_error_falls_back_to_outbox() = runTest {
        val port = FakeMealRatingPort().apply { nextResult = Result.failure(RateError.RateUnavailable) }
        val outbox = RecordingOutboxPort()
        val optimistic = RecordingOptimisticMealWritePort()

        val result = useCase(port = port, outbox = outbox, optimistic = optimistic)(crew, mealId, rater, score)

        assertTrue(result is Result.Ok)
        assertEquals(1, port.calls.size, "online path attempts the direct write first")
        assertEquals(
            listOf<PendingCommand>(PendingCommand.RateMeal(crew, mealId, rater, score)),
            outbox.enqueued,
        )
        assertEquals(
            PendingCommand.RateMeal(crew, mealId, rater, score).idempotencyKey,
            optimistic.applied.single().idempotencyKey,
            "connectivity-error fallback also renders the star locally",
        )
    }

    // ─── M6: enqueue persistence failure rolls back optimistic state ──────────

    /**
     * If [OutboxPort.enqueue] fails (e.g. [OutboxError.PersistenceUnavailable]), the
     * optimistic star that was written before enqueue must be rolled back via
     * [OptimisticMealWritePort.clearPending], and the use case must return an error
     * (NOT [Result.Ok]) so the UI does not show a permanently-phantom vote.
     */
    @Test fun enqueue_failure_rolls_back_optimistic_star_and_returns_error() = runTest {
        val outbox = FailingOutboxPort()
        val optimistic = RecordingOptimisticMealWritePort()
        val cmd = PendingCommand.RateMeal(crew, mealId, rater, score)

        // Build the use case directly (bypassing the helper that enforces RecordingOutboxPort).
        val result = RateMealUseCase(FakeMealRatingPort(), FakeConnectivityPort(online = false), outbox, optimistic)(crew, mealId, rater, score)

        assertTrue(result is Result.Err, "a persistence failure must NOT return Ok")
        assertEquals(
            RateError.RateUnavailable, result.error,
            "persistence failure maps to RateError.RateUnavailable",
        )
        assertEquals(1, optimistic.applied.size, "the star was applied optimistically before enqueue")
        assertEquals(
            listOf(cmd.idempotencyKey), optimistic.cleared,
            "clearPending must be called with the command's idempotency key on enqueue failure",
        )
        assertFalse(
            optimistic.cleared.isEmpty(),
            "the phantom star must be cleared (not left dangling)",
        )
    }
}
