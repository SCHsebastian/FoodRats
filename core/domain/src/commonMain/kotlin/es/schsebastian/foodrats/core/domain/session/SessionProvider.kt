package es.schsebastian.foodrats.core.domain.session

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow

interface SessionProvider {
    /**
     * The current session, or `null` when the user is signed out.
     *
     * Contract: the **first** emitted value is authoritative — implementations must NOT emit a
     * placeholder `null` while auth state is still being restored. "No value emitted yet" means
     * "auth resolving"; consumers that gate UI on sign-in (the root navigator) should hold a
     * loading/splash state until the first emission rather than treating absence as signed-out.
     */
    val current: Flow<Session?>

    /** Suspends until [current] resolves; returns [SessionError.NotSignedIn] only on a real `null`. */
    suspend fun requireCurrent(): Result<Session, SessionError>
}

sealed interface SessionError {
    data object NotSignedIn          : SessionError
    data object TokenExpired         : SessionError
    data object AccountDisabled      : SessionError
    /** The auth/identity provider was unreachable (network, outage, …). Vendor-agnostic name. */
    data object ProviderUnavailable  : SessionError
}
