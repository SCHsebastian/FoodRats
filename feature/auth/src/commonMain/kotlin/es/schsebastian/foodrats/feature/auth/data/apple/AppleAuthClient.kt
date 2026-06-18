package es.schsebastian.foodrats.feature.auth.data.apple

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

/**
 * Platform Sign-in-with-Apple client, the mirror of `GoogleAuthClient`.
 *
 * No constructor is declared on the `expect` (same as `GoogleAuthClient`) so each `actual` is free
 * to take whatever platform handles it needs — today both are parameterless stubs returning
 * [AuthError.AppleSignIn.NotYetAvailable] ("being built"); later iOS will take Swift lambdas (like
 * the Google bridge) and Android a Context for the web-OAuth flow, without touching this expect.
 */
expect class AppleAuthClient {
    suspend fun signIn(): Result<AppleSignInToken, AuthError.AppleSignIn>
    suspend fun signOut()
}
