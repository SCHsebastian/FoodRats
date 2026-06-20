package es.schsebastian.foodrats.core.domain.outbox

import kotlin.jvm.JvmInline

/**
 * Stable, client-generated identifier for an [OutboxEntry] (P2 §1 T1).
 *
 * Lives for the life of the queued entry and is unchanged by retries, so the
 * data layer can enqueue / observe / update-status / dequeue exactly one entry.
 *
 * NOT the mutated record's identity — idempotency rides on
 * [PendingCommand.idempotencyKey] (a deterministic per-command token the runner
 * uses to dedupe replays and to coalesce on enqueue). The data layer generates
 * the raw string (e.g. a platform UUID); domain only carries it as an opaque,
 * non-blank token.
 */
@JvmInline
value class OutboxEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "OutboxEntryId must not be blank" }
    }
}
