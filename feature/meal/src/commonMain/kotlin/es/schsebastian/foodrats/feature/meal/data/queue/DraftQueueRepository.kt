package es.schsebastian.foodrats.feature.meal.data.queue

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * DataStore-backed [DraftQueuePort] (roadmap §5.2). The single IO boundary for
 * the offline-first publish queue: exactly one `withContext(dispatchers.io)` per
 * public method (CLAUDE.md rule), delegating the (de)serialization read-modify-
 * write to [DraftQueueLocalStore].
 *
 * Persistence failures (DataStore IO) are mapped to the closest typed leaf,
 * [MealError.Publish.PublishUnavailable] — there is deliberately no dedicated
 * "queue IO failed" leaf (the domain task confirmed no new `MealError`), and a
 * persistence failure means the upload subsystem is, for now, unavailable.
 *
 * The `attemptCount` / `lastAttemptAt` bookkeeping lives here (the port contract
 * says the implementation owns it): [markUploading] stamps `lastAttemptAt` and,
 * for a re-attempt of a previously-failed entry, leaves the count intact;
 * [markFailed] increments `attemptCount` (it counts attempts that have FAILED,
 * 1-based, matching `DraftRetryPolicy`).
 */
@OptIn(ExperimentalUuidApi::class)
class DraftQueueRepository(
    private val store: DraftQueueLocalStore,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : DraftQueuePort {

    override suspend fun enqueue(draft: MealDraft): Result<QueuedDraft, MealError> =
        withContext(dispatchers.io) {
            runCatching {
                val entry = QueuedDraft(
                    id = QueueEntryId(Uuid.random().toString()),
                    draft = draft,
                    status = QueuedDraftStatus.Pending,
                    attemptCount = 0,
                    createdAt = clock.now(),
                    lastAttemptAt = null,
                )
                store.add(entry)
                entry
            }.fold(
                onSuccess = { Result.Ok(it) },
                onFailure = { fail("enqueue", it) },
            )
        }

    override fun observe(): Flow<List<QueuedDraft>> = store.observe()

    override suspend fun updateStatus(
        id: QueueEntryId,
        status: QueuedDraftStatus,
    ): Result<Unit, MealError> = withContext(dispatchers.io) {
        runCatching { store.update(id) { it.copy(status = status) } }
            .fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("updateStatus", it) })
    }

    override suspend fun markUploading(id: QueueEntryId): Result<Unit, MealError> =
        withContext(dispatchers.io) {
            val now = clock.now()
            runCatching {
                store.update(id) { it.copy(status = QueuedDraftStatus.Uploading, lastAttemptAt = now) }
            }.fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("markUploading", it) })
        }

    override suspend fun markFailed(
        id: QueueEntryId,
        errorKey: String,
        retryable: Boolean,
    ): Result<Unit, MealError> = withContext(dispatchers.io) {
        val now = clock.now()
        runCatching {
            store.update(id) {
                it.copy(
                    status = QueuedDraftStatus.Failed(errorKey = errorKey, retryable = retryable),
                    attemptCount = it.attemptCount + 1,
                    lastAttemptAt = now,
                )
            }
        }.fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("markFailed", it) })
    }

    override suspend fun remove(id: QueueEntryId): Result<Unit, MealError> =
        withContext(dispatchers.io) {
            runCatching { store.remove(id) }
                .fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("remove", it) })
        }

    private fun fail(op: String, t: Throwable): Result<Nothing, MealError> {
        FrLog.w("DraftQueue", t) { "queue op '$op' failed: ${t.message}" }
        return Result.Err(MealError.Publish.PublishUnavailable)
    }
}
