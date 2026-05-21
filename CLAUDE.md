# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

FoodRats is a closed-group meal-sharing KMP app (Android + iOS). The codebase implements the DDD/Clean Architecture design in `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` (~1400 lines, authoritative). When the spec and code disagree, the spec wins until explicitly revised. The MVP scaffold and all six feature plans (`auth`, `crew`, `meal`, `feed`, `stats`, `notifications`) are implemented and on `main`; supplementary plans live in the gitignored `docs/superpowers/plans/`.

## Build, run, test

JVM is auto-provisioned (Amazon Corretto 21 via foojay — see `gradle/gradle-daemon-jvm.properties`); first build downloads it. Configuration cache + build cache on by default.

| Task | Command |
|---|---|
| Build & install on connected Android | `./gradlew :androidApp:installDebug` then `adb shell am start -n es.schsebastian.foodrats/.MainActivity` |
| Build Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Build & install design-system catalog | `./gradlew :catalogApp:installDebug` then `adb shell am start -n es.schsebastian.foodrats.catalog/es.schsebastian.foodrats.catalog.CatalogActivity` |
| All Android host tests | `./gradlew :core:domain:testAndroidHostTest :feature:auth:testAndroidHostTest :feature:crew:testAndroidHostTest :feature:meal:testAndroidHostTest :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest :feature:notifications:testAndroidHostTest :core:designsystem:testAndroidHostTest` |
| Single test | `./gradlew :feature:meal:testAndroidHostTest --tests "*PublishMealUseCaseTest.publishes_when_draft_day_is_today"` (use `*` wildcards) |
| iOS framework (sim) | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — **currently fails**, see "iOS status" below |
| iOS simulator tests | `./gradlew :<module>:iosSimulatorArm64Test` — fails to link for any module that transitively depends on Firebase (no `FirebaseCore.framework` locally; needs SPM setup in Xcode) |

There is no configured linter/formatter (no ktlint/detekt/editorconfig). Don't introduce one without asking.

Lifecycle nuance: `commonTest` sources are exercised through each target's test task (`testAndroidHostTest`, `iosSimulatorArm64Test`), not via a separate `commonTest` task — there is no `:<module>:test`.

## Module graph

```
shared/        Compose root + NavGraph + Koin aggregator only (no business logic)
androidApp/    Application bootstrap, MainActivity, FCM service, manifest
catalogApp/    Standalone design-system catalog (separate APK, no Firebase/Koin/features)
iosApp/        Xcode project + Swift glue (AppDelegate, iOSApp, GoogleSignInBridge)

core/
  domain/         Result<T,E>, Clock, DispatcherProvider, CrashReporter, shared VOs
                  (AccountId, CrewId, MealDay, Score, …), MealReadPort, ActiveCrewProvider
  data/           DataStore factory (expect/actual), Firebase initializer, AppPreferences
  designsystem/   Fr* atoms/molecules/templates + theme + tokens (Atomic Design)
  presentation/   MviViewModel base + ErrorToStringMapper
  i18n/           StringKey sealed interface + resolve() Composable + en/es common strings

feature/
  auth/           Google Sign-In via Credential Manager (Android) + GoogleSignIn-iOS bridge
  crew/           Create/join/leave crew, invite codes, member list, active-crew picker
  meal/           ImagePickerKMP (launcher-style native camera + gallery) → compose → publish (the rich-domain exemplar)
  feed/           Day window of meals for the active crew (reads via MealReadPort)
  stats/          Client-side streaks/leaderboards over last 30 days (reads MealReadPort)
  notifications/  Permission rationale, FCM token registration, streak-nudge WorkManager job
```

`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` is on — refer to projects as `projects.core.domain`, never `project(":core:domain")`. New modules must be registered in `settings.gradle.kts`.

## Architectural rules (easy to violate by reading code alone)

