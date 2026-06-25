# :core:data

Cross-cutting data infrastructure shared by every feature: DataStore factory (`expect/actual`), Firebase initializer, `AppPreferences`, per-platform `CrashReporter` implementations (`AndroidCrashReporter`, `IosCrashReporter`). Feature-specific repositories live in each feature's own `data/` package, not here.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §8 (data layer), §8.4 (Firebase wiring), §8.6 (DataStore).
- Root `CLAUDE.md` — "Architectural rules" (one I/O boundary per public data-layer method, Firebase only in adapter layers, JVM 17 for Firebase-touching modules).
- Recent change — "Firebase Crashlytics wired per-platform" (2026-05-20).

## Local rules

- JVM target is **17** (Firebase BOM `33.5.1` inline functions). Stays in sync with `:androidApp`.
- `CrashReporter` is bound per platform: Android via `androidCrashModule()` in `FoodRatsApplication`, iOS via `crashIosModule()` in `MainViewController`. Do **not** rebind in `coreDataModule`.

## Test

`commonTest` runs on the JVM through `:core:data:testAndroidHostTest` (this is a KMP `androidLibrary` module with `withHostTest`, so the host-test task is `testAndroidHostTest`, NOT `testDebugUnitTest` — the latter does not exist here) and on iOS through `:core:data:iosSimulatorArm64Test` (currently fails to link without Xcode-resolved Firebase SPM frameworks).
