package es.schsebastian.foodrats.feature.auth.di

import es.schsebastian.foodrats.feature.auth.data.apple.AppleAuthClient
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * iOS-side Koin module that registers GoogleAuthClient + AppleAuthClient. The Swift caller in iOSApp
 * provides the lambdas at app startup — see iosApp/ContentView.swift.
 */
fun authIosModule(
    viewControllerProvider: () -> UIViewController,
    signIn: (
        UIViewController,
        (idToken: String?, accessToken: String?, errorCode: String?) -> Unit,
    ) -> Unit,
    signOut: () -> Unit,
    appleSignIn: (
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
    appleSignOut: () -> Unit,
) = module {
    single { GoogleAuthClient(viewControllerProvider, signIn, signOut) }
    // Native Sign-in-with-Apple — Swift AppleSignInBridge runs ASAuthorizationController and the
    // identity token + raw nonce are exchanged via OAuthProvider("apple.com") in the data source.
    single { AppleAuthClient(viewControllerProvider, appleSignIn, appleSignOut) }
}
