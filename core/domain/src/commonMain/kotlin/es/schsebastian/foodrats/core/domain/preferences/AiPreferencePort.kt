package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * User opt-out for on-device AI features (plate-photo analysis → ingredient suggestions),
 * persisted locally for fast offline reads. Default is enabled (opt-out model).
 *
 * Absent value in the store = user has not opted out = AI is on.
 */
interface AiPreferencePort {
    /** Whether on-device AI features (plate-photo analysis → ingredient suggestions) are enabled. Default true. */
    val enabled: Flow<Boolean>
    suspend fun set(enabled: Boolean): Result<Unit, AiPreferenceError>
}

sealed interface AiPreferenceError {
    sealed interface Persist : AiPreferenceError {
        data object Unavailable : Persist
    }
}
