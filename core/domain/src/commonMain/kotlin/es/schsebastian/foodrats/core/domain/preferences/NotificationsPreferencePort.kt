package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * User opt-in for app notifications, persisted locally for fast offline reads.
 * The OS authorization is a separate signal (see :feature:notifications) — both
 * must be true for the user to actually receive notifications.
 */
interface NotificationsPreferencePort {
    val enabled: Flow<Boolean>
    suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError>

    /**
     * One-shot signal: true once the user has gone through the post-signin notification
     * permission screen at least once (regardless of outcome). Drives the post-signin
     * gate in RootNavViewModel.
     */
    val prompted: Flow<Boolean>
    suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError>
}

sealed interface NotificationsPreferenceError {
    sealed interface Persist : NotificationsPreferenceError {
        data object Unavailable : Persist
    }
}
