package es.schsebastian.foodrats.feature.auth.di

import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * iOS-side Koin module that registers GoogleAuthClient. The Swift caller in iOSApp
 * provides the lambdas at app startup — see iosApp/ContentView.swift.
 */
fun authIosModule(
    viewControllerProvider: () -> UIViewController,
    signIn: (
        UIViewController,
        (idToken: String?, accessToken: String?, errorCode: String?) -> Unit,
    ) -> Unit,
    signOut: () -> Unit,
) = module {
    single { GoogleAuthClient(viewControllerProvider, signIn, signOut) }
}
