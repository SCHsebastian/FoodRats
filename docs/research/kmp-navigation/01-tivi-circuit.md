# Tivi (Circuit) — KMP Navigation Review

**One-liner:** Tivi navigates with Slack's Circuit — a single `SaveableBackStack` of `@Parcelize` `Screen` data classes driven through Circuit's `Navigator`, shared across Android/iOS/desktop, with custom-scheme deep links but **no** universal links and **no** auth-gated routing.
**Repo:** https://github.com/chrisbanes/tivi (reviewed at commit `a0c62c2`, the final "Deprecated" state, 2024-11-12)
**Docs:** https://slackhq.github.io/circuit/ (Circuit) · https://github.com/chrisbanes/tivi/tree/main/docs
**Approach:** Circuit (Slack) — `com.slack.circuit:circuit-* 0.25.0`

> Note: as of this commit Tivi's README declares the project **deprecated/archived** ("I've decided to stop work on Tivi"). The architecture below is still a high-quality, complete reference; just be aware it is frozen, not actively maintained.

## Why it's AAA-grade
- Real, shipped Compose-Multiplatform app (Android + iOS + JVM desktop) by Chris Banes (ex-Google Compose team). One shared UI/navigation layer, three thin platform entrypoints.
- Clean module split: `Screen` definitions are their own tiny module (`common/ui/screens`) depending only on `circuit-runtime`; each feature is an independent `ui/<feature>` module contributing Circuit presenter/UI factories via DI (kotlin-inject). The root (`ui/root`) owns the back stack + scaffold.
- DI-assembled Circuit: every feature module exposes a `*Component` that multibinds a `Presenter.Factory`/`Ui.Factory`; `SharedUiComponent.provideCircuit(...)` folds the `Set`s into one `Circuit`. Adding a screen = add a factory binding, no central registry edit.

## Navigation approach & route definitions
- **Routes are Circuit `Screen`s** (not URL strings, not androidx `NavType`). All live in one file: `common/ui/screens/src/commonMain/kotlin/app/tivi/screens/Screens.kt`. They subclass a project base `TiviScreen(name)` (carries an analytics name + optional `arguments` map) which implements `com.slack.circuit.runtime.screen.Screen`:
  ```kotlin
  // Screens.kt:8-20, 82-84
  @Parcelize object DiscoverScreen : TiviScreen(name = "Discover()")
  @Parcelize data class EpisodeDetailsScreen(val id: Long) : TiviScreen(name = "EpisodeDetails()") {
    override val arguments get() = mapOf("id" to id)
  }
  abstract class TiviScreen(val name: String) : Screen { open val arguments: Map<String, *>? = null }
  ```
  Type-safe args are just constructor params (`ShowDetailsScreen(id: Long)`, `ShowSeasonsScreen(showId, selectedSeasonId?, openEpisodeId?)`). This is FoodRats' `@Serializable Route` pattern's analogue — except the carrier is `Parcelable`, not `@Serializable`, and there is no NavGraph DSL.
- **`@Parcelize` is multiplatform-faked.** `app.tivi.screens.Parcelize` is a no-op `@Target(CLASS) annotation class` in `commonMain` (`Parcelize.kt`). On the **Android target only**, the kotlin-parcelize compiler plugin is told to treat it as `@Parcelize` via a compiler arg — see `common/ui/screens/build.gradle.kts`:
  ```kotlin
  if (isAndroidTarget) freeCompilerArgs.addAll(
    "-P","plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=app.tivi.screens.Parcelize")
  ```
  So Screens are real `Parcelable`s on Android (state save/restore for free) and plain objects elsewhere.
- **Navigation is imperative via `Navigator`**, called from presenters (never from Composables): `navigator.goTo(screen)`, `navigator.pop(result)`, `navigator.resetRoot(screen)`. Example — `ui/discover/.../DiscoverPresenter.kt:152-159`:
  ```kotlin
  DiscoverUiEvent.OpenAccount -> navigator.goTo(AccountScreen)
  is DiscoverUiEvent.OpenShowDetails -> navigator.goTo(ShowDetailsScreen(event.showId))
  ```
  A `Presenter.Factory` per feature maps `Screen -> Presenter` with a `when (screen) { is DiscoverScreen -> ... }` (`DiscoverPresenter.kt:46-58`). Circuit pairs that with a `Ui.Factory` to render.
