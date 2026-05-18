import UIKit
import SwiftUI
import FoodRatsShared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            viewControllerProvider: { () -> UIViewController in
                // Walk the key-window scene to find the current root view controller.
                // GoogleSignIn requires a non-nil presenter; falling back to an empty
                // UIViewController would still satisfy types, but in practice the
                // SwiftUI window is always available by the time the user taps sign-in.
                let scenes = UIApplication.shared.connectedScenes
                let windowScene = scenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
                    ?? scenes.compactMap { $0 as? UIWindowScene }.first
                let keyWindow = windowScene?.windows.first(where: { $0.isKeyWindow })
                    ?? windowScene?.windows.first
                return keyWindow?.rootViewController ?? UIViewController()
            },
            googleSignIn: { presenter, completion in
                GoogleSignInBridge.signIn(presenter: presenter) { idToken, errorCode in
                    completion(idToken, errorCode)
                }
            },
            googleSignOut: {
                GoogleSignInBridge.signOut()
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