- **Custom `Result<T, E>` in `:core:domain`, not stdlib `Result<T>`.** Stdlib `Result` carries `Throwable`; the domain `Result` carries a typed feature error so the UI layer can `when`-exhaust it. (spec §5.6)
- **Errors are `sealed interface` with nested `sealed interface` groups and `data object` leaves** (e.g. `MealError.Publish.AlreadyPostedToday`). Not enums — `data object` keeps the door open to attaching payloads later. No `Unknown` cases unless genuinely justified. (§5.7) This now applies uniformly to `:core:domain` errors too — `IdError`, `MealValueObjectError`, `MealReadError`, and `SessionError` are all sealed interfaces. If you add a new error type anywhere, follow the same shape.
- **Dispatcher boundary lives in repositories only.** Exactly one `withContext(dispatchers.io) { ... }` per public data-layer method; zero `withContext` in use cases or ViewModels. `DispatcherProvider` is a concrete class in `commonMain` that obtains its IO dispatcher via an `internal expect fun platformIoDispatcher()`; on Android it's `Dispatchers.IO`, on iOS/Native it aliases to `Dispatchers.Default` (Native makes `Dispatchers.IO` `internal` in coroutines 1.10.x). (§6)
- **Features can't depend on other features.** Cross-context reads go through ports declared in `:core:domain` (e.g. `MealReadPort` consumed by `:feature:feed` and `:feature:stats`; `ActiveCrewProvider` consumed by everyone needing the current crew). Two acknowledged violations on `main` need post-MVP refactor: `feature:auth → feature:notifications` (token registration after sign-in) and `feature:meal → feature:notifications` (streak nudge after publish). Move these to ports in `:core:domain` when touching the area. (§2.3, §3.1)
- **`:core:domain` allows only `kotlin.stdlib`, `kotlinx-datetime`, `kotlinx-coroutines-core`.** No Firebase, no Android, no Compose. Enforced by `KonsistRulesTest` in `core/domain/src/androidHostTest/`.
- **MVI base lives in `:core:presentation`** (~80 LOC). `handle(intent)` is intentionally impure (suspends, calls use cases). Test against state-flow emissions via Turbine. (§4.5)
- **MVI single source of truth.** State lives only in `MviViewModel`'s `State`. Do not keep parallel `MutableStateFlow<X>` for fields that are also in `State` — that was the original `FeedViewModel.day` bug. Feed downstream use cases via `state.map { it.x }.filterNotNull().distinctUntilChanged()`. `FeedViewModel` is the reference pattern. Updates happen only through the `update { it.copy(...) }` reducer; read with `currentState`.
- **Design-system composables prefixed `Fr*`.** `:core:designsystem` atoms/molecules never import domain types — they take primitives or presentation enums. Domain-aware components (`FrMealCard`, `FrFeedMealCard`, `FrCrewMemberRow`) live in the owning feature's `presentation/components/`. (§4.1, §4.3)
- **Every public `Fr*` composable and every foundation token group ships with a `:catalogApp` entry.** The catalog is the design-review surface — if a component isn't in it, designers can't see it. Add a `CatalogEntry` to one of the four story files (`stories/FoundationStories.kt`, `AtomStories.kt`, `MoleculeStories.kt`, `TemplateStories.kt`) under the matching `CatalogGroup`; use `CatalogScene` / `CatalogSceneSplit` to render scenarios with a label. IDs are `<group>.<name>` in lowercase (e.g. `atom.button`). The contract is convention-enforced today; a Konsist rule that asserts every public `Fr*` composable has an entry is the eventual hardening.
- **Beyond `MaterialTheme.colorScheme`, role-named meaning colors live in `FrSemanticColors`** (`success`, `warning`, `danger`, `info`, `celebration`, `streakHot` — each with an `on…` pair). Access via `LocalFrSemanticColors.current` from anywhere inside `FoodRatsTheme`. Use these for meaning — don't alias Material brand roles for it (e.g. don't paint a warning banner with `MaterialTheme.colorScheme.secondary` just because both are yellow). Don't reach for raw `Color(0x…)` either.
- **All user-visible text — including error messages — flows through `resolve(StringKey)`.** Each feature defines its own `<Feature>StringKey` enum implementing the sealed `StringKey` interface. Per-feature `<Feature>Error.toStringKey()` mapper is an exhaustive `when` over the error tree, with a matching `*ErrorToStringKeyTest` in `commonTest` to lock exhaustiveness. No hardcoded strings outside `composeResources/`. **Includes punctuation and glyph separators** — `★`, `•`, parentheses around counts, etc. See `FeedStringKey.RatingSummary` / `FeedStringKey.VoterScore` / `MealStringKey.TagSeparator` / `StatsStringKey.PostCount` for the pattern. (§7)
- **Firebase via GitLive KMP bindings** (`dev.gitlive:firebase-*`). Domain layer must not import Firebase types — DTOs and error mapping live in each feature's `data/firebase/`. Plan is to replace Firebase with an owned server without touching domain code. (§1, §8.4)
- **Ubiquitous language enforced in review.** Use `Meal` (never Post/Entry), `Plate` (never Photo for the composed artifact), `Score` (never rating/stars), `Crew` (never group/team), `Member` (a Crew membership, distinct from `Account`). (§2.2)

