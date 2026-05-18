package es.schsebastian.foodrats.feature.auth.data.google

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * iOS GoogleAuthClient. Delegates the actual native sign-in to a Swift bridge supplied
 * at app startup via [signInNative] / [signOutNative] lambdas (see iosApp/ContentView.swift
 * + iosApp/GoogleSignInBridge.swift). Kotlin/Native exports lambdas as Swift closures, so
 * Swift implements them directly without needing to declare a Kotlin interface.
 *
 * Error contract: [signInNative] callback receives `(idToken, errorCode)`. On success,
 * `idToken` is non-null and `errorCode` is null. On failure, `idToken` is null and
 * `errorCode` is one of:
 *   - "cancelled"   -> AuthError.GoogleSignIn.UserCancelled
 *   - "no_accounts" -> AuthError.GoogleSignIn.NoGoogleAccountsOnDevice
 *   - "network"     -> AuthError.GoogleSignIn.NetworkUnavailable
 *   - anything else -> AuthError.GoogleSignIn.UnknownClientFailure
 */
actual class GoogleAuthClient(
    private val viewControllerProvider: () -> UIViewController,
    private val signInNative: (UIViewController, (idToken: String?, errorCode: String?) -> Unit) -> Unit,
    private val signOutNative: () -> Unit,
) {
    actual suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn> =
        suspendCancellableCoroutine { cont ->
            signInNative(viewControllerProvider()) { idToken, errorCode ->
                if (idToken != null) {
                    cont.resume(Result.success(GoogleIdToken(idToken)))
                } else {
                    val err = when (errorCode) {
                        "cancelled"   -> AuthError.GoogleSignIn.UserCancelled
                        "no_accounts" -> AuthError.GoogleSignIn.NoGoogleAccountsOnDevice
                        "network"     -> AuthError.GoogleSignIn.NetworkUnavailable
                        else          -> AuthError.GoogleSignIn.UnknownClientFailure
                    }
                    cont.resume(Result.failure(err))
                }
            }
        }

    actual suspend fun signOut() {
        signOutNative()
    }
}
