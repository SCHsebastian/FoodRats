# iOS setup steps (run once)

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. File → Add Package Dependencies. Add:
     https://github.com/firebase/firebase-ios-sdk    (FirebaseAuth, FirebaseFirestore, FirebaseStorage, FirebaseMessaging)
     https://github.com/google/GoogleSignIn-iOS      (GoogleSignIn, GoogleSignInSwift)
3. Drop `GoogleService-Info.plist` (from Firebase Console) into the `iosApp/iosApp` target.
4. In Info.plist add a URL scheme: under `URL types`, add a single entry whose
   `URL Schemes` array contains the REVERSED_CLIENT_ID value from
   `GoogleService-Info.plist`.
5. Build the Xcode target once to ensure Swift sources compile.

## Uncomment Firebase/GoogleSignIn in iOSApp.swift

After completing the SPM steps above, open `iosApp/iosApp/iOSApp.swift` and:

1. Add at the top:
   ```swift
   import FirebaseCore
   import GoogleSignIn
   ```
2. In `init()`, uncomment:
   ```swift
   FirebaseApp.configure()
   ```
3. In `body`, uncomment the `.onOpenURL` modifier:
   ```swift
   .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
   ```

## Framework import note

The shared framework baseName is `FoodRatsShared` (set in `shared/build.gradle.kts`).
`iOSApp.swift` imports it as `import FoodRatsShared`. If Xcode still references the old
`Shared` framework name (from the initial scaffold), you must update the Xcode project's
framework search path and the embedded build phase after running:

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Then update any remaining `import Shared` occurrences to `import FoodRatsShared`.

# Known issue (2026-05-16)

`navigation-compose 2.8.0-alpha10` is incompatible with Kotlin 2.3.21 on iOS — the framework link step fails with `getBackStackEntry is not found`. The Kotlin compile succeeds. Until a compatible nav-compose version ships, the iOS framework cannot link with the current shared module. Track via the spec.
