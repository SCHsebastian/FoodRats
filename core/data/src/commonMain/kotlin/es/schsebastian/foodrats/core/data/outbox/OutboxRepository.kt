package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxError
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * DataStore-backed [OutboxPort] (offline-first P2 §1 T2). The single IO boundary
 * for the write outbox: exactly one `withContext(dispatchers.io)` per public
 * method (CLAUDE.md rule), delegating the (de)serialization read-modify-write to
 * [OutboxLocalStore].
 *
 * The sibling of `:feature:meal`'s `DraftQueueRepository` (kept untouched) — the
 * write outbox COEXISTS with the meal-publish queue. Persistence failures
 * (DataStore IO) map to the typed [OutboxError.PersistenceUnavailable] leaf so the
 * caller can `when`-exhaust them.
 *
 * The `attemptCount` / `lastAttemptAt` bookkeeping lives here (the port contract
 * says the implementation owns it): [markUploading] stamps `lastAttemptAt` and
 * leaves the count intact; [markFailed] increments `attemptCount` (it counts
 * attempts that have FAILED, 1-based, matching [es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy]).
 * Enqueue coalescing on [PendingCommand.idempotencyKey] is handled by the store.
 */
@OptIn(ExperimentalUuidApi::class)
class OutboxRepository(
    private val store: OutboxLocalStore,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : OutboxPort {

    override suspend fun enqueue(cmd: PendingCommand): Result<OutboxEntry, OutboxError> =
        withContext(dispatchers.io) {
            runCatching {
                val entry = OutboxEntry(
                    id = OutboxEntryId(Uuid.random().toString()),
                    command = cmd,
                    status = OutboxEntryStatus.Pending,
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

    override fun observePending(): Flow<List<OutboxEntry>> = store.observe()

    override suspend fun markUploading(id: OutboxEntryId): Result<Unit, OutboxError> =
        withContext(dispatchers.io) {
            val now = clock.now()
            runCatching {
                store.update(id) { it.copy(status = OutboxEntryStatus.Uploading, lastAttemptAt = now) }
            }.fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("markUploading", it) })
        }

    override suspend fun markFailed(
        id: OutboxEntryId,
        errorKey: String,
        retryable: Boolean,
    ): Result<Unit, OutboxError> = withContext(dispatchers.io) {
        val now = clock.now()
        runCatching {
            store.update(id) {
                it.copy(
                    status = OutboxEntryStatus.Failed(errorKey = errorKey, retryable = retryable),
                    attemptCount = it.attemptCount + 1,
                    lastAttemptAt = now,
                )
            }
        }.fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("markFailed", it) })
    }

    override suspend fun updateStatus(
        id: OutboxEntryId,
        status: OutboxEntryStatus,
    ): Result<Unit, OutboxError> = withContext(dispatchers.io) {
        runCatching { store.update(id) { it.copy(status = status) } }
            .fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("updateStatus", it) })
    }

    override suspend fun remove(id: OutboxEntryId): Result<Unit, OutboxError> =
        withContext(dispatchers.io) {
            runCatching { store.remove(id) }
                .fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("remove", it) })
        }

    override suspend fun requeue(id: OutboxEntryId): Result<Unit, OutboxError> =
        withContext(dispatchers.io) {
            runCatching { store.requeue(id) }
                .fold(onSuccess = { Result.Ok(Unit) }, onFailure = { fail("requeue", it) })
        }

    private fun fail(op: String, t: Throwable): Result<Nothing, OutboxError> {
        FrLog.w("Outbox", t) { "outbox op '$op' failed: ${t.message}" }
        return Result.Err(OutboxError.PersistenceUnavailable)
    }
}
