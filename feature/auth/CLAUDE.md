# :feature:auth

Google Sign-In bounded context. Credential Manager on Android, `GoogleSignInBridge` (Swift) on iOS, Firebase Auth exchange behind it. The canonical exemplar for the per-feature DDD layout.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §9 (Exemplar A — Auth, the full feature walkthrough).
- Root `CLAUDE.md` — "Module graph", "iOS status" (the iOS Google Sign-In wiring via lambdas).
- Status — feature done and on `main`; the iOS lambda bridge is wired in `MainViewController` + `ContentView.swift`.

## Local rules

- `GoogleAuthClient` is the platform expect — Android calls Credential Manager, iOS receives `(viewControllerProvider, signIn, signOut)` lambdas from Swift via `authIosModule(...)`. Don't import Firebase types in domain.
- JVM target **17** (Firebase BOM).

## Apple Sign-In — LIVE on iOS, "coming soon" on Android (2026-06-18)

Sign-in-with-Apple is **implemented and building on iOS**. The flow mirrors Google exactly: Swift
`AppleSignInBridge` runs `ASAuthorizationController` with a SHA-256 nonce → `AppleAuthClient.ios`
(lambdas wired by `authIosModule`) returns an `AppleSignInToken` → `FirebaseAuthDataSource.signInWithApple(token)`
exchanges via GitLive `OAuthProvider.credential(providerId="apple.com", idToken=, rawNonce=)` →
`FirebaseAuthRepository.signInWithApple()` → `finishSignIn(uid)`. The `apple.com` provider string and
the raw-nonce contract are the only Apple-specific bits.

**Android is still the stub** (`AppleAuthClient.android` → `AuthError.AppleSignIn.NotYetAvailable`,
rendered as the "coming soon" info notice). Reason: GitLive firebase-auth 2.1.0 wraps no
web/redirect OAuth (`signInWithProvider`), so Android Apple login needs the native Firebase SDK
redirect (`startActivityForSignInWithProvider`) + an Apple Service-ID web config. The ViewModel
already branches NotYetAvailable→notice vs. real-result→sign-in, so flipping Android on later is
contained to `AppleAuthClient.android` (+ Apple Service ID/key + Firebase OAuth redirect).

**Manual prerequisites for the iOS flow to actually authenticate** (code is done; these are not):
1. Firebase console → Authentication → Sign-in method → **enable Apple**.
2. Apple Developer → App ID `es.schsebastian.foodrats` → enable **Sign in with Apple** capability;
   regenerate the distribution profile (Xcode automatic signing on archive, or `match appstore --force`).
   The `com.apple.developer.applesignin` entitlement is already in `iosApp.entitlements`.

`AppleSignInToken` (in `data/apple/`) carries `identityToken`/`nonce`/`authorizationCode`/`email`/`fullName`.
Session notes: `docs/session/2026-06-18-apple-login-structure/PROGRESS.md`.

## Test

`./gradlew :feature:auth:testAndroidHostTest` — runs `commonTest` (use cases, error mapper exhaustiveness) on the Android host.
