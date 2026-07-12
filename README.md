# FoodRats

**A closed-group daily meal-sharing app for Android and iOS.** Built with Kotlin Multiplatform and Compose Multiplatform, sharing one codebase across both platforms.

FoodRats is for small circles — 3 to 8 friends, family, or colleagues — who want to share what they eat each day without the diet-app baggage. Each member posts **one photo per day** of a meal, adds a short description and a 1–10 score, and the crew sees a shared feed plus light group stats (streaks, leaderboards, most-eaten dishes). The tone is social and lightly competitive, and explicitly **anti-calorie-tracking**: it's about sharing food with people you like, not counting macros.

---

## What it does

- **Crews** — Create a private crew and invite 3–8 people with a join code. Membership is closed; there's no public discovery.
- **One meal a day** — Capture a photo with the native camera (or pick from the gallery), compose a *Plate* with a description and a 1–10 *Score*, and publish. The "one meal per day per member" rule is a domain invariant.
- **Meal AI (in progress)** — On-device ingredient/dish classification from the meal photo using MediaPipe + a bundled TensorFlow Lite Food-101 model, so the app can suggest what's on the plate.
- **Feed** — Scroll your crew's meals day by day.
- **Stats** — Client-side streaks and leaderboards over a rolling 30-day window.
- **Notifications** — Push (FCM) plus local streak-nudge reminders so the crew keeps its streak alive.

## Tech stack

| Area | Choice |
|---|---|
| Language / platforms | Kotlin Multiplatform → Android + iOS |
| UI | Compose Multiplatform (shared UI), SwiftUI glue on iOS |
| Architecture | Domain-Driven Design + Clean Architecture, roll-your-own MVI |
| DI | Koin |
| Navigation | Jetpack Navigation Compose (type-safe routes) |
| Backend (MVP) | Firebase — Auth, Firestore, Storage, Messaging, Crashlytics — via [GitLive](https://github.com/GitLiveApp/firebase-kotlin-sdk) KMP bindings |
| Push backend | Cloud Functions (TypeScript, `europe-west3`) |
| Images | Coil 3 (loading), in-house native camera + system photo picker (`rememberPhotoPicker`, `:core:presentation`) |
| On-device AI | MediaPipe Tasks Vision + TensorFlow Lite (Food-101) |
| Design system | Atomic Design (`Fr*` components), standalone catalog app |

The data layer is deliberately isolated behind ports so the Firebase MVP backend can later be swapped for an owned server **without touching the domain**.

## Architecture

The codebase follows the DDD + Clean Architecture design documented in [`docs/specs/2026-05-16-foodrats-ddd-kmp-design.md`](docs/specs/2026-05-16-foodrats-ddd-kmp-design.md), the authoritative spec.

Key principles:

- **Bounded contexts** — Identity, Crew, Meal, Feed, Stats, Notifications. Each owns its ubiquitous language (`Meal` never "Post", `Plate` for the composed artifact, `Score` for the 1–10 rating, `Crew` for the group).
- **Features never depend on other features.** Cross-context reads go through ports declared in `:core:domain` (e.g. `MealReadPort`, consumed by Feed and Stats).
- **Typed `Result<T, E>`** with sealed-interface errors — no exceptions for domain failures, so the UI can exhaustively handle every error.
- **One I/O boundary per repository method** — the dispatcher boundary lives only in the data layer.
- **All user-visible text via i18n** — every string flows through `resolve(StringKey)`.

### Module graph

```
shared/        Compose root + NavGraph + Koin aggregator (no business logic)
androidApp/    Application bootstrap, MainActivity, FCM service
catalogApp/    Standalone design-system catalog (separate APK)
iosApp/        Xcode project + Swift glue

core/
  domain/         Result<T,E>, value objects, ports — no Firebase/Android/Compose
  data/           DataStore, Firebase init, preferences
  designsystem/   Fr* atoms/molecules/templates + theme (Atomic Design)
  presentation/   MVI base + error mapping
  i18n/           StringKey + resolve() + en/es strings

feature/
  auth/           Google Sign-In → Firebase Auth → Session
  crew/           Create/join/leave crew, invite codes, members
  meal/           Camera → compose → publish (the rich-domain exemplar)
  feed/           Day window of the active crew's meals
  stats/          Streaks & leaderboards
  notifications/  Permission rationale, FCM, streak-nudge job
```

## Build & run

The JDK is auto-provisioned (Amazon Corretto 21 via foojay) on first build.

```sh
# Build & install the Android debug app on a connected device
./gradlew :androidApp:installDebug
adb shell am start -n es.schsebastian.foodrats/.MainActivity

# Build the Android debug APK
./gradlew :androidApp:assembleDebug

# Design-system catalog (separate APK)
./gradlew :catalogApp:installDebug

# iOS: open iosApp/ in Xcode and run, or link the framework:
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

> **Note:** Real Firebase builds need a `google-services.json` from a project with applicationId `es.schsebastian.foodrats` plus `googleServerClientId` in `~/.gradle/gradle.properties`. The checked-in placeholder lets the app build and launch to the sign-in screen. See `androidApp/google-services.json.template`.

## Tests

```sh
# All Android host tests
./gradlew :core:domain:testAndroidHostTest :feature:auth:testAndroidHostTest \
  :feature:crew:testAndroidHostTest :feature:meal:testAndroidHostTest \
  :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest \
  :feature:notifications:testAndroidHostTest :core:designsystem:testAndroidHostTest

# A single test (use * wildcards)
./gradlew :feature:meal:testAndroidHostTest --tests "*PublishMealUseCaseTest*"
```

Cross-platform tests live in `commonTest/` and run on every target; JVM-only tests (Konsist architecture rules, Compose UI tests) live in `androidHostTest/`.

## CI/CD

GitHub Actions + Fastlane, zero paid infra: Android builds on free Linux runners, iOS on a self-hosted Mac runner. Every merge to `main` ships to Play Internal + TestFlight; production releases are triggered by pushing a SemVer tag and gated behind a protected GitHub Environment. See [`docs/cicd-runbook.md`](docs/cicd-runbook.md).

## License

[MIT](LICENSE) © chsumiapps.com
