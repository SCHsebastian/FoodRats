package es.schsebastian.foodrats.core.domain.meal

import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-context read port for the meal-upload coordinator's state.
 *
 * `:feature:feed` and `:feature:stats` (and any future surface that wants to
 * show "an upload is in flight") inject this port and observe [status]. The
 * write side ([MealUploadCoordinator]) is owned by `:feature:meal`.
 */
interface MealUploadProgressPort {
    val status: StateFlow<MealUploadStatus>
}
