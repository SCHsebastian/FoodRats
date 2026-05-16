// NOTE: FirebaseCore, FirebaseMessaging and GoogleSignIn imports require SPM packages to be added
// to the Xcode project before uncomenting. Firebase iOS SDK and GoogleSignIn-iOS must be added
// via Xcode → File → Add Package Dependencies. See iosApp SETUP.md for instructions.
//
// import FirebaseCore
// import FirebaseMessaging
// import GoogleSignIn
import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // FirebaseApp.configure()          // Uncomment after adding Firebase SPM
        // Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        // UIApplication.shared.registerForRemoteNotifications()   // Uncomment after Firebase SPM
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Messaging.messaging().apnsToken = deviceToken   // Uncomment after Firebase SPM
    }

    // GoogleSignIn URL callback — uncomment after adding GoogleSignIn SPM
    // func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
    //     return GIDSignIn.sharedInstance.handle(url)
    // }

    // FCM token refresh — GitLive observes this internally via the underlying SDK; no-op for MVP.
    // func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {}

    // Foreground notification handling — show banner + sound.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }
}
