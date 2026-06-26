package es.schsebastian.foodrats.core.domain.outbox

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for the durable write outbox (P2 §1 T1).
 *
 * Declared in `:core:domain` (cross-context: feed/crew/meal all enqueue through
 * it). The data task implements it over local persistence (DataStore) + the
 * `OutboxRunner`. This port only declares the enqueue / observe / update-status /
 * dequeue contract.
 *
 * IDEMPOTENCY. [enqueue] coalesces on [PendingCommand.idempotencyKey]: a new
 * command whose key matches an existing Pending/Failed entry REPLACES it
 * (last-write-wins), so an offline user who rates the same meal twice ends up
 * with one queued command, and a replay can never apply twice.
 */
interface OutboxPort {

    /**
     * Durably enqueue [cmd] and return the created entry (with its generated
     * [OutboxEntryId], [OutboxEntryStatus.Pending] status, `attemptCount = 0`, and
     * `createdAt`). Coalesces on [PendingCommand.idempotencyKey] — an existing
     * Pending/Failed entry with the same key is replaced. The entry survives
     * process death; the `OutboxRunner` replays it when connectivity allows.
     */
    suspend fun enqueue(cmd: PendingCommand): Result<OutboxEntry, OutboxError>

    /**
     * Observe the full outbox (ordered by `createdAt`). Emits a fresh list on
     * every change so the runner and any UI surface can react. Empty when nothing
     * is queued.
     */
    fun observePending(): Flow<List<OutboxEntry>>

    /**
     * Conditional claim: atomically transitions entry [id] from [OutboxEntryStatus.Pending]
     * to [OutboxEntryStatus.Uploading]. Returns [Result.Ok] with `true` if the claim
     * succeeded (the entry was Pending and is now Uploading); [Result.Ok] with `false`
     * if the entry was already Uploading, Failed, or absent — another drain owns it or
     * it has been removed. The caller must NOT call [execute] when `false` is returned.
     */
    suspend fun markUploading(id: OutboxEntryId): Result<Boolean, OutboxError>

    /**
     * Record a failed attempt: set [OutboxEntryStatus.Failed] with [errorKey] and
     * [retryable], and increment `attemptCount`. The runner consults
     * [OutboxRetryPolicy] (with the new `attemptCount`) to decide whether to
     * schedule another attempt or leave the entry in a terminal failed state.
     */
    suspend fun markFailed(
        id: OutboxEntryId,
        errorKey: String,
        retryable: Boolean,
    ): Result<Unit, OutboxError>

    /**
     * Update the lifecycle [status] of the entry [id], bumping `attemptCount` /
     * `lastAttemptAt` as appropriate. No-op-safe if the entry is already gone.
     */
    suspend fun updateStatus(id: OutboxEntryId, status: OutboxEntryStatus): Result<Unit, OutboxError>

    /**
     * Remove the entry [id]. Called after a successful (or already-applied) replay
     * or when the user dismisses a terminally-failed entry. No-op-safe if already
     * removed.
     */
    suspend fun remove(id: OutboxEntryId): Result<Unit, OutboxError>

    /**
     * User-initiated retry: transition the terminally-failed entry [id] back to
     * [OutboxEntryStatus.Pending] AND reset its attempt counter to 0 so the runner
     * grants a fresh backoff budget. Unlike [updateStatus], this is the ONLY path
     * that resets `attemptCount`. The automatic backoff re-arm path
     * (`OutboxRunner.scheduleRetry`) calls [updateStatus] and must NOT reset the
     * count — only an explicit user retry gets a fresh budget.
     */
    suspend fun requeue(id: OutboxEntryId): Result<Unit, OutboxError>

    /**
     * M5 housekeeping: drop terminally-failed entries ([OutboxEntryStatus.Failed] with
     * `retryable = false`) whose `createdAt` is strictly older than [beforeEpochMs]. Pending,
     * uploading, and retryable-failed entries are never touched. Called fire-and-forget at app
     * start to bound unbounded growth of dismissed terminal entries.
     *
     * Default is a no-op `Result.Ok(Unit)` so non-durable adapters / test doubles need not
     * implement housekeeping; the durable implementation overrides it behind its single IO
     * boundary.
     */
    suspend fun pruneTerminal(beforeEpochMs: Long): Result<Unit, OutboxError> = Result.Ok(Unit)
}