## Build conventions worth knowing

- **`@JvmInline` is required on every `value class` in commonMain** (e.g. `AccountId`, `CrewId`, `MealId`, `Score`, `DishName`). Without it, JVM/Android compilation fails — value classes need the annotation even though Native ignores it. Always `import kotlin.jvm.JvmInline`.
- **Firebase-touching modules target `JvmTarget.JVM_17`**, not 11. `firebase-bom 33.5.1` ships inline functions compiled at JVM 17; inlining them into a JVM 11 target is rejected. Currently: `core:data`, `feature:auth`, `feature:crew`, `feature:meal`, `feature:notifications`. Modules without Firebase (`core:domain`, `core:i18n`, `core:designsystem`, `core:presentation`, `feature:feed`, `feature:stats`, `shared`) stay on JVM 11.
- **Firebase BOM must be added to `androidMain.dependencies`** in every Firebase-touching module: `implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.5.1"))`. The GitLive KMP wrappers pull in `com.google.firebase:*` artifacts transitively on Android without pinned versions — the BOM pins them.
- **Material Icons on iOS:** `material-icons-extended` has no iOS publication in CMP 1.11.0. Use `material-icons-core` only (`materialIconsCore` alias in the catalog). `FrIcons` currently uses neutral core-icon placeholders where camera-specific icons were intended — improve once a compatible icons artifact ships or vendor SVGs into `composeResources/`. **ImagePickerKMP caveat:** `io.github.ismoy:imagepickerkmp:1.0.41` declares `material-icons-extended` as a `commonMain` dependency. If the iOS build breaks with an unresolved `material-icons-extended` artifact, add `exclude(group = "org.jetbrains.compose.material", module = "material-icons-extended")` to the `imagepickerkmp` library declaration in `feature/meal/build.gradle.kts`.
- **Compose Multiplatform Navigation typed routes:** `Route` is a `sealed interface` of `@Serializable data object`s; NavGraph uses `composable<Route.X> { … }`. `popUpTo` with the typed API takes a `KClass` (`popUpTo<Route.SignIn>`), not the Int id returned by `findStartDestination`.

## Tests

- **Cross-platform tests live in `commonTest/`.** They run on every test target (`testAndroidHostTest`, `iosSimulatorArm64Test`) via the per-target task. Use `kotlin.test`, Turbine, `kotlinx-coroutines-test`.
- **JVM-only tests live in `androidHostTest/`.** Konsist (architecture enforcement) and Compose UI tests both require JVM and live here. To enable `androidHostTest` on a new module: add `withHostTest { isIncludeAndroidResources = true }` inside the `androidLibrary { }` block, then a `val androidHostTest by getting { dependencies { … } }` source-set block with `kotlin-test-junit`, `junit`, etc. See `core/domain/build.gradle.kts` and `core/designsystem/build.gradle.kts`.
- **Compose UI behavior tests:** use `androidx.compose.ui.test.junit4.v2.createComposeRule` (the `v2` package — `StandardTestDispatcher`-backed). Robolectric 4.15.1 provides the JVM Android runtime. `core/designsystem/src/androidHostTest/resources/robolectric.properties` sets `sdk=33` + display qualifiers (qualifiers are required for Row-based hit-testing to work — without them, all children collapse to the same coordinate). Pattern: `core/designsystem/src/androidHostTest/kotlin/.../{atoms,molecules}/*Test.kt`.
- **`MviViewModelTest` pattern with `UnconfinedTestDispatcher`** coalesces state emissions: tests should `expectMostRecentItem()` rather than `awaitItem()` a transient intermediate, otherwise they race.
- The Konsist rule in `core/domain/src/androidHostTest/kotlin/.../KonsistRulesTest.kt` enforces the no-Firebase/no-Android/no-Compose import rule for `:core:domain`. Run it whenever you touch domain.

