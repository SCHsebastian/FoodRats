package es.schsebastian.foodrats.core.domain.outbox

/**
 * Failures of the write-outbox persistence boundary ([OutboxPort], P2 §1 T1).
 *
 * A `sealed interface` with `data object` leaves (not an enum, no `Unknown`), so
 * the presentation layer can `when`-exhaust it and a leaf can grow a payload
 * later. These are outbox-storage failures only — the per-command business
 * failures are an `OutboxExecuteResult.Terminal`/`Retryable` the runner records
 * as an [OutboxEntryStatus.Failed] errorKey, not an [OutboxError].
 */
sealed interface OutboxError {
    /** The durable store (DataStore) could not be read or written. */
    data object PersistenceUnavailable : OutboxError

    /** A queued entry could not be (de)serialized to/from its persisted form. */
    data object Serialization : OutboxError
}