- **The back stack is rendered by `NavigableCircuitContent`** in `ui/root/.../Home.kt:166-173`, wrapped in `ContentWithOverlays` (Circuit overlays for dialogs/bottom sheets) with `GestureNavigationDecoration(onBackInvoked = navigator::pop)` for predictive-back / iOS swipe.
- Bottom-nav tab switching uses `resetRoot(..., saveState=true, restoreState=true)` (per-tab back stack save/restore) via a `resetRootIfDifferent` helper that pops-to-root if you re-tap the active tab (`Home.kt:400-414`).

## Auth gating (public vs protected routes, redirect-to-login)
**There is none. Tivi does not classify routes as protected vs public and never redirects to a sign-in screen.** This is the single biggest gap vs FoodRats' requirement.
- Auth is **optional and feature-local**, not a routing concern. Login is a Trakt OAuth flow triggered as a Circuit event from the Account screen — `ui/account/.../AccountPresenter.kt:75`: `AccountUiEvent.Login -> launchOrThrow { loginTrakt.value.invoke() }`. There is no `LoginScreen`/`SignInScreen` in `Screens.kt` at all (the OAuth UI is an external browser/Custom Tab / AppAuth, not a Circuit screen).
- The closest thing to a "root auth observer" is `ui/root/.../RootViewModel.kt`, and it does **not** gate navigation — it just observes `ObserveTraktAuthState`, refreshes user details on `LOGGED_IN`, and calls `LogoutTrakt` on a Trakt 401:
  ```kotlin
  observeTraktAuthState.value.flow.debounce(200.ms)
    .filter { it == TraktAuthState.LOGGED_IN }.collect { refreshMe() }
  ```
- Screens that need auth **degrade gracefully** instead of being blocked: Discover/UpNext/Library presenters read `ObserveTraktAuthState` and show a logged-out empty/login-prompt state; the route itself is always reachable.
- **Implication for FoodRats:** Circuit gives you the primitives to build auth gating yourself (an `AuthAwareNavigator` decorator over `goTo`, or an auth observer that `resetRoot(SignInScreen)`), but Tivi ships **no** reference implementation of it. You'd be designing the guard from scratch. (Tivi *does* show the decorator pattern — see `TiviNavigator` below — which is exactly where a guard would slot in.)

## Deep links / universal links / app links (Android + iOS)
**Custom-scheme deep links: yes (cross-platform, elegant). HTTPS App Links / iOS Universal Links: NO — entirely absent from the repo.**

Mechanism — a tiny shared `DeepLinker` + a pure-Kotlin URL→Screen parser, fed by each platform:
- **Shared core** `common/ui/circuit/.../navigation/DeepLinker.kt`:
  - `DeepLinker` is an app-scoped class holding a `MutableSharedFlow<Uri>` (eygraber `uri-kmp`, multiplatform). Platforms push URIs in via `addDeeplink(uri)`.
  - `Navigator.applyDeeplink(deeplink: Uri)` is the parser: it walks `deeplink.pathSegments` pairwise (`"show"/123`, `"season"/45`, `"episode"/67`) building a `List<Screen>`, then `resetRoot(DiscoverScreen)` + `goTo(each)` inside a `Snapshot.withMutableSnapshot` so the back stack is rebuilt atomically:
    ```kotlin
    "show"    -> queued.add(ShowDetailsScreen(id = id))
    "season"  -> queued.add(ShowSeasonsScreen(showId = last.id, selectedSeasonId = id))
    "episode" -> queued.add(EpisodeDetailsScreen(id))
    ```
  - `LaunchDeepLinker(deepLinker, navigator)` is a Composable in the root that `collect`s `deepLinker.pending` and applies each (`TiviContent.kt:84`). **The URL→Screen mapping is 100% in commonMain** — platforms only supply the raw URI.
