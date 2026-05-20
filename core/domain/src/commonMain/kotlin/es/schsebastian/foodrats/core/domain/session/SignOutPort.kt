package es.schsebastian.foodrats.core.domain.session

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Write-side port for ending the current session. Read access lives on [SessionProvider]
 * — kept separate so features that only need to sign out (e.g. settings) can depend on a
 * narrow interface without pulling in the full auth feature surface.
 *
 * Implementations are expected to clear platform auth state, the local session token, and
 * any session-derived caches (e.g. active crew id) so the next sign-in starts clean.
 */
interface SignOutPort {
    suspend fun signOut(): Result<Unit, SessionError>
}
