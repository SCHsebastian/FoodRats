package es.schsebastian.foodrats.core.domain.meal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-context read port for the meal-upload coordinator's state.
 *
 * `:feature:feed` and `:feature:stats` (and any future surface that wants to
 * show "an upload is in flight") inject this port and observe [status]. The
 * write side ([MealUploadCoordinator]) is owned by `:feature:meal`.
 *
 * [queue] is the offline-first extension (roadmap §5.2): the aggregate count of
 * drafts durably queued for publish (pending / failed). The durable-queue
 * coordinator overrides it with a live flow derived from `DraftQueuePort.observe()`;
 * single-upload implementers and test fakes that have no queue can back it with
 * [DEFAULT_QUEUE].
 */
interface MealUploadProgressPort {
    val status: StateFlow<MealUploadStatus>

    /** Aggregate queued/failed counts for the feed top bar — see [MealUploadQueueSnapshot]. */
    val queue: StateFlow<MealUploadQueueSnapshot>

    companion object {
        /**
         * A stable, always-empty queue flow for implementers without a durable
         * queue. Shared so observers keep a single live subscription — never
         * instantiate a fresh [MutableStateFlow] inside a property getter.
         */
        val DEFAULT_QUEUE: StateFlow<MealUploadQueueSnapshot> =
            MutableStateFlow(MealUploadQueueSnapshot.EMPTY)
    }
}
