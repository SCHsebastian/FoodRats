# iOS (Apple) App Store-readiness audit (2026-06-18)

Scope: `iosApp/` — Info.plist, `iosApp.entitlements`, `PrivacyInfo.xcprivacy`, `project.pbxproj`,
`Config.xcconfig`, Podfile, Assets.xcassets, app-init Swift. Companion to AUDIT.md (Android).

Overall verdict: **strong baseline, one BLOCKING signing gap (now fixed)**. The usual top
rejection cause — a complete `PrivacyInfo.xcprivacy` — was already well-handled.

## PASS — already correct
| Area | Evidence |
|---|---|
| `PrivacyInfo.xcprivacy` | NSPrivacyTracking=false, empty tracking domains, 7 collected-data types (all Tracking=false), 3 required-reason APIs (UserDefaults 1C8F.1, FileTimestamp C617.1, DiskSpace E174.1) |
| Read privacy strings | NSCameraUsageDescription, NSPhotoLibraryUsageDescription, NSLocationWhenInUseUsageDescription all present with real copy |
| Tracking strings absent | NSUserTrackingUsageDescription / NSMicrophone correctly absent (no ATT, no audio) |
| URL schemes | CFBundleURLTypes = Google reversed-client-id + `foodrats`; LSApplicationQueriesSchemes = instagram-stories/instagram |
| Push background mode | UIBackgroundModes = remote-notification |
| GA4 consent defaults | FIREBASE_ANALYTICS_COLLECTION_ENABLED=false + FirebaseAutomaticScreenReportingEnabled=false |
| Localization | CFBundleLocalizations=[en,es], DevelopmentRegion=en, AllowMixedLocalizations |
| Release config | dwarf-with-dsym (Crashlytics), ENABLE_NS_ASSERTIONS=NO, wholemodule, VALIDATE_PRODUCT=YES, ARCHS=arm64 |
| Bitcode | absent (correct — deprecated) |
| Marketing icon | AppIcon 1024×1024 RGBA present + wired (ASSETCATALOG_COMPILER_APPICON_NAME) |
| App init Swift | FirebaseApp.configure(), Messaging.delegate + APNS register, GIDSignIn handle, onOpenURL + continueUserActivity, MainViewController bridges — all present |
| Podfile | platform 18.2, MediaPipeTasksVision 0.10.35 pinned, deliberate no-use_frameworks! |
| Entitlements content | associated-domains = applinks:foodrats-de4ec.web.app (the live host; foodrats.app removed) |

## APPLIED this session (verified by xcodebuild -showBuildSettings)
1. **Wired the entitlements file (BLOCKING).** `Config.xcconfig` →
   `CODE_SIGN_ENTITLEMENTS=iosApp/iosApp.entitlements`. It was an orphan — never referenced in
   pbxproj — so Associated Domains (Universal Links) + aps-environment would have been absent
   from the signed build. Scoped to the app target (its base config); MealAiVision unaffected.
   Verified: `CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements`.
2. **Added `aps-environment` = development** to `iosApp.entitlements` (was missing → no APNs
   entitlement). Xcode promotes to production for distribution archives.
3. **`ITSAppUsesNonExemptEncryption` = false** in Info.plist — skips the manual export-compliance
   prompt on every upload (app uses only exempt HTTPS/TLS crypto).
4. **MealAiVision `IPHONEOS_DEPLOYMENT_TARGET` 26.4 → 18.2** (pbxproj). 26.4 was auto-set by
   Xcode 26.4.1 (CreatedOnToolsVersion) — a framework floor above the app's 18.2 would break
   load on anything < iOS 26.4. Now matches the app + Podfile. Verified: both targets = 18.2.
5. **AppIcon Contents.json** — removed the two dark/tinted appearance entries that were declared
   without image files (unassigned-image warnings); clean single-1024 set. Verified: valid JSON.

Verification: `plutil -lint` OK on Info.plist / entitlements / PrivacyInfo; `python3 -m json.tool`
valid on Contents.json; `xcodebuild -list` parses the project; `xcodebuild -showBuildSettings`
confirms all resolved values. (No archive/device run in this env — see boundary below.)

## NEEDS YOU — Apple portal / CI (can't be done in-repo)
- **Capabilities on the App ID + provisioning profiles.** The newly-wired entitlements require the
  `es.schsebastian.foodrats` App ID to enable **Associated Domains** + **Push Notifications**.
  Local Automatic signing adds them; **CI `fastlane match` profiles MUST be regenerated**
  (`match appstore --force`) or distribution signing will fail on the now-present entitlements.
- **APNs Auth Key** uploaded to Firebase (for FCM→APNs) — operational, not in repo.
- **AASA is already hosted** at `https://foodrats-de4ec.web.app/.well-known/apple-app-site-association`
  (deployed from `website/`). The entitlements + Android manifest now declare ONLY that live host —
  `foodrats.app` was removed (it's a future vanity domain that hosts nothing; see host-fix below).

## RECOMMENDED — open (decision / asset / verification)
| # | Finding | Severity | Note |
|---|---|---|---|
| i1 | Min deployment target = **18.2** | Med (reach) | Excludes all iOS 16/17 devices. Product call — consider lowering to 16.0/17.0 for install base. Not changed. |
| i2 | Launch screen auto-generated **blank** | Low | `INFOPLIST_KEY_UILaunchScreen_Generation=YES`. AAA+ ship a branded UILaunchScreen (parity with the Android splash). Deferred — interacts with GENERATE_INFOPLIST + can't render-verify here. |
| i3 | `PRODUCT_BUNDLE_IDENTIFIER = es.schsebastian.foodrats$(TEAM_ID)` | Low | TEAM_ID is empty so it resolves correctly, but a stray TEAM_ID would corrupt the bundle id + break match/ASC. Smell; left as-is (works today). |
| i4 | AppIcon dark/tinted variants | Low | Now removed (fall back to default). Add real dark/tinted 1024s if design ships them. |
| i5 | NSPhotoLibraryAddUsageDescription absent | Info | Only needed if the app writes images to Photos. Share-card uses the share sheet / pasteboard, not a Photos write — not required. Confirm before any save-to-camera-roll feature. |
| i6 | Privacy required-reason: SystemBootTime | Info | Not declared. Validate against linked SDKs' required-reason usage once (Firebase/GSI/gRPC ship their own manifests). |
