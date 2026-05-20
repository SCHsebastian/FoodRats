import SwiftUI
import FirebaseCore
import FirebaseCrashlytics
import GoogleSignIn

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    init() {
        // Configure Firebase as the very first thing — Firestore, Auth, Storage and
        // Messaging all assume FirebaseApp.configure() has been called before any use.
        FirebaseApp.configure()

        // Disable Crashlytics collection in debug builds so dev crashes don't pollute prod data.
        // Mirrors AndroidCrashReporter(collectionEnabled = !BuildConfig.DEBUG).
        #if DEBUG
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
        #else
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        #endif
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
