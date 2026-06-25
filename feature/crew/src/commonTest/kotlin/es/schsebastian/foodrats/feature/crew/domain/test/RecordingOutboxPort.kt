package es.schsebastian.foodrats.feature.crew.domain.test

import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxError
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/** Records [enqueue] calls so tests can assert the offline fallback parked the right command. */
class RecordingOutboxPort : OutboxPort {
    val enqueued = mutableListOf<PendingCommand>()
    private var seq = 0

    override suspend fun enqueue(cmd: PendingCommand): Result<OutboxEntry, OutboxError> {
        enqueued += cmd
        return Result.success(
            OutboxEntry(
                id = OutboxEntryId("entry-${seq++}"),
                command = cmd,
                status = OutboxEntryStatus.Pending,
                createdAt = Instant.fromEpochMilliseconds(0L),
            ),
        )
    }

    override fun observePending(): Flow<List<OutboxEntry>> = MutableStateFlow(emptyList())
    override suspend fun markUploading(id: OutboxEntryId): Result<Boolean, OutboxError> = Result.success(true)
    override suspend fun markFailed(id: OutboxEntryId, errorKey: String, retryable: Boolean): Result<Unit, OutboxError> =
        Result.success(Unit)
    override suspend fun updateStatus(id: OutboxEntryId, status: OutboxEntryStatus): Result<Unit, OutboxError> =
        Result.success(Unit)
    override suspend fun remove(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
    override suspend fun requeue(id: OutboxEntryId): Result<Unit, OutboxError> = Result.success(Unit)
}