- **URI format:** `<applicationId>://tivi/show/{id}/season/{id}/episode/{id}` — a **custom scheme**, not https. Generated for local episode notifications in `domain/.../ScheduleEpisodeNotifications.kt:92`.
- **Android wiring:**
  - `android-app/app/src/main/AndroidManifest.xml:53-63`: `MainActivity` (`launchMode="singleTask"`) has a VIEW/BROWSABLE intent-filter with `<data android:host="tivi" android:scheme="${applicationId}" />`. **No `android:autoVerify`, no `https` scheme → these are custom-scheme deep links, not verified Android App Links.**
  - Intent capture: `TiviActivity.onPostCreate`/`onNewIntent -> handleIntent(intent)`; `MainActivity.handleIntent` does `intent.data?.toUri()?.let(deepLinker::addDeeplink)` (`MainActivity.kt:85-90`).
- **iOS wiring:** **only push notifications produce deep links.** In `ios-app/Tivi/Tivi/TiviApp.swift:61-72`, `userNotificationCenter(_:didReceive:)` pulls `userInfo["deeplink_uri"]` and calls `applicationComponent.deepLinker.addDeeplink(string:)`. There is **no** `scene(_:continue:)` / `NSUserActivity` / `application(_:open:)` path for app deep links — the one `open url:` handler present is solely for AppAuth OAuth callback (`TiviApp.swift:40-52`).
- **Universal Links / App Links / AASA:** **confirmed absent.** A full-repo search for `apple-app-site-association`, `applinks`, `com.apple.developer.associated-domains`, `associatedDomains`, `NSUserActivity`, `continueUserActivity`, `autoVerify`, `webcredentials` returned **zero matches**, and there are **no `.entitlements` files** in the repo. So Tivi cannot be opened from an `https://` link on either platform — only from its custom scheme (Android intent) or a local notification (both platforms).
- Desktop (`desktop-app/.../Main.kt`) wires the back stack/navigator but no deep-link source.

## Back stack & state restoration
- Single `SaveableBackStack` created at each platform root via `rememberSaveableBackStack(listOf(DiscoverScreen))` + `rememberCircuitNavigator(backstack)` — Android `MainActivity.kt:65-66`, iOS `TiviUiViewController.kt:50-51`, desktop `Main.kt:35-36`. All three start at `DiscoverScreen`.
- State restoration: Circuit's `SaveableBackStack` + Android `@Parcelize` Screens survive process death / config change; `LocalRetainedStateRegistry provides continuityRetainedStateRegistry()` (`TiviContent.kt:105`) plus `circuit-retained` (`collectAsRetainedState`, `rememberRetained`) preserve presenter state across config changes without ViewModels.
- Per-tab back-stack save/restore via `resetRoot(saveState=true, restoreState=true)` on bottom-nav switches (`Home.kt:121,146`).
- Predictive back / iOS edge-swipe via `circuitx-gesture-navigation`'s `GestureNavigationDecoration(onBackInvoked = navigator::pop)` (`Home.kt:169-171`); Android manifest sets `android:enableOnBackInvokedCallback="true"`.

## iOS / multiplatform wiring
- **Shared side** exposes a UIViewController factory. `ui/root/src/iosMain/.../TiviUiViewController.kt`: `typealias TiviUiViewController = () -> UIViewController`; the function builds `ComposeUIViewController { val backstack = rememberSaveableBackStack(...); val navigator = rememberCircuitNavigator(backstack, onRootPop = {}); tiviContent.Content(backstack, navigator, onOpenUrl = { SFSafariViewController... }, Modifier) }`. So **the navigator/back stack are created in Kotlin**, identically to Android — Swift never touches Circuit.
- **`TiviContent.Content`** (`ui/root/.../TiviContent.kt`) is the shared entrypoint taking `(backstack, navigator, onOpenUrl, modifier)`. It wraps `navigator` in a `TiviNavigator` decorator that intercepts `UrlScreen` → `onOpenUrl` (Custom Tab on Android / `SFSafariViewController` on iOS) and otherwise delegates — **this decorator is exactly where an auth guard would be added.** It then provides `LocalNavigator`, Circuit composition locals, and renders `Home(...)`.
- **`LocalNavigator`** (`common/ui/circuit/.../navigation/LocalNavigator.kt`) is a `staticCompositionLocalOf<Navigator> { Navigator.NoOp }` — a deliberate workaround (commented) for slackhq/circuit#653 so deep-link/overlay code can reach the navigator outside the Circuit content tree.
- **Swift entrypoint** (`ios-app/Tivi/Tivi/TiviApp.swift` + `ContentView.swift`): SwiftUI `App` builds a kotlin-inject `IosApplicationComponent` + `HomeUiControllerComponent`, and `ComposeView: UIViewControllerRepresentable` returns `component.uiViewControllerFactory()`. The Swift layer's only navigation responsibility is forwarding notification deep links into `applicationComponent.deepLinker`.