## Repository conventions

- **`docs/superpowers/` is gitignored scratch space** (skill-generated plans/specs). It exists locally but is not committed. Final, reviewable specs go to `docs/specs/` and are committed. The seven implementation plans for the MVP all live under `docs/superpowers/plans/`.
- **`androidApp/google-services.json` is gitignored.** What's currently on disk is a placeholder synthesized for local builds — it lets `:androidApp:assembleDebug`/`installDebug` succeed and the app launches to SignIn, but Firebase Auth will fail past the credential picker. Real builds require: a real `google-services.json` from a Firebase project with applicationId `es.schsebastian.foodrats`, plus `googleServerClientId=<web-client-id>` in `~/.gradle/gradle.properties` (NOT the repo). See `androidApp/google-services.json.template`.
- **`local.properties` is gitignored.**
- Android SDK versions are pulled from `gradle/libs.versions.toml` (`android-compileSdk=36`, `android-minSdk=30`, `android-targetSdk=36`). Update there, not in module Gradle files.
- iOS framework `baseName = "FoodRatsShared"`. Swift imports it as `import FoodRatsShared`.
- Security Rules live at the repo root: `firestore.rules`, `storage.rules`, plus `firebase.json` and `.firebaserc` (project alias `default = foodrats-de4ec`, storage target `default → foodrats-de4ec.firebasestorage.app`). Deploy both: `pnpm dlx firebase-tools deploy --only firestore:rules,storage --project foodrats-de4ec`. Note: it's `--only storage` (not `storage:rules`) — the CLI treats the suffix after `:` as a deploy-target alias, not a sub-resource. Login is interactive (`pnpm dlx firebase-tools login`); no CI hookup.
- **Use `pnpm` instead of `npm`/`npx`.** For ad-hoc tool invocations use `pnpm dlx <pkg>` instead of `npx <pkg>` (e.g. `pnpm dlx firebase-tools …`). `pnpm` is installed via Homebrew. `npm` itself stays on disk because it's bundled with Homebrew's `node` — it can't be removed without uninstalling `node` — but we don't use it.

## Recent decisions (2026-05-19 → 2026-05-20)

These are the recent shifts in conventions or implementation that you should carry forward when touching the area. Each entry: **what** changed, **why** it changed, **how** to apply it.

### Design system v3 — Iron & Ember refresh (2026-05-20)

- **What.** Palette in `core/designsystem/theme/Colors.kt` swapped from the "healthy" Avocado + Citrus + Berry set to **Iron & Ember**: deep-olive primary `#4F6E2B` (light) / moss `#A8BC85` (dark), ember-copper secondary `#B0561E` / warm-ember `#E6A47B`, rust tertiary `#7A3826` / clay `#C58D80`. Surfaces moved to **concrete** `#E8E6DE` in light and **charcoal-olive** `#1B1C19` in dark — both intentionally low-saturation. `FrSemanticColors` retuned to match: success = moss, celebration = ember copper, streakHot = forge orange `#D45A14`, danger = deep crimson `#8E2A2A`, info = steel blue. Typography, radius, elevation, and Sizes unchanged from v2.
- **Why.** The previous palette read too "cute" for the closed-group meal-sharing positioning the team wanted — pale-green containers, neon yellow-citrus, and berry pink all leaned cartoonish. Iron & Ember keeps food-meaning intact (olive ≈ herbs, ember ≈ heat/smoked, rust ≈ braise) but lands rugged and earthy. Dark theme stops being "forest night" green-tinted and becomes a true smoky charcoal.
- **How.** All atoms still compose against `MaterialTheme.colorScheme` / `typography` / `shapes`, so the swap propagates without touching feature code. The bottom bar + top app bar use `primaryContainer` (now muted moss `#9CB47A` in light), so they read as deeper / less pastel than before — no per-screen edits needed. Continue to use `LocalFrSemanticColors.current` for meaning roles. The `docs/specs/2026-05-19-healthy-design-system-design.md` spec is now historical; the palette numbers in `Colors.kt`/`SemanticColors.kt` are authoritative until a new spec lands.

