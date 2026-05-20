import UIKit
import SwiftUI
import FoodRatsShared

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
            crashRecordNonFatal: { domain, message in
                CrashlyticsBridge.recordNonFatal(domain: domain, message: message)
            },
            crashLog: { message in
                CrashlyticsBridge.log(message)
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
