import FirebaseCore
import FirebaseMessaging
import FoodRatsShared
import GoogleSignIn
import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // FirebaseApp.configure() runs in iOSApp.init() so other services are ready by now.
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        UIApplication.shared.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // GoogleSignIn URL callback — required for the OAuth redirect handshake on iOS < 13.
    // On iOS 13+ the .onOpenURL modifier in iOSApp.swift also handles this, but providing
    // this method is harmless and keeps older callers working.
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        if GIDSignIn.sharedInstance.handle(url) { return true }
        IosDeepLinkBridge.shared.receive(uri: url.absoluteString)
        return true
    }

    // Universal Links: iOS delivers https://foodrats-de4ec.web.app/... taps as a browsing-web user activity.
    // Forward the URL to the shared DeepLinkBus, which RootNavViewModel parses + routes.
    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else { return false }
        IosDeepLinkBridge.shared.receive(uri: url.absoluteString)
        return true
    }

    // Foreground notification handling — show banner + sound and forward the data payload
    // through the Kotlin bridge so the in-app banner observers see it.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        forwardData(notification.request.content.userInfo)
        completionHandler([.banner, .sound, .badge])
    }

    // Notification tap → navigate. The server puts the canonical deep link under "link"; forward
    // it to the shared DeepLinkBus, which RootNavViewModel parses + routes (a meal post / comment
    // opens that meal). A reminder/streak push carries no link, so tapping just foregrounds the
    // app on Feed — the desired "just open it" behaviour.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let link = response.notification.request.content.userInfo["link"] as? String {
            IosDeepLinkBridge.shared.receive(uri: link)
        }
        completionHandler()
    }

    private func forwardData(_ userInfo: [AnyHashable: Any]) {
        var stringMap: [String: String] = [:]
        for (k, v) in userInfo {
            if let key = k as? String, let value = v as? String { stringMap[key] = value }
        }
        IosNotificationBridge.shared.publish(data: stringMap)
    }
}

// FCM token refresh — GitLive observes this internally via the underlying SDK; this delegate
// hook is required by MessagingDelegate but the app does not need to do anything with the
// refreshed token here.
extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        // No-op: IosFcmTokenProvider reads Messaging.messaging().token on demand.
    }
}
