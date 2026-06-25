package es.schsebastian.foodrats.feature.auth.data.firebase

/**
 * Does this backend throwable mean the user's SESSION is no longer valid server-side — the account
 * was deleted/disabled, the ID token was revoked, or a security rule now rejects the account-doc read
 * — as opposed to a transient connectivity failure?
 *
 * The session-resolution path uses it to decide between two very different reactions:
 *  - `true`  → the persisted Firebase user is stale: sign it out so [SessionProvider.current] nulls
 *              and the root nav routes to SignIn.
 *  - `false` → a network blip: KEEP the session (Firebase restored the user from local cache, so they
 *              ARE signed in) and proceed offline-first. Signing a valid user out on a blip would be a
 *              far worse bug than the one we're fixing.
 *
 * Transient codes are checked FIRST and short-circuit to `false`, so an `UNAVAILABLE`/network message
 * can never fall through to the revocation buckets. Message-substring is the only cross-platform
 * signal GitLive exposes (same seam as [AuthFault] / the per-feature `FirebaseFault`s); downstream
 * never inspects `message` again.
 */
internal fun Throwable.indicatesRevokedSession(): Boolean {
    val m = message.orEmpty().lowercase()
    // Transient connectivity must NOT sign the user out.
    if ("unavailable" in m || "unreachable" in m || "no route" in m || "network" in m ||
        "timeout" in m || "timed out" in m || "deadline" in m || "offline" in m
    ) {
        return false
    }
    return "permission_denied" in m || "permission-denied" in m || "permission denied" in m ||
        "insufficient permissions" in m ||
        "unauthenticated" in m ||
        "user-disabled" in m || "user_disabled" in m || "account-disabled" in m || "disabled" in m ||
        "user-not-found" in m || "user_not_found" in m || "no user record" in m ||
        "user-token-expired" in m || "token-expired" in m || "token_expired" in m ||
        "user-token-revoked" in m || "token-revoked" in m || "token has been revoked" in m ||
        "revoked" in m || "invalid-user-token" in m
}
