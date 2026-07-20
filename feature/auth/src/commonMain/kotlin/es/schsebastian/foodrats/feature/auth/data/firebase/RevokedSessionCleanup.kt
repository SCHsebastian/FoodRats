package es.schsebastian.foodrats.feature.auth.data.firebase

import es.schsebastian.foodrats.core.domain.session.LocalDataEraser

/**
 * Ends a session that [indicatesRevokedSession] classified as stale server-side, and scrubs the
 * device's account-scoped local data the same way the voluntary sign-out does.
 *
 * The revoked-session sign-out happens inside [FirebaseAuthDataSource] (cold-start session
 * resolution + foreground revalidate) — NOT through `AuthSignOutPort`, the voluntary-sign-out
 * funnel that runs [LocalDataEraser]. Without an equivalent wipe here, a server-side revocation
 * (account deleted/disabled, token revoked) left the previous user's ActiveCrewId, SQLDelight
 * feed/crew caches, and queued outbox writes behind: the next account on this device then
 * inherited the stale active crew — skipping the crew gate straight into the previous user's
 * cached feed — and replayed the previous user's queued mutations under the new identity
 * (security #3, the exact leak the funnel exists to prevent).
 *
 * Both steps are best-effort and independent: a sign-out failure must not block the wipe, and a
 * wipe failure must not escape into the session flow (which must stay alive to emit null).
 * Ordering matters — sign out FIRST so `authStateChanged` nulls the session before the erase's
 * DataStore writes re-trigger the session `combine`.
 *
 * Extracted with injected lambdas (mirrors [AccountDocSelfHealer]) so `commonTest` can lock the
 * behaviour without a live `FirebaseAuth`.
 */
internal class RevokedSessionCleanup(
    private val signOut: suspend () -> Unit,
    private val localDataEraser: LocalDataEraser,
) {
    suspend fun endRevokedSession() {
        runCatching { signOut() }
        runCatching { localDataEraser.eraseLocalAccountData() }
    }
}