### Firebase Crashlytics wired per-platform (2026-05-20)

- **What.** `CrashReporter` (`:core:domain`) is no longer bound to `NoopCrashReporter` in the shared `coreDataModule`. It's now bound **per platform**: Android → `AndroidCrashReporter` (`:core:data` androidMain, wraps `FirebaseCrashlytics.getInstance()`) registered in `FoodRatsApplication.androidCrashModule()`; iOS → `IosCrashReporter` (`:core:data` iosMain) registered via `crashIosModule(recordNonFatal, log)` in `MainViewController`, bridged to `CrashlyticsBridge.swift`. Collection is disabled in debug on both platforms (`!BuildConfig.DEBUG` / `#if DEBUG`). Gradle: `firebaseCrashlytics` plugin applied in `androidApp`, `com.google.firebase:firebase-crashlytics` added to `androidApp` + `:core:data` androidMain (pinned by the existing BOM).
- **Why.** Crashlytics has no GitLive KMP binding, so a single common implementation isn't possible — the native SDKs must be reached from each platform. Removing the common `NoopCrashReporter` binding (rather than relying on Koin override order) keeps exactly one `single<CrashReporter>` per graph, which is deterministic. `NoopCrashReporter` stays in `:core:domain` for tests (`MealErrorMapperRateTest` uses it directly).
- **How.** If you need a new telemetry call, add it to the `CrashReporter` interface and implement it in **both** `AndroidCrashReporter` and `IosCrashReporter` (the iOS one forwards through a Swift lambda — add the matching method to `CrashlyticsBridge.swift` and thread it through `MainViewController` + `ContentView.swift`, mirroring the GoogleSignIn bridge). iOS still requires the two manual Xcode steps documented under "iOS status".

### Domain errors uniformly sealed (commit `fbf5e40`)

- **What.** `IdError`, `MealValueObjectError`, `MealReadError`, and `SessionError` in `:core:domain` converted from `enum class` to `sealed interface` with `data object` leaves.
- **Why.** Feature-level errors already followed this shape; the four `:core:domain` ones were the last enums, blocking future payload attachment and breaking the "all errors look the same" rule. The audit caught it.
- **How.** Caller code is unchanged — `X.Member` access still works, `when` exhaustiveness still holds (it does for sealed interfaces with data-object leaves the same way it did for enums). If you introduce a new error type anywhere, **always** use the sealed-interface + data-object pattern, never an enum.

### FeedViewModel — true MVI single source of truth (commit `fbf5e40`)

- **What.** `FeedViewModel` no longer holds a parallel `MutableStateFlow<FeedDay> day` alongside `FeedState.day`. The day field lives only in state; `ObserveFeedUseCase` is fed by `state.map { it.day }.filterNotNull().distinctUntilChanged()`. `navigatePrev` / `navigateNext` read `currentState.day` and mutate via `update { it.copy(day = candidate, …) }`.
- **Why.** The old pattern wrote to `day.value` *and* `state.day` in two places, which is the classic "two sources of truth" bug — easy for them to drift, and impossible to test against a single state stream.
- **How.** Treat `FeedViewModel` as the reference. When a ViewModel needs to feed a flow into a use case, derive that flow from `state` (using `map` + `filterNotNull` + `distinctUntilChanged` as needed) instead of keeping a separate `MutableStateFlow`. Never reach for `MutableStateFlow` in a ViewModel — `MviViewModel` already owns the only one. Use `currentState` to read synchronously.

