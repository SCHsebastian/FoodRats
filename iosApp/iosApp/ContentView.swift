import UIKit
import SwiftUI
import FoodRatsShared
import MealAiVision

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            viewControllerProvider: { () -> UIViewController in
                let scenes = UIApplication.shared.connectedScenes
                let windowScene = scenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
                    ?? scenes.compactMap { $0 as? UIWindowScene }.first
                let keyWindow = windowScene?.windows.first(where: { $0.isKeyWindow })
                    ?? windowScene?.windows.first
                return keyWindow?.rootViewController ?? UIViewController()
            },
            googleSignIn: { presenter, completion in
                GoogleSignInBridge.signIn(presenter: presenter) { idToken, accessToken, errorCode in
                    completion(idToken, accessToken, errorCode)
                }
            },
            googleSignOut: {
                GoogleSignInBridge.signOut()
            },
            appleSignIn: { presenter, completion in
                AppleSignInBridge.signIn(presenter: presenter) { identityToken, rawNonce, authorizationCode, email, fullName, errorCode in
                    completion(identityToken, rawNonce, authorizationCode, email, fullName, errorCode)
                }
            },
            appleSignOut: {
                AppleSignInBridge.signOut()
            },
            crashRecordNonFatal: { domain, message in
                CrashlyticsBridge.recordNonFatal(domain: domain, message: message)
            },
            crashLog: { message in
                CrashlyticsBridge.log(message)
            },
            classifyPlate: { jpeg, completion in
                MediaPipeClassifierBridge.classify(jpeg: jpeg as Data) { labels, errorCode in
                    completion(labels, errorCode)
                }
            },
            share: { text in
                ShareBridge.shareText(text)
            },
            storyShare: { pngBytes in
                // KotlinByteArray -> Data, then hand to the Stories / fallback presenter.
                let data = pngBytes.toData()
                return KotlinInt(value: StoryShareBridge.shareToStories(data))
            },
            analyticsLogEvent: { name, params in
                AnalyticsBridge.logEvent(name: name, params: params)
            },
            analyticsSetUserId: { accountId in
                AnalyticsBridge.setUserId(accountId)
            },
            analyticsSetUserProperty: { name, value in
                AnalyticsBridge.setUserProperty(name: name, value: value)
            },
            analyticsSetConsent: { granted in
                // The Kotlin `(Boolean) -> Unit` lambda param is exported boxed (KotlinBoolean) —
                // unbox before handing it to the Bool-typed bridge.
                AnalyticsBridge.setConsent(granted: granted.boolValue)
            },
            analyticsReset: {
                AnalyticsBridge.resetData()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