## Strengths
- **Genuinely shared navigation across 3 platforms** — back stack, navigator, and URL→Screen parsing all in commonMain; platform code is ~10 lines each.
- **Type-safe args** via Screen data-class constructors; no string routes, no `NavType` registration, no serialization keys to maintain.
- **DI-multibinding registration** — features are fully decoupled; the root never imports feature internals, only their DI components.
- **Deep-link parsing is platform-agnostic and trivially unit-testable** (`Navigator.applyDeeplink` is pure Kotlin over a `Uri`).
- **State restoration is first-class** (SaveableBackStack + retained state + Parcelable screens) and predictive-back/gesture-back work out of the box.
- Clear decorator seam (`TiviNavigator`) for cross-cutting nav concerns.

## Weaknesses
- **No auth gating whatsoever** — no protected/public route concept, no redirect-to-login. You'd build it yourself (Tivi's model is "auth is optional, screens degrade").
- **No HTTPS App Links / iOS Universal Links / AASA / associated-domains** anywhere in the repo. Deep links are custom-scheme on Android and notification-only on iOS. Opening from a web URL is unsupported on both platforms — a hard gap vs FoodRats goal #1.
- **iOS deep-link surface is incomplete**: only `didReceive` notification responses feed `DeepLinker`; cold-start from a URL, `scene(_:openURLContexts:)`, and `NSUserActivity` are not handled.
- **Third-party dependency on Circuit** (Slack) + `uri-kmp` + kotlin-inject — a different stack from FoodRats' androidx-nav + Koin. Circuit replaces the ViewModel/MVI base entirely (Presenter + UiState + eventSink), so adopting it is an architecture change, not a library swap.
- **`@Parcelize`-via-compiler-arg trick** and the `LocalNavigator` NoOp workaround (circuit#653) are a bit of necessary cleverness, not plain API.
- Frozen/deprecated project — no future fixes for the iOS deep-link gaps.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)
- **Goal 1 — universal links / app links / deep links (both platforms): partially instructive, not a turnkey answer.** Tivi's *internal* design — a shared `DeepLinker` `Flow<Uri>` + a pure-commonMain `Uri -> List<Screen>` parser + thin per-platform intake — is an **excellent pattern FoodRats should copy regardless of which nav library it picks**: keep URL parsing in commonMain, have each platform only hand over the raw URI. But Tivi proves nothing about **verified App Links / iOS Universal Links** — it has none (no `autoVerify`, no AASA, no associated-domains entitlement). For FoodRats' https-universal-link requirement you must look elsewhere (the platform layer: Android `autoVerify` intent-filters + `/.well-known/assetlinks.json`; iOS Associated Domains + `apple-app-site-association` + `scene(_:continue:)`), then route the resulting URI through a Tivi-style shared parser.
- **Goal 2 — auth-gated routing: Tivi does NOT help.** It has no route guard, no protected/public split, no redirect-to-login. FoodRats (which genuinely gates on sign-in + active crew) gets no reference here. With androidx-nav this is straightforward (observe session, `navigate(SignIn){ popUpTo<...>{ inclusive } }`); in Circuit you'd wrap the `Navigator` (à la `TiviNavigator`) or drive `resetRoot(SignInScreen)` from a root auth observer — but you'd design it yourself.
- **Migration cost from androidx-nav → Circuit: high.** Circuit is not a navigation library you bolt on; it's a UDF architecture that **replaces** FoodRats' `MviViewModel`/`State`/`handle(intent)` base (CLAUDE.md `:core:presentation`) with Circuit `Presenter` + `CircuitUiState` + `eventSink`, replaces `composable<Route.X>{}` NavGraph with `Presenter.Factory`/`Ui.Factory` multibindings, swaps `@Serializable Route` for `@Parcelize Screen`, and (in Tivi) pairs with kotlin-inject rather than Koin. Every feature ViewModel/Screen would be rewritten. That's a large, cross-cutting refactor for a codebase whose six features are already on `main`.
- **Verdict:** Tivi is a top-tier reference for *cross-platform Compose nav mechanics and a clean shared deep-link parser*, and worth mining for the `DeepLinker` pattern. But it does **not** demonstrate either of FoodRats' two priority capabilities (web universal links, auth gating), and adopting Circuit wholesale is a full architecture migration. Recommendation: **keep androidx Compose-Multiplatform Navigation, borrow Tivi's shared-`DeepLinker` idea, and source universal-link + auth-gating patterns from other references.**

