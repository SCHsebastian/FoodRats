# Apple Sign-In — REAL flow on iOS (2026-06-18, follow-up)

Goal: turn the wired-but-stubbed seam into a working login on **iOS** (the TestFlight path).
Android stays the honest "coming soon" stub — GitLive firebase-auth 2.1.0 wraps no web/redirect
OAuth (confirmed from on-disk 2.1.0 sources), so Android Apple login needs the native SDK + Apple
Service-ID web config; out of scope for the iOS friends-beta and not runtime-verifiable here.

Apple credential (GitLive 2.1.0): `OAuthProvider.credential(providerId="apple.com", idToken=<jwt>,
rawNonce=<raw>)` → `auth.signInWithCredential(cred).user?.uid`. Mirrors the Google path exactly.

Flow: Swift `AppleSignInBridge` (ASAuthorizationController + SHA-256 nonce) →
(identityToken, rawNonce, authCode, email, fullName, errorCode) → `AppleAuthClient.ios` (lambdas) →
`AppleSignInToken` → `FirebaseAuthDataSource.signInWithApple(token)` →
`FirebaseAuthRepository.signInWithApple()` → `finishSignIn(uid)`. ViewModel/Screen unchanged
(already branch NotYetAvailable→"coming soon" vs else→real result; Android keeps NotYetAvailable).

Changes: data source `signInWithApple` · repo real exchange · `AppleAuthClient.ios` · `authIosModule`
Apple lambdas · `MainViewController` Apple params · `ContentView.swift` Apple lambdas ·
`AppleSignInBridge.swift` (NEW, auto-included via PBXFileSystemSynchronizedRootGroup) ·
`iosApp.entitlements` `com.apple.developer.applesignin` · +1 ViewModel success test · CLAUDE.md note.

Manual (not codeable here): (1) Firebase console → enable Apple provider. (2) Apple Developer App ID
→ enable Sign in with Apple capability + regenerate distribution profile. (3) Android later: Apple
Service ID/key + Firebase OAuth redirect.

Signing follow-up (2026-06-18): added a non-interactive **`ios rotate_signing`** fastlane lane
(`fastlane/Fastfile`) — `app_store_connect_api_key` (ASC key auth, no Apple ID/2FA) + `match(type:
appstore, readonly: false, force: true)` to re-issue the App Store profile after the App ID gained
the Sign in with Apple capability (the Matchfile is `readonly(true)`; the lane overrides it). Raw
`bundle exec fastlane match appstore --force` can't run here: fastlane wasn't installed and
MATCH_GIT_URL/MATCH_PASSWORD/ASC key envs are unset + Apple login is interactive. Verified:
`bundle install --path vendor/bundle` (no sudo) → `bundle exec fastlane lanes` lists `ios
rotate_signing`. User runs it with ASC_KEY_ID/ASC_ISSUER_ID/ASC_KEY_PATH + MATCH_GIT_URL/MATCH_PASSWORD
set. Docs updated: `docs/testflight-beta.md` (Paso 0a-2 + checklist), `docs/cicd-runbook.md`. New
untracked `Gemfile.lock` pins the toolchain (commit it). For a one-off manual Archive, match isn't
needed — Xcode automatic signing re-issues the profile.

Verification (2026-06-18, all green):
- `:feature:auth:testAndroidHostTest` — BUILD SUCCESSFUL; SignInViewModelTest tests=16/0 (incl. new
  `apple_sign_in_success_emits_signedIn_and_tracks_login`), AuthErrorToStringKeyTest 19/0,
  AuthModuleVerifyTest 1/0 (Koin graph still complete).
