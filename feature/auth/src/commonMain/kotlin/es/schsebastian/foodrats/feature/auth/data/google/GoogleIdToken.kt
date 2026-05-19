package es.schsebastian.foodrats.feature.auth.data.google

/**
 * Tokens returned by the platform GoogleSignIn flow.
 *
 * On Android, accessToken is null because Credential Manager only returns the id token —
 * Firebase Auth on Android accepts a null accessToken in `GoogleAuthProvider.credential`.
 *
 * On iOS, accessToken MUST be non-null — the GitLive Firebase Auth wrapper requires both
 * (the underlying FIRGoogleAuthProvider.credential(withIDToken:accessToken:) is strict).
 */
data class GoogleIdToken(val raw: String, val accessToken: String? = null)