## Sources (specific files/commits/docs reviewed)
Reviewed locally at commit `a0c62c2c763c83e3a0ecf79b283224374bb06c4a` (2024-11-12, "Deprecated"):
- `common/ui/screens/src/commonMain/kotlin/app/tivi/screens/Screens.kt` — Screen/route definitions, `TiviScreen` base
- `common/ui/screens/src/commonMain/kotlin/app/tivi/screens/Parcelize.kt` + `common/ui/screens/build.gradle.kts` — multiplatform `@Parcelize` fake + Android compiler-arg
- `common/ui/circuit/src/commonMain/kotlin/app/tivi/navigation/DeepLinker.kt` — `DeepLinker`, `Navigator.applyDeeplink`, `LaunchDeepLinker`
- `common/ui/circuit/src/commonMain/kotlin/app/tivi/navigation/LocalNavigator.kt` — NoOp navigator composition-local (circuit#653)
- `common/ui/circuit/src/commonMain/kotlin/app/tivi/EventSink.kt` — `wrapEventSink`
- `ui/root/src/commonMain/kotlin/app/tivi/home/TiviContent.kt` — shared root entry, `TiviNavigator` decorator (`UrlScreen`/onOpenUrl), composition-local wiring
- `ui/root/src/commonMain/kotlin/app/tivi/home/Home.kt` — `NavigableCircuitContent`, `ContentWithOverlays`, gesture-back, bottom-nav `resetRoot`/save-restore
- `ui/root/src/commonMain/kotlin/app/tivi/home/RootViewModel.kt` — Trakt auth observation (no nav gating)
- `ui/root/src/iosMain/kotlin/app/tivi/home/TiviUiViewController.kt` — iOS `ComposeUIViewController` factory + back stack/navigator creation
- `ui/discover/src/commonMain/kotlin/app/tivi/home/discover/DiscoverPresenter.kt` — `Presenter.Factory` + `navigator.goTo(...)` usage
- `ui/account/src/commonMain/kotlin/app/tivi/account/AccountPresenter.kt` — login/logout as Circuit events (no LoginScreen)
- `shared/common/src/commonMain/kotlin/app/tivi/inject/SharedUiComponent.kt` — `Circuit.Builder` assembled from multibound factory `Set`s
- `android-app/app/src/main/AndroidManifest.xml` — custom-scheme VIEW intent-filter (no `autoVerify`/https)
- `android-app/app/src/main/kotlin/app/tivi/home/MainActivity.kt` + `TiviActivity.kt` — Android intent → `DeepLinker`
- `ios-app/Tivi/Tivi/TiviApp.swift`, `ContentView.swift`, `Info.plist` — iOS entry; notification-only deep links; no associated-domains/AASA
- `domain/src/commonMain/kotlin/app/tivi/domain/interactors/ScheduleEpisodeNotifications.kt:92` — deep-link URI format `<applicationId>://tivi/show/{id}/season/{id}/episode/{id}`
- `desktop-app/src/jvmMain/kotlin/app/tivi/Main.kt` — desktop back stack/navigator
- `gradle/libs.versions.toml` — `circuit = 0.25.0`, `uri-kmp 0.0.18`, kotlin-parcelize
- Negative searches (zero hits, no `.entitlements` files): `apple-app-site-association`, `applinks`, `com.apple.developer.associated-domains`, `associatedDomains`, `autoVerify`, `NSUserActivity`, `continueUserActivity`, `webcredentials`
- Circuit docs: https://slackhq.github.io/circuit/ (navigation/back-stack model)
