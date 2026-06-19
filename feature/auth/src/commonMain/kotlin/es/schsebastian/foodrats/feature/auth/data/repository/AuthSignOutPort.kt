package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
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
 * This is also the single funnel for every sign-out, so it's where we evict this
 * device's push token (D5). Consuming [TokenRegistrationPort] here — rather than from
 * the auth repository, which is the `SessionProvider` the deregister use case itself
 * depends on — keeps the Koin graph acyclic.
 *
 * Maps [AuthError] → [SessionError]. The full GoogleSignIn / EmailPassword error
 * trees can't actually fire from sign-out (those are sign-IN paths) so the only
 * meaningful translations are the `AuthError.Firebase.*` leaves; anything else
 * collapses to [SessionError.ProviderUnavailable].
 */
internal class AuthSignOutPort(
    private val auth: AuthRepository,
    private val tokenRegistration: TokenRegistrationPort,
) : SignOutPort {

    override suspend fun signOut(): Result<Unit, SessionError> {
        FrLog.d(FrLog.Tags.SignOut) { "port: signOut entry" }
        // Evict THIS device's FCM token from the current account FIRST, while still
        // authenticated (Firestore rules need the uid) and before the auth sign-out
        // clears it — otherwise the per-install token keeps delivering the previous
        // user's pushes to whoever signs in next on this handset (D5). Best-effort:
        // a failure must never block sign-out.
        val deregister = runCatching { tokenRegistration.deregisterCurrentDeviceToken() }.getOrNull()
        FrLog.d(FrLog.Tags.SignOut) { "port: device-token deregister=${deregister ?: "threw"}" }
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
        is AuthError.AppleSignIn,
        is AuthError.EmailPassword          -> SessionError.ProviderUnavailable
    }
}
