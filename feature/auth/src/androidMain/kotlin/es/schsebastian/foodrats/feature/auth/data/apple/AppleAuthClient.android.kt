package es.schsebastian.foodrats.feature.auth.data.apple

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

/**
 * Android Sign-in-with-Apple client — STUB. The seam is wired; the flow is "being built".
 *
 * When implemented, Apple sign-in on Android is a web-OAuth flow (Custom Tabs / Firebase
 * `OAuthProvider("apple.com")` `signInWithProvider`); this class would take a Context/Activity
 * provider and return a real [AppleSignInToken]. Until then it returns
 * [AuthError.AppleSignIn.NotYetAvailable], which the SignIn screen renders as a "coming soon" notice.
 */
actual class AppleAuthClient {
    actual suspend fun signIn(): Result<AppleSignInToken, AuthError.AppleSignIn> =
        Result.failure(AuthError.AppleSignIn.NotYetAvailable)

    actual suspend fun signOut() {
        // No persisted Apple session yet — no-op until the real flow lands.
    }
}
