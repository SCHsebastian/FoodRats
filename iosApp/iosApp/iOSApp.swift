import SwiftUI
import FirebaseCore
import FirebaseCrashlytics
import FoodRatsShared
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
                    // GoogleSignIn claims its OAuth redirect first; anything else is a
                    // custom-scheme deep link (foodrats://app/...) → forward to the shared bus.
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    IosDeepLinkBridge.shared.receive(uri: url.absoluteString)
                }
        }
    }
}
