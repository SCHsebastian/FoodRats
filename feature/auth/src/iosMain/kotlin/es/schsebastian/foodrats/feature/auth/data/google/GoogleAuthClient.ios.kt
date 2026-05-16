package es.schsebastian.foodrats.feature.auth.data.google

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import platform.UIKit.UIViewController

// The Swift GoogleSignInBridge integration is wired up in Task 5.3.
// For now, return UnknownClientFailure as a placeholder so the build is green;
// real Swift bridge call will replace this body once Phase 5 wires SPM/CocoaPods.
actual class GoogleAuthClient(
    private val viewControllerProvider: () -> UIViewController,
) {
    actual suspend fun signIn(): Result<GoogleIdToken, AuthError.GoogleSignIn> =
        Result.failure(AuthError.GoogleSignIn.UnknownClientFailure)

    actual suspend fun signOut() = Unit
}
