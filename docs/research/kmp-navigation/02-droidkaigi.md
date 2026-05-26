# DroidKaigi Conference App — KMP Navigation Review

**One-liner:** DroidKaigi 2024 runs on the JetBrains Compose-Multiplatform fork of `navigation-compose` (string routes + typed `@Serializable` destinations for arg screens) on Android, while iOS navigates *natively* with SwiftUI + TCA `NavigationStack` and only embeds select Compose screens as `UIViewController`s — and it ships **zero** deep links/universal links and **zero** auth-gated routing.

**Repo:** https://github.com/DroidKaigi/conference-app-2024 (also peeked at conference-app-2023)
**Approach:** `org.jetbrains.androidx.navigation:navigation-compose` `2.8.0-alpha09` on Compose Multiplatform `1.7.0-beta01` (2024). 2023 used Android-only `androidx.navigation:navigation-compose` `2.7.2`.

## Why it's AAA-grade
- Rebuilt every year by hundreds of contributors; production app on both stores. Multi-module Gradle (`core/*`, `feature/*`, `app-android`, `app-ios`, `app-ios-shared`), `build-logic` convention plugins, Roborazzi screenshot tests, detekt.
- Per-feature navigation contracts: each feature module owns its route constants + `NavGraphBuilder.xScreens()` + `NavController.navigateXScreen()` extensions in `commonMain`, so the app shell wires features without importing their internals (`feature/sessions/.../TimetableItemDetailScreen.kt:84`).

## Navigation approach & route definitions
**Hybrid route model** inside JetBrains `navigation-compose`:

- **Simple screens → string routes.** `core/.../NavHostWithSharedAxisX.kt` wraps `NavHost(startDestination: String)`. Routes are bare `const val`s:
  ```kotlin
  // feature/sessions/.../TimetableScreen.kt
  const val timetableScreenRoute = "timetable"          // :71
  fun NavController.navigateTimetableScreen() { navigate(timetableScreenRoute) }  // :90
  // feature/sessions/.../SearchScreen.kt
  const val searchScreenRoute = "search"                // :49
  // feature/main/.../MainScreen.kt
  const val mainScreenRoute = "main"                    // :80
  ```
  Registered via `composable(timetableScreenRoute) { … }` / `composable(searchScreenRoute) { … }`.

- **Arg-carrying screens → typed `@Serializable` destinations** (the *exact* mechanism FoodRats uses):
  ```kotlin
  // feature/sessions/.../navigation/TimetableItemDetailDestination.kt
  @Serializable
  data class TimetableItemDetailDestination(
      @SerialName("timetableItemId") val timetableItemId: String,
  )
  // feature/sessions/.../TimetableItemDetailScreen.kt
  composable<TimetableItemDetailDestination> { … }                    // :91
  fun NavController.navigateToTimetableItemDetailScreen(timetableItem: TimetableItem) {
      navigate(TimetableItemDetailDestination(timetableItem.id.value)) // :109
  }
  ```
  Note: it's a `data class` carrying a payload (FoodRats uses `data object` route singletons). DroidKaigi does **not** use a single sealed `Route` catalog — routes are decentralized per feature and the type-safe `composable<T>` API is reserved for screens with arguments.

- **Two-level graph.** A root `NavHost` (top-level screens: main, session detail, search, settings, sponsors, staff, contributors, favorites) + a **nested** `NavHost` (`mainNestedNavController`) for the 5 bottom-tab destinations. Tab↔route mapping is an app-supplied `MainNestedGraphStateHolder` (`app-android/.../KaigiApp.kt:264-290`):
  ```kotlin
  override fun routeToTab(route: String): MainScreenTab? = when (route) {
      timetableScreenRoute -> Timetable; eventMapScreenRoute -> EventMap; … else -> null }
  override fun onTabSelected(nav, tab) = when (tab) { Timetable -> nav.navigateTimetableScreen(); … }
  ```

- **Outbound links** (browser, native app, calendar, share) are *not* part of the nav graph — they go through a hand-rolled `ExternalNavController` (`KaigiApp.kt:305`, iOS version in `IosComposeKaigiApp.kt:394`). On Android it tries native-app intent → Custom Tab → toast; on iOS it calls `UIApplication.openURL`.

## Auth gating (public vs protected routes, redirect-to-login)
**None.** There is no login screen, no protected-route concept, no redirect-to-login, no conditional root destination. `startDestination` is unconditionally `mainScreenRoute` on both platforms (`KaigiApp.kt:130`, `IosComposeKaigiApp.kt:213`).

The only "auth" is **anonymous Firebase auth used purely to mint an API id-token** for the backend — never to gate UI:
```kotlin
// core/data/.../auth/DefaultAuthApi.kt
var idToken = authenticator.currentUser()?.idToken
if (userDataStore.isAuthenticated().first() != true || idToken == null) {
    idToken = authenticator.signInAnonymously()?.idToken.orEmpty()  // :25
}
// AndroidAuthenticator.kt -> Firebase.auth.signInAnonymously()
```
The `profilecard` feature is a local QR/profile editor; it has no account/login. **DroidKaigi is not a useful reference for auth-gated routing** — that capability simply isn't in the codebase.

