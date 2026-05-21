# :feature:auth

Google Sign-In bounded context. Credential Manager on Android, `GoogleSignInBridge` (Swift) on iOS, Firebase Auth exchange behind it. The canonical exemplar for the per-feature DDD layout.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §9 (Exemplar A — Auth, the full feature walkthrough).
- Root `CLAUDE.md` — "Module graph", "iOS status" (the iOS Google Sign-In wiring via lambdas), "Active tech debt" (dev-crew hardcoding in `FirebaseAuthRepository`).
- Status — feature done and on `main`; the iOS lambda bridge is wired in `MainViewController` + `ContentView.swift`.

## Local rules

- `GoogleAuthClient` is the platform expect — Android calls Credential Manager, iOS receives `(viewControllerProvider, signIn, signOut)` lambdas from Swift via `authIosModule(...)`. Don't import Firebase types in domain.
- `FirebaseAuthRepository.signInWithGoogle()` currently stamps `Session.activeCrewId = CrewId("test-crew-1")`. Remove when the Crew picker is wired as the post-signin destination (carries `TODO(scope = "feature:crew")`).
- JVM target **17** (Firebase BOM).

## Test

`./gradlew :feature:auth:testAndroidHostTest` — runs `commonTest` (use cases, error mapper exhaustiveness) on the Android host.
