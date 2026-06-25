package es.schsebastian.foodrats.core.domain.session

/**
 * Proactively re-checks that the current session is still valid server-side and signs the user out if
 * it isn't.
 *
 * The identity provider auto-refreshes its token only periodically (~hourly for Firebase), so a
 * server-side account disable/delete or token revocation can go undetected for up to an hour while the
 * app stays open — the user keeps acting on authenticated screens with writes silently failing.
 * [revalidate] forces an immediate token round-trip; on a revoked-session failure the implementation
 * signs out, which nulls [SessionProvider.current] and routes the app to SignIn. It is a no-op when
 * signed out or when the session is still valid, and transient network failures are ignored (they must
 * never sign a valid user out). Intended to be called on app foreground.
 */
interface SessionRevalidator {
    suspend fun revalidate()
}