## Deep links / universal links / app links (Android + iOS)
**None on either platform. Explicitly verified absent.**

- **Android manifest** (`app-android/src/main/AndroidManifest.xml`) has a single MAIN/LAUNCHER intent filter and nothing else — no `<data android:scheme/host>`, no `android.intent.action.VIEW`, no `android:autoVerify`:
  ```xml
  <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
  </activity>
  ```
- **No `navDeepLink { }` / `deepLinks =`** anywhere in the codebase (repo-wide grep returns zero hits).
- **iOS** `app-ios/App/App.entitlements` contains only app-sandbox + user-selected-file keys — **no `com.apple.developer.associated-domains`**, so universal links cannot work. `App.swift` is a plain `WindowGroup { RootView(...) }` with **no `.onOpenURL`/`.onContinueUserActivity`**, and `AppDelegate.swift` only calls `firebaseAppClient.prepareFirebase()`.

DroidKaigi never needs to open to a specific screen from a URL, so URL→destination parsing does not exist. If you need this pattern, DroidKaigi offers nothing to copy.

## Back stack & state restoration
- Back stack is the standard `navigation-compose` `NavHostController` stack; nav-icon clicks call `navController::popBackStack` (wired throughout `KaigiApp.kt:139-178`).
- Predictive back is enabled (`android:enableOnBackInvokedCallback="true"` in the manifest).
- Custom shared-axis-X transitions wrap every `NavHost` (`NavHostWithSharedAxisX.kt`) plus `SharedTransitionLayout` for shared-element animation between list and detail.
- **State restoration** relies on the library defaults + the `@Serializable` typed destinations (args survive process death via the SavedStateHandle the typed route deserializes into — see `TimetableItemDetailPresenter.kt:44` reading `timetableItemId` back out). No custom `rememberSaveable` nav-state plumbing.

## iOS / multiplatform wiring
This is the most important nuance for an "is this truly cross-platform nav?" question: **DroidKaigi 2024's iOS app does NOT use the shared Compose nav graph as its primary navigation.**

- **Primary iOS nav = SwiftUI + The Composable Architecture (TCA).** `app-ios/Sources/App/RootView.swift` builds per-tab `NavigationStack`s bound to TCA `StackState` paths:
  ```swift
  // RootReducer.swift
  public enum Path {
      @Reducer enum Timetable { case timetableDetail(TimetableDetailReducer); case search(SearchReducer) }
      @Reducer enum About { case staff…; case contributor…; case sponsor…; case acknowledgements }
  }
  public var paths = Paths(timetable: StackState(), favorite: StackState(), about: StackState())
  public var viewType: ViewType = .swiftUI    // toggle: .swiftUI vs .compose
  ```
  ```swift
  // RootView.swift  — native NavigationStack, push by appending to the TCA path
  NavigationStack(path: $store.scope(state: \.paths.timetable, action: \.paths.timetable)) {
      TimetableView(...)
  } destination: { store in
      switch store.case { case let .timetableDetail(store): TimetableDetailView(store: store)
                          case let .search(store): SearchView(store: store) } }
  ```
- **Compose Multiplatform is embedded, not driving.** Only some features render as Compose via `ComposeUIViewController` wrappers (`KmpProfileCardComposeViewControllerWrapper`, `KmpAppComposeViewControllerWrapper`), gated by `viewType == .compose`. The KMP entry `kaigiAppController(...)` lives in `app-ios-shared/.../IosComposeKaigiApp.kt:112` and returns a `UIViewController`.
- **The full Compose nav graph is duplicated, not shared.** `IosComposeKaigiApp.kt` re-declares `KaigiNavHost`, `mainScreen`, `KaigiAppMainNestedGraphStateHolder`, and an iOS `ExternalNavController` (Safari/EventKit) — a near-copy of `app-android/.../KaigiApp.kt`. The graph definition is **not** hoisted into `commonMain`; each platform's app module assembles it. (The per-feature `xScreens()` builders *are* in `commonMain`; only the top-level assembly is duplicated.)

Takeaway: DroidKaigi treats the iOS Compose path as secondary/experimental; the canonical iOS UX is native SwiftUI with TCA-owned navigation state. There is no single shared navigation graph that drives both platforms.

## Strengths
- Decentralized, per-feature nav contracts (`xScreens()` + `navigateX()` + route const) keep feature modules decoupled — a clean pattern to mimic regardless of the route-encoding choice.
- Typed `@Serializable` `composable<T>` for arg screens gives compile-time-safe args with kotlinx-serialization (validates that this API is production-viable on Compose Multiplatform).
- Nice separation of in-app nav (`NavController`) vs outbound/system nav (`ExternalNavController`).
- Polished transitions (shared-axis + shared-element) layered cleanly over the stock `NavHost`.

