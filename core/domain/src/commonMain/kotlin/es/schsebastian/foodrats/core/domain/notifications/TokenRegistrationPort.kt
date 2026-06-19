package es.schsebastian.foodrats.core.domain.notifications

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Port for registering the current device's push-notification token with the backend.
 *
 * Declared in :core:domain so :feature:auth can call it after sign-in without taking a
 * dependency on :feature:notifications. The adapter lives in :feature:notifications.
 */
interface TokenRegistrationPort {
    /** Idempotent: safe to call after every successful sign-in. */
    suspend fun registerCurrentDeviceToken(): Result<Unit, TokenRegistrationError>

    /**
     * Removes this device's token from the currently signed-in account. Call BEFORE
     * signing out (while still authenticated — Firestore rules need the uid), otherwise
     * the per-install token keeps pointing this handset at the previous user's pushes
     * once a different account signs in on the same device.
     */
    suspend fun deregisterCurrentDeviceToken(): Result<Unit, TokenRegistrationError>
}

sealed interface TokenRegistrationError {
    data object NoToken : TokenRegistrationError
    data object NotSignedIn : TokenRegistrationError
    data object Unavailable : TokenRegistrationError
}
