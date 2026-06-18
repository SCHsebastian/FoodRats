package es.schsebastian.foodrats.feature.auth.data.apple

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * iOS Sign-in-with-Apple client. Mirrors [es.schsebastian.foodrats.feature.auth.data.google
 * .GoogleAuthClient]: it delegates to the Swift `AppleSignInBridge` via lambdas wired by
 * `authIosModule(...)` from iosApp/ContentView.swift.
 *
 * The Swift side runs an `ASAuthorizationController` Sign-in-with-Apple request with a SHA-256
 * nonce and calls back with `(identityToken, rawNonce, authorizationCode, email, fullName,
 * errorCode)` — exactly one of (identityToken+rawNonce) or errorCode is non-null. Firebase exchanges
 * the identity token + RAW nonce via `OAuthProvider.credential("apple.com", …)` in
 * [es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource.signInWithApple].
 */
actual class AppleAuthClient(
    private val viewControllerProvider: () -> UIViewController,
    private val signInNative: (
        UIViewController,
        (
            identityToken: String?,
            rawNonce: String?,
            authorizationCode: String?,
            email: String?,
            fullName: String?,
            errorCode: String?,
        ) -> Unit,
    ) -> Unit,
    private val signOutNative: () -> Unit,
) {
    actual suspend fun signIn(): Result<AppleSignInToken, AuthError.AppleSignIn> =
        suspendCancellableCoroutine { cont ->
            signInNative(viewControllerProvider()) { identityToken, rawNonce, authCode, email, fullName, errorCode ->
                if (identityToken != null) {
                    cont.resume(
                        Result.success(
                            AppleSignInToken(
                                identityToken = identityToken,
                                nonce = rawNonce,
                                authorizationCode = authCode,
                                email = email,
                                fullName = fullName,
                            ),
                        ),
                    )
                } else {
                    val err = when (errorCode) {
                        "cancelled" -> AuthError.AppleSignIn.UserCancelled
                        "network"   -> AuthError.AppleSignIn.NetworkUnavailable
                        "invalid"   -> AuthError.AppleSignIn.InvalidResponse
                        else        -> AuthError.AppleSignIn.UnknownClientFailure
                    }
                    cont.resume(Result.failure(err))
                }
            }
        }

    actual suspend fun signOut() {
        // Apple keeps no client-side session token (Firebase owns the session); the bridge clears
        // any cached ASAuthorization state if needed. No-op-safe.
        signOutNative()
    }
}
