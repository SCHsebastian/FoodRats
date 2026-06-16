package es.schsebastian.foodrats.feature.auth.data.firebase

/**
 * Typed classification of a raw Firebase Auth failure.
 *
 * Firebase Auth throws platform-specific exceptions whose message text carries the auth
 * error code (GitLive doesn't expose typed cross-platform exceptions consistently), so
 * the *only* place a raw throwable is inspected by message-substring is [toAuthFault]
 * below. `AuthErrorMapper` then maps **by fault type**, never by message — so an SDK
 * message wording change, a locale change, or the eventual Firebase→own-server swap
 * touches exactly one function.
 *
 * Data-layer-private: this type never leaves `data/firebase/`; the domain stays
 * vendor-free.
 */
internal sealed interface AuthFault {
    /** Sign-up rejected: the email address is already registered. */
    data object EmailAlreadyInUse : AuthFault
    /** Password fails the minimum strength rule. */
    data object WeakPassword : AuthFault
    /** Email format is invalid / malformed. */
    data object InvalidEmail : AuthFault
    /** Sign-in rejected (wrong password or unknown user — deliberately conflated). */
    data object WrongCredentials : AuthFault
    /** Account has been disabled by an administrator. */
    data object AccountDisabled : AuthFault
    /** Auth token missing or expired. */
    data object TokenExpired : AuthFault
    /** Connectivity failure reaching Firebase Auth. */
    data object Network : AuthFault
    /** Anything we cannot confidently bucket. */
    data class Unknown(val cause: Throwable) : AuthFault
}

/**
 * The single seam that turns a raw Firebase Auth throwable into a typed [AuthFault].
 *
 * This is the *only* substring-matching site in the auth data layer. Order preserves the
 * original mapper's precedence: email/password-specific codes are checked before the
 * generic `token` / `network` buckets so they don't fall through.
 */
internal fun Throwable.toAuthFault(): AuthFault {
    val m = message.orEmpty().lowercase()
    return when {
        "email-already-in-use" in m || "email already in use" in m || "emailalreadyinuse" in m ->
            AuthFault.EmailAlreadyInUse
        "weak-password" in m || "weak password" in m || "weakpassword" in m ->
            AuthFault.WeakPassword
        "invalid-email" in m || "badly formatted" in m || "malformed" in m ->
            AuthFault.InvalidEmail
        // Firebase deliberately conflates wrong password + unknown user into one code
        // (INVALID_LOGIN_CREDENTIALS) since 2024 — we surface a single fault too.
        "invalid_login_credentials" in m || "invalid-login-credentials" in m ||
            "wrong-password" in m || "user-not-found" in m || "incorrect" in m ->
            AuthFault.WrongCredentials
        "disabled" in m -> AuthFault.AccountDisabled
        "token" in m -> AuthFault.TokenExpired
        "network" in m -> AuthFault.Network
        else -> AuthFault.Unknown(this)
    }
}
