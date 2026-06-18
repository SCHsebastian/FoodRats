package es.schsebastian.foodrats.feature.auth.data.apple

/**
 * Tokens returned by the platform Sign-in-with-Apple flow, mirroring [es.schsebastian.foodrats
 * .feature.auth.data.google.GoogleIdToken].
 *
 * [identityToken] is the JWT Apple issues; Firebase exchanges it via `OAuthProvider("apple.com")`.
 * [nonce] is the raw nonce whose SHA-256 was sent in the ASAuthorization request — Firebase needs
 * the raw value to validate the token. [authorizationCode] is required only for server-side token
 * revocation. [email]/[fullName] are surfaced by Apple ONLY on the first authorization for an app
 * and must be persisted then (Apple never resends them), so they are captured here.
 *
 * Not yet populated: the platform clients currently return [AuthError.AppleSignIn.NotYetAvailable]
 * — see the per-platform `AppleAuthClient` actuals.
 */
data class AppleSignInToken(
    val identityToken: String,
    val nonce: String? = null,
    val authorizationCode: String? = null,
    val email: String? = null,
    val fullName: String? = null,
)