### i18n covers separator glyphs (commit `fbf5e40`)

- **What.** The hardcoded `★`, `•`, and `(N)` separators in `FrFeedMealCard`, `FrMealCard`, and `FrLeaderboardRow` are now resource-keyed via `FeedStringKey.RatingSummary` (`"%1$s ★ · %2$d"`), `FeedStringKey.VoterScore` (`"%1$s: %2$d ★"`), `MealStringKey.TagSeparator` (`" • "`), and `StatsStringKey.PostCount` (`"(%1$d)"`). Both `values/strings.xml` and `values-es/strings.xml` populated.
- **Why.** "All user-visible text via `resolve(StringKey)`" includes glyph punctuation. Locales may render `·`, `•`, parentheses differently (RTL layouts especially), and templating the full formatted string keeps assembly out of Kotlin.
- **How.** When adding a row that combines name + value + separator, define a single parameterized string (`"%1$s · %2$d"`) rather than concatenating in Kotlin. Resource separator keys like `MealStringKey.TagSeparator` are fine for `joinToString` cases where the call site genuinely needs the raw glyph.

## iOS status

- `:shared:linkDebugFrameworkIosSimulatorArm64` **should link** now that nav-compose is on stable `2.9.2` (was `2.8.0-alpha10`, which produced a Native cache-build error with Kotlin 2.3.21). `kotlin.native.cacheKind=none` workarounds were removed from `gradle.properties`.
- iOS Google Sign-In is now wired. `feature/auth/iosMain/.../GoogleAuthClient.ios.kt` takes `(viewControllerProvider, signIn, signOut)` lambdas; the Swift caller in `iosApp/iosApp/ContentView.swift` delegates to the existing `GoogleSignInBridge` static methods. Koin binding lives in `feature/auth/iosMain/.../di/AuthIosModule.kt`. `MainViewController()` in `shared/iosMain/` now accepts the lambdas from Swift and installs `authIosModule(...)` alongside `notificationsIosModule`.
- iOS Firebase initialisation is now enabled: `iosApp/iosApp/iOSApp.swift` calls `FirebaseApp.configure()` in `init()` and `AppDelegate.swift` enables `Messaging.delegate`, APNS registration, and the `GIDSignIn.handle(url:)` callback.
- iOS test linking (any module that touches Firebase) still fails with `ld: framework 'FirebaseCore' not found` — Firebase native frameworks are SPM-resolved inside Xcode, not visible to Gradle.
- **iOS: link `CoreLocation.framework` manually in Xcode.** ImagePickerKMP 1.0.41 references `CLLocation` symbols unconditionally in its iOS EXIF extractor (no opt-out). After the next clean iOS build, if the linker reports `_OBJC_CLASS_$_CLLocation`, open `iosApp/iosApp.xcodeproj` → target `iosApp` → Build Phases → "Link Binary With Libraries" → `+` → add `CoreLocation.framework`. There is no Gradle-side fix.
- **iOS Crashlytics needs two manual Xcode steps (no Gradle equivalent).** The Kotlin/Swift glue is in place (`IosCrashReporter` + `crashIosModule` in `:core:data` iosMain, bridged from `iosApp/iosApp/CrashlyticsBridge.swift` via lambdas passed through `MainViewController`), and `iOSApp.swift` toggles collection off in `#if DEBUG`. But FirebaseCrashlytics is SPM-resolved inside Xcode, so you must: **(1)** add the **FirebaseCrashlytics** product to target `iosApp` → General → "Frameworks, Libraries, and Embedded Content" (it ships in the same `firebase-ios-sdk` SPM package already used for Core/Auth/Messaging — just check the additional product); and **(2)** add a Run Script build phase **after** "Compile Sources" / "Embed Frameworks" that runs the Crashlytics dSYM upload — `"${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run"` with Input Files `${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}/Contents/Resources/DWARF/${TARGET_NAME}` and `$(SRCROOT)/$(BUILT_PRODUCTS_DIR)/$(INFOPLIST_PATH)`. Without step 1 the Swift bridge won't compile; without step 2 crash reports arrive unsymbolicated.