## Weaknesses
- **Bleeding-edge dependency:** pinned to `navigation-compose:2.8.0-alpha09` on Compose MP `1.7.0-beta01` — an alpha + beta combo (acceptable for a yearly-rebuilt app, riskier for a long-lived one).
- **No single shared nav graph:** the top-level graph is copy-pasted between `app-android` and `app-ios-shared`; drift is a real maintenance hazard.
- **Inconsistent route encoding:** string routes for some screens, typed `@Serializable` for others — no unified `Route` catalog.
- **iOS isn't actually Compose-navigated** end-to-end; it's SwiftUI/TCA with embedded Compose islands — so it doesn't demonstrate "one nav layer, two platforms."
- **Neither deep links nor auth gating exist** — the two capabilities FoodRats cares most about are entirely unaddressed.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)
- **Same library, validated.** DroidKaigi proves the JetBrains `navigation-compose` fork + typed `@Serializable` `composable<T>` routes works in a top-tier production KMP app. FoodRats already uses this exact stack, so there is **no migration** — it's confirmation to *stay put*, not a reason to switch.
- **Goal 1 (deep links / universal links / app links): NOT supported here.** DroidKaigi has none on either platform — no manifest VIEW filters, no `navDeepLink`, no iOS associated-domains, no URL→destination parsing. This repo gives FoodRats **zero** to copy for deep linking; we'll need another reference (e.g. a project that wires `navDeepLink {}` + `<intent-filter android:autoVerify>` on Android and `associated-domains` + `.onOpenURL`/`onContinueUserActivity` feeding the shared `NavController` on iOS).
- **Goal 2 (auth-gated routing): NOT supported here.** No login screen, no protected routes, no redirect-to-login; the only auth is anonymous Firebase token-minting. FoodRats' auth-required-vs-public route classification + redirect-to-sign-in has **no analog** in DroidKaigi.
- **Architecture caution:** DroidKaigi's "native SwiftUI/TCA on iOS, Compose embedded" model is the *opposite* of FoodRats' "single Compose Multiplatform UI on both platforms." Their duplicated-graph approach is not a model FoodRats should adopt — if anything it's a cautionary tale for keeping the nav graph in `commonMain`.
- **Net verdict:** Useful as proof that our chosen library is AAA-grade and that decentralized per-feature nav contracts + typed serializable routes scale — but **not** a source for our two priority capabilities (deep links, auth gating), which we must design ourselves or source elsewhere.

## Sources (specific files/commits/docs reviewed)
- `gradle/libs.versions.toml:111` — `org.jetbrains.androidx.navigation:navigation-compose` `2.8.0-alpha09`; `:11` Compose MP `1.7.0-beta01`.
- `app-android/src/main/java/io/github/droidkaigi/confsched/KaigiApp.kt` — root `KaigiNavHost`, nested graph, `MainNestedGraphStateHolder`, `ExternalNavController` (outbound links).
- `app-ios-shared/src/commonMain/kotlin/io/github/droidkaigi/confsched/shared/IosComposeKaigiApp.kt` — duplicated Compose nav graph + iOS `kaigiAppController(...)` `ComposeUIViewController` entry + iOS `ExternalNavController`.
- `core/droidkaigiui/src/commonMain/kotlin/io/github/droidkaigi/confsched/droidkaigiui/NavHostWithSharedAxisX.kt` — `NavHost(startDestination: String)` wrapper with shared-axis transitions.
- `feature/sessions/src/commonMain/.../TimetableItemDetailScreen.kt` (:84 `sessionScreens`, :91 `composable<TimetableItemDetailDestination>`, :106 `navigateToTimetableItemDetailScreen`) and `.../navigation/TimetableItemDetailDestination.kt` — typed `@Serializable` destination.
- `feature/sessions/src/commonMain/.../TimetableScreen.kt:71/:90`, `SearchScreen.kt:49/:67`, `feature/main/src/commonMain/.../MainScreen.kt:80` — string-route pattern.
- `app-android/src/main/AndroidManifest.xml` — only MAIN/LAUNCHER; no deep-link intent filters.
- `app-ios/App/App.entitlements` — no associated-domains; `app-ios/App/App.swift` — plain `WindowGroup`, no `.onOpenURL`; `app-ios/Sources/App/AppDelegate.swift` — Firebase init only.
- `app-ios/Sources/App/RootView.swift` + `RootReducer.swift` — SwiftUI `NavigationStack` + TCA `StackState` paths; `viewType` `.swiftUI`/`.compose` toggle; `Kmp*ComposeViewControllerWrapper` islands.
- `core/data/src/commonMain/.../auth/DefaultAuthApi.kt`, `.../user/UserDataStore.kt:54`, `core/data/src/androidMain/.../auth/AndroidAuthenticator.kt` — anonymous Firebase auth for API tokens only (no UI gating).
- Comparison: conference-app-2023 `gradle/libs.versions.toml:97` — Android-only `androidx.navigation:navigation-compose` `2.7.2` (2023 iOS was fully native SwiftUI; no shared Compose UI). Shows the 2023→2024 jump to the multiplatform nav fork.
