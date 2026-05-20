package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository

/**
 * Adapter exposing the auth repository's `signOut` through the narrow [SignOutPort]
 * interface in `:core:domain`. Lets features that only need to end the session
 * (e.g. settings) avoid depending on the whole `:feature:auth` module surface.
 *
 * Maps [AuthError] → [SessionError]. The full GoogleSignIn / EmailPassword error
 * trees can't actually fire from sign-out (those are sign-IN paths) so the only
 * meaningful translations are the `AuthError.Firebase.*` leaves; anything else
 * collapses to [SessionError.FirebaseUnavailable].
 */
internal class AuthSignOutPort(
    private val auth: AuthRepository,
) : SignOutPort {

    override suspend fun signOut(): Result<Unit, SessionError> {
        return when (val r = auth.signOut()) {
            is Result.Ok  -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toSessionError())
        }
    }

    private fun AuthError.toSessionError(): SessionError = when (this) {
        AuthError.Firebase.AccountDisabled  -> SessionError.AccountDisabled
        AuthError.Firebase.TokenExpired     -> SessionError.TokenExpired
        AuthError.Firebase.NotSignedIn,
        AuthError.Firebase.Unavailable,
        is AuthError.GoogleSignIn,
        is AuthError.EmailPassword          -> SessionError.FirebaseUnavailable
    }
}
