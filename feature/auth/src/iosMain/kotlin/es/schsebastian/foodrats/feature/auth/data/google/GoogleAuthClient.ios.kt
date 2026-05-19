package es.schsebastian.foodrats.feature.auth.data.google

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * iOS GoogleAuthClient. Delegates to the Swift GoogleSignInBridge via lambdas wired by
 * `authIosModule(...)` from iosApp/ContentView.swift.
 *
 * The Swift callback signature is `(idToken, accessToken, errorCode) -> Unit`. iOS's GitLive
 * Firebase Auth requires BOTH idToken and accessToken to build a Google credential — Android
 * accepts a null accessToken, but iOS doesn't (see GoogleAuthProvider.credential signature).
 */
actual class GoogleAuthClient(
    private val viewControllerProvider: () -> UIViewController,
    private val signInNative: (
        UIViewController,
        (idToken: String?, accessToken: String?, errorCode: String?) -> Unit,
    ) -> Unit,
    private val signOutNative: () -> Unit,
) {
    actual suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn> =
        suspendCancellableCoroutine { cont ->
            signInNative(viewControllerProvider()) { idToken, accessToken, errorCode ->
                if (idToken != null) {
                    cont.resume(Result.success(GoogleIdToken(idToken, accessToken)))
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