- `:androidApp:assembleDebug` + `:shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL.
- **Full iOS app `xcodebuild ... build`** (simulator, CODE_SIGNING_ALLOWED=NO) — **BUILD SUCCEEDED**.
  First full iOS app link since `firebase-functions` was added to `:core:data` — required two
  pre-existing fixes surfaced by the build (see below).

Pre-existing blockers found + fixed (NOT part of the Apple seam, but blocked ALL iOS builds):
1. `AnalyticsBridge.setConsent(granted:)` — Kotlin `(Boolean)->Unit` lambda param exports boxed
   (`KotlinBoolean`); ContentView passed it to a `Bool` param → compile error. Fixed: `.boolValue`.
2. `FirebaseFunctions` was missing from the iOS SPM (`:core:data` uses `dev.gitlive:firebase-functions`
   → undefined `FIRFunctions`/`FIRHTTPSCallableResult` at link). Fixed: added the product to
   `project.pbxproj` (XCSwiftPackageProductDependency + packageProductDependencies + PBXBuildFile +
   Frameworks phase). This closes the long-standing "FirebaseFunctions iOS SPM" pending gate.

---

# Apple Sign-In — full structure, login deferred ("being built")

Goal: wire the complete Sign-in-with-Apple seam end-to-end (mirroring the Google path) so a
real implementation later is a small, contained change; for now the button shows a friendly
"being built / coming soon" notice instead of authenticating.

## Layers (mirror of the Google Sign-In path)

| Layer | Google | Apple (this change) |
|---|---|---|
| Domain error | `AuthError.GoogleSignIn.*` | `AuthError.AppleSignIn.*` (+ `NotYetAvailable` = "being built") |
| Token VO | `GoogleIdToken` | `AppleSignInToken` |
| Platform port | `expect class GoogleAuthClient` | `expect class AppleAuthClient` |
| Android actual | Credential Manager | stub → `NotYetAvailable` |
| iOS actual | Swift `GoogleSignInBridge` lambdas | stub → `NotYetAvailable` |
| Repo method | `signInWithGoogle()` | `signInWithApple()` |
| Analytics dim | `AuthMethod.GOOGLE` | `AuthMethod.APPLE` |
| Intent | `ContinueWithGoogle` | `ContinueWithApple` |
| UI | Google button | Apple button + "coming soon" info notice |

## Decision: where "being built" lives

The platform `AppleAuthClient.signIn()` returns `AuthError.AppleSignIn.NotYetAvailable`. The
ViewModel renders that one leaf as a friendly **info** notice (not the red error banner); any
*other* AppleSignIn error (future, real flow) goes through the normal error banner. Real login
later = implement the platform client + Firebase OAuthProvider("apple.com") exchange in the
repo; the rest of the seam is already wired.

## Next steps to actually enable Apple login (NOT done here)

1. iOS: `ASAuthorizationController` Sign-in-with-Apple in a Swift bridge → identity token + nonce,
   thread lambdas into `authIosModule(...)` like Google; implement `AppleAuthClient.ios`.
2. Android: web-OAuth (Custom Tabs) or Firebase `OAuthProvider` reauth flow; implement
   `AppleAuthClient.android`.
3. `FirebaseAuthDataSource.signInWithApple(token)` via `OAuthProvider("apple.com")`, then flip the
   repo's TODO branch to `finishSignIn(uid)`.
4. Enable Apple as a sign-in provider in the Firebase console; configure Apple Developer
   Service ID + key.

## Verification — all green (2026-06-18)
- [x] `:core:domain:testAndroidHostTest` — BUILD SUCCESSFUL; `AnalyticsTaxonomyTest` tests=6 failures=0
- [x] `:feature:auth:testAndroidHostTest` — BUILD SUCCESSFUL; `AuthErrorToStringKeyTest` tests=19/0,
      `SignInViewModelTest` tests=15/0, `AuthModuleVerifyTest` tests=1/0 (Koin graph complete)
- [x] `:androidApp:assembleDebug` — BUILD SUCCESSFUL
- [x] `:shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL (only benign expect/actual-Beta warnings)

## Files touched
- domain: `AuthError.AppleSignIn` (+ NotYetAvailable), `data/apple/AppleSignInToken.kt`,
  `data/apple/AppleAuthClient.kt` (expect) + android/ios actuals
- repo: `AuthRepository.signInWithApple()`, `FirebaseAuthRepository` (appleClient arg + method + signOut),
  `AuthSignOutPort` (exhaustive when)
- DI: `authModule` repo binding (+1 get), `authIosModule` (+AppleAuthClient), `FoodRatsApplication.androidAuthModule`
- analytics: `AuthMethod.APPLE`
- presentation: `SignInContract` (ContinueWithApple + appleComingSoon), `SignInViewModel.doApple()`,
  `SignInScreen` (Apple button + info notice), `AuthStringKey` (+2), `AuthErrorToStringKey` (+5 branches)
- i18n: `auth_signin_continue_apple`, `auth_error_apple_coming_soon` (en/es)
- tests: `FakeAuthRepository`, `AuthErrorToStringKeyTest` (+5), `SignInViewModelTest` (+3)
