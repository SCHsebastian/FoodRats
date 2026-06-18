package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
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
 * collapses to [SessionError.ProviderUnavailable].
 */
internal class AuthSignOutPort(
    private val auth: AuthRepository,
) : SignOutPort {

    override suspend fun signOut(): Result<Unit, SessionError> {
        FrLog.d(FrLog.Tags.SignOut) { "port: signOut entry" }
        return when (val r = auth.signOut()) {
            is Result.Ok  -> {
                FrLog.d(FrLog.Tags.SignOut) { "port: repo returned Ok" }
                Result.success(Unit)
            }
            is Result.Err -> {
                val mapped = r.error.toSessionError()
                FrLog.d(FrLog.Tags.SignOut) { "port: repo returned Err=${r.error} → mapped=$mapped" }
                Result.failure(mapped)
            }
        }
    }

    private fun AuthError.toSessionError(): SessionError = when (this) {
        AuthError.Firebase.AccountDisabled  -> SessionError.AccountDisabled
        AuthError.Firebase.TokenExpired     -> SessionError.TokenExpired
        AuthError.Firebase.NotSignedIn,
        AuthError.Firebase.Unavailable,
        is AuthError.GoogleSignIn,
        is AuthError.EmailPassword          -> SessionError.ProviderUnavailable
    }
}
