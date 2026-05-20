import Foundation
import FirebaseCrashlytics

/// Swift bridge over Firebase Crashlytics, mirroring GoogleSignInBridge.
///
/// FirebaseCrashlytics is resolved via SPM inside Xcode and is invisible to Gradle/Kotlin, so the
/// shared module (IosCrashReporter) calls into these static methods through lambdas wired in
/// ContentView.swift -> MainViewController.
enum CrashlyticsBridge {

    /// Records a non-fatal as an NSError so it shows up in the Crashlytics "non-fatals" tab.
    /// - Parameters:
    ///   - domain: error domain (the Kotlin throwable's class name, or an explicit tag).
    ///   - message: human-readable description, surfaced via NSLocalizedDescriptionKey.
    static func recordNonFatal(domain: String, message: String) {
        let error = NSError(
            domain: domain,
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
        Crashlytics.crashlytics().record(error: error)
    }

    /// Appends a breadcrumb to the next crash/non-fatal report.
    static func log(_ message: String) {
        Crashlytics.crashlytics().log(message)
    }
}
