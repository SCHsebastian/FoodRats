package es.schsebastian.foodrats.feature.meal.domain.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * A [MealDraft] durably parked in the offline-first publish queue (roadmap §5.2).
 *
 * When a plate is composed offline — or an upload fails — the draft is enqueued
 * as a [QueuedDraft] and re-published when connectivity returns. The entry is
 * the single durable unit the data layer persists (DataStore / SQLDelight) and
 * the WorkManager / iOS background task retries; the domain only models its
 * shape, its lifecycle ([status]), and the policy that governs retries
 * ([es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy]).
 *
 * IDEMPOTENCY. A retried publish must NOT create a duplicate meal. We do not
 * need a separate idempotency key: the publish path already derives a
 * *deterministic* per-crew meal id from `(crewId, authorId, day, slot)` via
 * `MealId.forDaySlot(...)`, so re-publishing the same [draft] overwrites the
 * same Firestore document (and its Storage blob at the same deterministic path)
 * rather than creating a new one. [QueueEntryId] is therefore a *queue-tracking*
 * id (stable across retries so the data layer can address/observe/update/remove
 * exactly this entry), NOT the meal's identity. The two are independent: one
 * queue entry whose audience spans N crews maps to N deterministic meal ids.
 *
 * @property id stable client-generated id for this queue entry; constant across
 *   all retries of the same draft so [DraftQueuePort] operations address it
 *   unambiguously.
 * @property draft the composed draft to publish (carries its audience crew set,
 *   the captured [Plate], dish, slot, ingredients, etc.).
 * @property status where this entry is in its lifecycle — see [QueuedDraftStatus].
 * @property attemptCount how many publish attempts have been made (0 before the
 *   first attempt). Feeds [DraftRetryPolicy] to compute the next backoff delay
 *   and to detect the give-up terminal.
 * @property createdAt when the entry was enqueued (for ordering / staleness).
 * @property lastAttemptAt when the most recent attempt ran, or `null` if never
 *   attempted yet.
 */
data class QueuedDraft(
    val id: QueueEntryId,
    val draft: MealDraft,
    val status: QueuedDraftStatus,
    val attemptCount: Int = 0,
    val createdAt: Instant,
    val lastAttemptAt: Instant? = null,
)

/**
 * Stable, client-generated identifier for a [QueuedDraft] entry. Lives for the
 * life of the queue entry and is unchanged by retries, so the data layer can
 * enqueue / observe / update-status / dequeue exactly one entry.
 *
 * NOT the meal's identity — see [QueuedDraft] for why idempotency rides on the
 * deterministic `MealId.forDaySlot(...)` instead. The data layer generates the
 * raw string (e.g. a platform UUID); domain only carries it as an opaque,
 * non-blank token.
 */
@JvmInline
value class QueueEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "QueueEntryId must not be blank" }
    }
}