## Active tech debt (carry forward when touching the area)

- **Dev-crew hardcoding:** `feature/auth/.../FirebaseAuthRepository.signInWithGoogle()` stamps `Session.activeCrewId = CrewId("test-crew-1")` so meal publishing has a non-null crew. There's a `TODO(scope = "feature:crew")` — remove when the Crew picker becomes the primary post-signin destination.
- **Cross-feature dep violation:** see Architecture rules above.
- **`material-icons-core` placeholders** for camera/gallery/no-camera icons in `FrIcons` (Build/Add/List/Warning substitute for Camera/AddAPhoto/GalleryImport/CameraOff). Bottom-nav now uses proper `FrIcons.Home` and `FrIcons.Stats` (Home / Star core icons) rather than the previous `GalleryImport` placeholder. Camera capture and gallery picking are now available on both Android and iOS via ImagePickerKMP's native modal launcher (previously Peekaboo, an inline viewfinder); the icon placeholders remain only because `material-icons-extended` has no iOS publication — not as a camera-functionality workaround.
- **Coil 3 image loading is wired.** `FrFeedMealCard` uses `coil3.compose.AsyncImage`. The singleton `ImageLoader` is installed by `installFeedImageLoader()` in both `FoodRatsApplication.onCreate()` and `MainViewController()` (iOS), using `KtorNetworkFetcherFactory` over the per-platform Ktor engine (OkHttp on Android, Darwin on iOS).
- **i18n for WorkManager:** `PublishMealViewModel` now resolves streak nudge title/body via `getString(NotificationStringKey.Streak{Title,Body}.resourceId)` (suspending Compose Resources API); the call is wrapped in try/catch so unit tests without bundled resources stay green.
- **iOS: link `CoreLocation.framework` manually in Xcode.** ImagePickerKMP 1.0.41 references `CLLocation` symbols unconditionally in its iOS EXIF extractor (no opt-out). After the next clean iOS build, if the linker reports `_OBJC_CLASS_$_CLLocation`, open `iosApp/iosApp.xcodeproj` → target `iosApp` → Build Phases → "Link Binary With Libraries" → `+` → add `CoreLocation.framework`. There is no Gradle-side fix.
- **Member-cache drift (Profile split):** `UpdateMyDisplayNameUseCase` / `UpdateMyAvatarUseCase` (in `:feature:auth`) do a two-port non-atomic dual write: canonical `accounts/{uid}` first (required), then a best-effort `crews/{activeCrewId}.members.{uid}` cache write via `CrewMemberCacheWritePort`. If the cache write fails after the canonical write succeeds, the denormalized member entry drifts until the next successful write. Telemetry: `CrashReporter.log("member cache drift: op=... crewId=... accountId=...")`. Future fix: migrate the members list to live reads via `AccountReadPort` (same pattern as comments) and drop the denormalized fields. See `docs/specs/2026-05-21-your-profile-split-design.md` §4.3.
- **Dead code in `CrewFirestoreDataSource`:** after the Profile split, `renameMember`, `updateAccountAvatarUrl`, and `CrewMemberWriter.renameAndPropagate` have no callers — kept in place to keep the cleanup diff focused. Safe to delete in a follow-up. `CrewMemberWriter` itself can go with them.
- **Remove-member button (not yet built):** the Profile-split spec calls for an owner-only per-row "Remove" button on the CrewSettings members list, wired to a `RemoveMemberUseCase` stub that surfaces "coming soon". Deferred — was not landed in the Profile-split branch. Real implementation will need: data-layer write, Firestore security rule (owner-only, can't remove self), confirmation dialog, and a member-removed notification. Spec §6.3 carries the design.
- **`CrewSettingsViewModelTest` deleted (Profile split):** the old test exhaustively covered the my-profile rename/avatar/sign-out flows that moved to `ProfileViewModel`. Rather than rewrite around the new (smaller) VM in the same diff, the file was removed. Reinstate when re-covering crew-name save / leave / delete / Switch-crew.
