import SwiftUI
import FirebaseCore
import GoogleSignIn

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    init() {
        // Configure Firebase as the very first thing — Firestore, Auth, Storage and
        // Messaging all assume FirebaseApp.configure() has been called before any use.
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
