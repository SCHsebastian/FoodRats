# PROGRESS — manifest + build.gradle store-readiness (2026-06-18)

Goal: audit `AndroidManifest.xml` + `androidApp/build.gradle.kts` against AAA+ store standards; fix gaps.

## Done
1. **Audit** — full pass/fail in AUDIT.md. Verdict: fundamentally store-ready; gaps are polish.
2. **Locale filtering** — `androidResources { localeFilters += listOf("en","es") }`. Strips ~80
   dead transitive-dep locales. `generateLocaleConfig` tried + reverted (Compose-resource i18n
   makes it emit an en-only locale config). Verified: assembleRelease green.
3. **Notification mono icon + FCM defaults** (user-approved) — `ic_stat_notification.xml` (toque),
   `colors.xml` accent, manifest FCM `default_notification_icon/_color/_channel_id`,
   `DailyInactivityWorker` now uses the mono icon + accent (runtime resolve, launcher fallback).
4. **Branded splash** (user-approved) — core-splashscreen 1.0.1, `Theme.FoodRats(.Splash)`,
   `values-night` bg, `MainActivity.installSplashScreen()`, manifest launch theme swap.

Verification: `:feature:notifications:testAndroidHostTest :androidApp:assembleRelease` →
`BUILD SUCCESSFUL in 2m 6s`; lintVitalRelease passed; merged manifest carries the splash theme +
FCM metadata. Logs: VERIFY.log, VERIFY-icon-splash.log.

## Done — iOS (Apple) side (IOS-AUDIT.md)
5. **Wired orphaned entitlements (BLOCKING)** — `Config.xcconfig` CODE_SIGN_ENTITLEMENTS; was
   never referenced → Universal Links + APNs absent from signed build.
6. **aps-environment** added to entitlements; **ITSAppUsesNonExemptEncryption=false** in Info.plist.
7. **MealAiVision deployment target 26.4 → 18.2** (Xcode 26.4.1 auto-set bug); AppIcon Contents.json
   empty dark/tinted entries removed.
Verified via plutil -lint + xcodebuild -showBuildSettings (CODE_SIGN_ENTITLEMENTS resolves, both
targets 18.2, bundle id es.schsebastian.foodrats) + xcodebuild -list parses.

iOS NEEDS-USER (Apple portal/CI): regenerate `match appstore` profiles with Associated Domains +
Push capabilities (else distribution signing fails on the now-present entitlements); host AASA file.
iOS RECOMMENDED: min target 18.2 cuts iOS 16/17 reach (product call); branded launch screen (i2).

## Done — App Store Connect app id + deep-link host correction (2nd request)
8. **ASC numeric app id `6781682875`** wired: `fastlane/Fastfile` `ASC_APP_ID` const → `apple_id:` on
   both iOS upload lanes (API-key auth resolves the app directly); `website/index.html` App Store URL
   filled (`id6781682875`). Verified: `ruby -c` Syntax OK.
9. **Deep-link host corrected `foodrats.app` → `foodrats-de4ec.web.app`** (the LIVE host that serves
   the AASA/assetlinks from `website/`; `foodrats.app` hosts nothing). Removed `foodrats.app` from
   the iOS entitlements + Android App-Links intent-filter; collapsed `DeepLink.kt` to a single
   canonical `WEB_HOST = foodrats-de4ec.web.app` (was doc-only `WEB_HOST=foodrats.app` + `HOSTING_HOST`);
   `inviteUrl` unchanged output. Updated deeplinks/README, IOS-AUDIT, AUDIT, human.md.
   Verified: `:shared:testAndroidHostTest` (DeepLinkParser/RootNav) + `:androidApp:processReleaseMainManifest`
   → BUILD SUCCESSFUL in 11s; merged manifest App-Links host = foodrats-de4ec.web.app only;
   entitlements plutil OK. Log: VERIFY-host-fix.log.
   Follow-up when foodrats.app vanity domain is mapped: re-add to manifest/entitlements + flip WEB_HOST.

## Open / decisions
- **R4 native debug symbols** (`ndk { debugSymbolLevel = "FULL" }`) — NOT applied (NDK-toolchain
  risk on the release build). Decide before relying on MediaPipe native crash stacks in Play.
- **R-locale per-app language picker** — deferred; needs hand-authored locales_config + on-device
  proof that Compose resources follow the system per-app locale.
- **On-device check** — splash handoff + toque rendering not verifiable in this env.
- **Uncommitted** — all changes are working-tree only (branch `develop`); not committed.
