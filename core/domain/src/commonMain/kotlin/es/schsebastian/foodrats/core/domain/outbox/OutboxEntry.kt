package es.schsebastian.foodrats.core.domain.outbox

import kotlin.time.Instant

/**
 * A [PendingCommand] durably parked in the write outbox (P2 §1 T1).
 *
 * The single durable unit the data layer persists (DataStore) and the
 * `OutboxRunner` replays. The domain only models its shape, its lifecycle
 * ([status]), and the policy that governs retries ([OutboxRetryPolicy]).
 *
 * @property id stable client-generated id for this entry; constant across all
 *   retries so [OutboxPort] operations address it unambiguously. NOT the mutated
 *   record's identity — idempotency rides on [PendingCommand.idempotencyKey].
 * @property command the user mutation to replay.
 * @property status where this entry is in its lifecycle — see [OutboxEntryStatus].
 * @property attemptCount how many attempts have been made (0 before the first).
 *   Feeds [OutboxRetryPolicy] to compute the next backoff delay and detect the
 *   give-up terminal.
 * @property createdAt when the entry was enqueued (for ordering / staleness).
 * @property lastAttemptAt when the most recent attempt ran, or `null` if never
 *   attempted yet.
 */
data class OutboxEntry(
    val id: OutboxEntryId,
    val command: PendingCommand,
    val status: OutboxEntryStatus,
    val attemptCount: Int = 0,
    val createdAt: Instant,
    val lastAttemptAt: Instant? = null,
)
