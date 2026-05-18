# iOS setup steps (run once)

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. **File → Add Package Dependencies**. Add:
   - `https://github.com/firebase/firebase-ios-sdk` — products: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `FirebaseMessaging` (and `FirebaseCore` if not transitively pulled).
   - `https://github.com/google/GoogleSignIn-iOS` — products: `GoogleSignIn`, `GoogleSignInSwift`.
3. Drop `GoogleService-Info.plist` (from Firebase Console — applicationId `es.schsebastian.foodrats`) into the `iosApp/iosApp` target. Make sure "Copy items if needed" + "Add to targets: iosApp" are checked.
4. In `Info.plist`:
   - Under **URL types**, add an entry whose `URL Schemes` array contains the `REVERSED_CLIENT_ID` value from `GoogleService-Info.plist`. Without this entry the Google OAuth redirect won't return into the app.
   - Add a **Background Modes** capability with `Remote notifications` checked (project target → Signing & Capabilities → +Capability → Background Modes → Remote notifications).
5. Build the Xcode target once to ensure Swift sources compile.

## What's already wired in Swift

You don't need to edit `iOSApp.swift`, `AppDelegate.swift`, `ContentView.swift`, or `GoogleSignInBridge.swift` — they ship configured to:
- `FirebaseApp.configure()` in `iOSApp.init()`.
- `Messaging.messaging().delegate = self` + `registerForRemoteNotifications()` in `AppDelegate.application(_:didFinishLaunchingWithOptions:)`.
- Handle the GoogleSignIn URL callback via both `application(_:open:options:)` and SwiftUI's `.onOpenURL { _ = GIDSignIn.sharedInstance.handle(url) }`.
- Pass `viewControllerProvider`, `googleSignIn`, and `googleSignOut` closures into `MainViewControllerKt.MainViewController(...)` so the Kotlin `GoogleAuthClient` on iOS delegates to the Swift `GoogleSignInBridge` static methods.

If Xcode flags a missing import after adding the SPM packages, do a clean build (`⇧⌘K`) and rebuild — SPM resolution is sometimes lazy on the first attempt.

## Framework import note

The shared framework `baseName` is `FoodRatsShared` (set in `shared/build.gradle.kts`). `iOSApp.swift`, `AppDelegate.swift`, and `ContentView.swift` import it as `import FoodRatsShared`. If Xcode still references the old `Shared` framework name (from the initial scaffold), you must update the Xcode project's framework search path and the embedded build phase after running:

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Then update any remaining `import Shared` occurrences to `import FoodRatsShared`.

## Running on a physical iPhone

1. Plug the iPhone in and select it as the run destination in Xcode.
2. **Signing & Capabilities** → your Apple Team. For a free personal team, the bundle id must be unique to your team (you may need to change `es.schsebastian.foodrats` to something like `es.schsebastian.foodrats.dev`); if you do that, the Firebase project and `GoogleService-Info.plist` need the same id, and the Google Cloud OAuth client's bundle id needs updating too — otherwise sign-in returns "unknown" because the audience won't match.
3. First run on the device will prompt you to **Trust** the developer certificate on the iPhone: Settings → General → VPN & Device Management → Developer App → Trust.
4. Press ⌘R. The app launches, FCM tries to register (will ask for notification permission), and tapping "Continue with Google" presents the GoogleSignIn picker driven by the in-app Safari controller.

## What used to be broken (now fixed)

`navigation-compose 2.8.0-alpha10` was incompatible with Kotlin 2.3.21 on iOS — the framework link step failed with `getBackStackEntry is not found`. The fix is the version bump to **2.9.2** (stable) in `gradle/libs.versions.toml`. The native cache workarounds in `gradle.properties` were removed; you should not need `kotlin.native.cacheKind=none` anywhere.
