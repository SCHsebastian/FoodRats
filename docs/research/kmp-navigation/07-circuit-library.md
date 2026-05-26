# Circuit (library, Slack) — KMP Navigation Review

**One-liner:** Circuit is a Compose-driven UDF (Presenter/UI) architecture with its own `Screen`/`Navigator`/`NavStack` back stack that *replaces* androidx-navigation entirely; deep linking is a documented DIY pattern (parse URL → `List<Screen>`) and auth gating has a first-party idiomatic recipe via the `circuitx-navigation` `NavigationInterceptor`.

**Repo:** https://github.com/slackhq/circuit  **Docs:** https://slackhq.github.io/circuit/

Reviewed at source version `0.34.0-SNAPSHOT` (latest tagged release `0.33.1`, 2026-02-19). Circuit is pre-1.0.

## What it is

Circuit is a UDF presentation + navigation framework, not just a navigator. The core model:

- **`Screen`** — the navigation key. In `commonMain` it's an `expect interface` (`circuit-runtime-screen`); the Android actual is `Parcelable` (so screens are typically `@Parcelize data object`/`data class`). Screens carry the minimal data the target presenter needs.
  ```kotlin
  // commonMain
  @Immutable public expect interface Screen
  // androidMain
  @Immutable public actual interface Screen : Parcelable
  ```
- **`Presenter<State>`** — `@Composable fun present(): State`. Produces an immutable state + an `eventSink`. Receives a `Navigator` via its `Presenter.Factory`.
- **`Ui<State>`** — `@Composable fun Content(state, modifier)`. Pure render; emits events back through the state's `eventSink`.
- **`Navigator`** — the navigation interface (see next section).
- **`NavStack`/`BackStack`** — the saveable stack of `Screen` records. `0.33.0` introduced a new `NavStack` (`SaveableNavStack`, browser-style bidirectional) alongside the older `SaveableBackStack`; **both currently exist in the source** (active migration — see Weaknesses).
- **`Circuit` (builder)** — registry mapping `Screen` → presenter/UI factories. Screens are registered, not declared inline:
  ```kotlin
  val circuit = Circuit.Builder()
    .addPresenterFactory(...)
    .addUiFactory(...)
    // or via codegen: @CircuitInject(HomeScreen::class, AppScope::class)
    .build()
  ```
- **`CircuitContent(screen)`** — renders a single screen (presenter+UI) with no back stack. **`NavigableCircuitContent(navigator, backStack)`** — renders the whole navigable surface with animated transitions and back handling.

Provided in `CircuitCompositionLocals(circuit) { … }`. Codegen (`@CircuitInject`) wires factories into a DI graph (Dagger/Anvil/Hilt/kotlin-inject/Metro).

## Navigation approach & route definitions

There are **no route strings and no `NavGraph`**. "Routes" are just `Screen` instances; navigation is `navigator.goTo(SomeScreen(args))`. The `Navigator` interface (`circuit-runtime/.../Navigator.kt`):

```kotlin
public interface Navigator : GoToNavigator {
  public override fun goTo(screen: Screen): Boolean
  public fun forward(): Boolean              // NEW in 0.33.0 (bidirectional)
  public fun backward(): Boolean             // NEW in 0.33.0
  public fun pop(result: PopResult? = null): Screen?
  public fun peek(): Screen?
  public fun peekBackStack(): List<Screen>
  public fun peekNavStack(): NavStackList<Screen>?
  public fun resetRoot(newRoot: Screen, options: StateOptions = StateOptions.Default): List<Screen>
}
```

Setup (current source API):
```kotlin
setContent {
  val backStack = rememberSaveableBackStack(listOf(HomeScreen))   // root list
  val navigator = rememberCircuitNavigator(backStack, onRootPop = { /* exit */ })
  CircuitCompositionLocals(circuit) {
    NavigableCircuitContent(navigator = navigator, backStack = backStack)
  }
}
```
(The published docs site `navigation/` page still shows the older `rememberSaveableNavStack(root = HomeScreen)` name; the cloned source and the STAR sample use `rememberSaveableBackStack(listOf(...))`. Same concept, naming in flux.)

`resetRoot` is the key flow primitive — its own KDoc names "authentication flow" as the canonical use case, and it supports **multiple back stacks** (save/restore per root, for bottom-nav tabs) via `StateOptions`:
```kotlin
navigator.resetRoot(HomeScreen)                                  // wipe + new root (login → home)
navigator.resetRoot(HomeNavTab2, Navigator.StateOptions.SaveAndRestore)  // tabbed back stacks
```

**Results** ("answering navigator") let a screen return a typed `PopResult` to its caller without a shared VM — `rememberAnsweringNavigator<Screen.Result>(navigator) { result -> … }` then `navigator.pop(result)`. **Nested navigation** is supported via `CircuitContent(screen, onNavEvent = …)` forwarding to a parent navigator.

Typed-args comparison to FoodRats: FoodRats' `Route` sealed interface of `@Serializable data object`s maps almost 1:1 onto Circuit `Screen`s (sealed/`data` + `@Parcelize`). `popUpTo<Route.SignIn>()` becomes `navigator.resetRoot(SignInScreen)`.

## Auth gating (public vs protected routes, redirect-to-login) — idiomatic pattern

Two idiomatic options; the **first is first-party and is the recommended one** for "classify routes as protected vs public, redirect to login."

**(A) `NavigationInterceptor` (from the optional `circuitx-navigation` artifact).** An interceptor sits in front of the `Navigator` and can `Skip`, `Consume`, `Rewrite`, or `Fail` any `goTo`/`pop`/`resetRoot`. The docs ship a literal `AuthInterceptor` recipe (`docs/circuitx/navigation.md`):

```kotlin
class AuthInterceptor(private val authManager: AuthManager) : NavigationInterceptor {
  override fun goTo(screen: Screen): InterceptedGoToResult {
    if (screen is ProtectedScreen && !authManager.isLoggedIn()) {
      // Rewrite to login, carrying the original destination so we can resume after sign-in
      return InterceptedGoToResult.Rewrite(LoginScreen(afterLoginDestination = screen))
    }
    return NavigationInterceptor.Skipped
  }
}
```

Wired via `rememberInterceptingNavigator` (interceptors run in order; a `Rewrite` restarts interception with the new screen):
```kotlin
val interceptors = persistentListOf(
  AndroidScreenAwareNavigationInterceptor(starter),
  AuthInterceptor(authManager),
  UrlRewriteInterceptor,
)
val backStack = rememberSaveableBackStack(HomeScreen)
val baseNavigator = rememberCircuitNavigator(backStack)
val navigator = rememberInterceptingNavigator(baseNavigator, interceptors, eventListeners, notifier)
NavigableCircuitContent(navigator = navigator, backStack = backStack)
```
"Protected vs public" = a marker interface on screens (`ProtectedScreen`) or a `Map<KClass<out Screen>, …>` (the sibling `FeatureFlagInterceptor` recipe shows the map approach). Note: the interceptor's `isLoggedIn()` here is **synchronous** — a snapshot read; if auth is async you cache the latest auth state into something the interceptor can read synchronously.

**(B) Presenter-driven redirect (no extra artifact).** A "gate"/root presenter observes auth state and calls `navigator.resetRoot(...)` on change — the same shape FoodRats uses today:
```kotlin
@Composable
fun RootPresenter(navigator: Navigator): RootState {
  val session by sessionRepository.session.collectAsState(initial = null)
  LaunchedEffect(session) {
    when (session) {
      null -> navigator.resetRoot(SignInScreen)   // logged out → wipe stack to login
      else -> navigator.resetRoot(HomeScreen)
    }
  }
  …
}
```
`resetRoot` is explicitly documented for "preventing the user from returning to a completed workflow, such as a … authentication flow." Option B handles *reactive* sign-out (token expiry mid-session) cleanly; Option A handles *gate-on-navigate* cleanly. They compose.

## Deep links / universal links / app links (Android + iOS) — what library provides vs DIY

**Circuit ships no URL router.** It provides exactly one deep-link primitive: the back stack can be **initialized to a `List<Screen>`**, so deep linking = "parse external URL → `List<Screen>` → seed `rememberSaveableBackStack(thatList)`." Everything from URL → screens is your code.

**Android (App Links / custom scheme).** You add the `<intent-filter>` to `AndroidManifest.xml` yourself (standard Android), then parse `intent.data` in your Activity. The STAR sample does exactly this for an HTTP App Link (`samples/star/.../MainActivity.kt`):
```kotlin
val initialBackstack =
  if (intent.data == null) {
    listOf(HomeScreen)
  } else {
    val httpUrl = intent.data.toString().toHttpUrl()
    val animalId = httpUrl.pathSegments[1].substringAfterLast("-").toLong()
    listOf(HomeScreen, PetDetailScreen(petId = animalId, photoUrlMemoryCacheKey = null, animal = null))
  }
setContent {
  val backStack = rememberSaveableBackStack(initialBackstack)
  …
}
```
The docs' `deep-linking-android/` guide generalizes this with a `parseDeepLink(intent): List<Screen>?` that walks `dataUri.pathSegments` and `getQueryParameter(...)` to build the stack. Tested with `adb shell am start -a android.intent.action.VIEW -d "circuitapp://…/inbox/view_email?emailId=2"`. **There is no interop with androidx-navigation deep links** (`navDeepLink`, `<nav-graph>` auto-generated filters) — Circuit doesn't use androidx-navigation, so you don't get its manifest-deeplink tooling; you hand-write the intent filter and the parser.

For *navigating out* to a URL (Custom Tab / browser), Circuit's pattern is an `AndroidScreen`/`IntentScreen` wrapper intercepted by `rememberAndroidScreenAwareNavigator`, or a cross-platform `OpenUrlScreen` handled by `LocalUriHandler` (STAR's `urlAwareNavigator` does the latter in commonMain). That's the inverse of deep-in, but shows the same "screen as side-effect" interop seam.

**iOS (universal links).** **Not documented and not exercised by the sample.** The deep-linking guide is explicitly "Android only … extend the idea to other platforms as needed." STAR's iOS entry point is a bare `ComposeUIViewController { StarCircuitApp(graph.circuit) }` (`StarUiViewController.kt`) with no URL handling, and its iOS `Info.plist` declares no `CFBundleURLSchemes` and no `associated-domains`/`applinks` entitlement. So on iOS you: (1) add the Associated Domains entitlement + apple-app-site-association yourself, (2) catch the URL in Swift (`onOpenURL` / `scene(_:continue:)`), (3) parse it to a `List<Screen>` and pass it into your KMP composition entry point (you'd add a parameter to `makeUiViewController`, mirroring how FoodRats already threads Swift→KMP lambdas for GoogleSignIn/Crashlytics). 100% DIY, but the seam is straightforward and identical in spirit to the Android one.

**Advanced routing alternative:** the `circuitx-navigation` `UrlRewriteInterceptor` / `InterceptedResult.Rewrite(NavEvent)` lets you centralize "this internal screen actually means open-this-URL" logic — useful for a unified router, but still not a URL→Screen *parser*; you write that.

## Back stack & state restoration

- **Back handling is built in.** `NavigableCircuitContent` + `rememberCircuitNavigator` install a `BackHandler`/predictive-back that pops the stack; `onRootPop` fires when you'd pop past the root (use it to exit). On Android predictive back + gesture nav is provided by `circuitx-gesture-navigation` (`GestureNavigationDecorationFactory`), which STAR uses.
- **State restoration is a core feature.** `SaveableBackStack`/`SaveableNavStack` persist the screen stack across config changes/process death (screens are `Parcelable`). Per-screen UI/presenter state survives via **`rememberRetained { }`** (the `circuit-retained` module) — Circuit's equivalent of a ViewModel/`SavedStateHandle`, retained across config changes and (with the saveable variant) process death. This is a genuine differentiator vs hand-rolled state.
- **Results** survive restoration too (Circuit re-delivers a `PopResult` only to the original requester).

## iOS / multiplatform wiring & maturity

- **KMP-first.** Runtime modules (`circuit-runtime-*`, `circuit-foundation`, `circuit-retained`, `circuitx-*`) publish for Android, iOS (arm64 + sim arm64; **X64 Apple targets were just removed** in Unreleased), JVM/desktop, JS, and Wasm. `Screen` has per-platform actuals (Android `Parcelable`, others plain interface).
- **iOS wiring is trivial** at the surface: `ComposeUIViewController { App(circuit) }`. STAR runs on iOS via CocoaPods/SPM exactly like FoodRats' setup. No iOS-specific navigation gotchas in the back stack itself.
- **Maturity:** used in production at Slack and adopted by Tivi, but the library is **pre-1.0 (`0.x`) and the public navigation API is actively churning** — `0.33.0` (Feb 2026) introduced the whole `NavStack` model next to the existing `BackStack`, changed `circuitx-navigation` interceptor return types (`InterceptedGoToResult`/`InterceptedPopResult` → unified `InterceptedResult`), and changed `NavDecoration.DecoratedContent`'s signature. Even the published docs site is out of sync with source on basic names (`rememberSaveableNavStack` vs `rememberSaveableBackStack`). Expect breaking changes between minor bumps and doc/source drift.

## How Circuit interops with / replaces androidx navigation

**It replaces it.** Circuit brings its own `Navigator` + back stack and does not sit on top of `NavHost`/`NavController`. There is **zero** mention of `androidx.navigation`, `NavHost`, or `navigation-compose` anywhere in the docs. The `interop.md` page is only about Compose↔Android `View`, RxJava, and `Flow` — not androidx-nav. Practically: adopting Circuit means **deleting** FoodRats' `NavGraph`/`composable<Route.X>{}`/`popUpTo<>()` and re-expressing every screen as a Circuit `Screen`+`Presenter`+`Ui`. There is no incremental "wrap my existing NavHost" path; the closest is embedding a `CircuitContent`/`NavigableCircuitContent` inside one androidx `composable {}` destination (or vice versa) during a migration, since both are just `@Composable`s.

## Strengths

- **Deep linking is conceptually clean and identical across platforms**: parse URL → `List<Screen>` → seed the back stack. The STAR sample proves the Android App-Link path end-to-end.
- **First-party, documented auth-gate** (`AuthInterceptor` rewrite-to-login) — directly satisfies "protected vs public, redirect to sign-in," and is composable with analytics/feature-flag interceptors.
- `resetRoot` with multiple-back-stack `StateOptions` is purpose-built for login/onboarding flows and bottom-nav tabs.
- **State restoration + `rememberRetained`** are core, well-tested, KMP-wide — no ViewModel/SavedStateHandle plumbing.
- True UDF: presenter↔UI split, typed results without shared VMs, excellent testability (`circuit-test`), strong DI/codegen story.
- Genuinely KMP (incl. iOS) and production-proven (Slack, Tivi).

## Weaknesses

- **Pre-1.0 with active navigation-API churn** (`NavStack` migration, `circuitx-navigation` return-type changes in 0.33.0) and **docs that lag the source**. Higher maintenance/upgrade tax than JetBrains nav-compose.
- **Deep linking is entirely DIY** — no URL DSL, no manifest-deeplink generation, no androidx `navDeepLink` interop. You own the parser on both platforms.
- **iOS universal links are undocumented and unsampled** — you wire the entitlement + Swift URL catch + Screen parsing yourself (manageable, but no reference).
- **No incremental migration from androidx-navigation** — it's a wholesale replacement of the presentation+nav layer, not a drop-in `Navigator`.
- Adopting it is a paradigm shift: every screen becomes Presenter+UI+Screen, plus a `Circuit` registry and (ideally) codegen — large blast radius for an app already structured around MVI ViewModels.
- The auth `NavigationInterceptor` check is synchronous; reactive sign-out still needs the presenter/`resetRoot` approach (Option B).

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)

**Goal 1 — deep/app/universal links (both platforms):** *Supported, but DIY-heavy.* Circuit gives you the back-stack-seeding primitive and a proven Android pattern; you still write the URL→`List<Screen>` parser and, on iOS, the entitlement + Swift catch + parser. This is comparable effort to what FoodRats would do under most options — Circuit doesn't make it harder, but it also provides less than androidx-navigation's manifest/`navDeepLink` tooling on Android.

**Goal 2 — auth-gated routing:** *Strongest fit of any candidate so far.* `AuthInterceptor` (rewrite protected `Screen` → `SignInScreen(afterLogin=…)`) plus reactive `resetRoot` on sign-out is a clean, first-party, documented expression of exactly FoodRats' "protected vs public + redirect to SignIn" requirement — better than ad-hoc redirect logic in nav-compose.

**Migration cost: HIGH.** This is not a navigator swap. FoodRats' MVI `MviViewModel`/`State`/`Screen` triplets, `core/presentation` base, and `NavGraph` would all be rewritten into Circuit `Presenter`/`Ui`/`Screen` + a `Circuit` registry, and `rememberRetained` would replace ViewModel retention. The `Route` sealed interface maps cleanly to `Screen`, and the Swift→KMP seam FoodRats already has (GoogleSignIn/Crashlytics lambdas) is exactly the shape needed to feed iOS deep links in — so the *mechanics* are familiar, but the *scope* touches every feature module. Given FoodRats is mid-flight on the meal-AI feature and already pre-1.0-averse (it deliberately pins stable nav-compose `2.9.2`), Circuit's `0.x` churn is a real risk.

**Verdict:** Best-in-class for the auth-gating goal and clean (if DIY) for deep links, but it's a whole-architecture commitment (replaces androidx-nav *and* reframes the MVI layer) on a pre-1.0, actively-churning API — adopt only if FoodRats wants Circuit's UDF model wholesale, not as a targeted nav upgrade.

## Sources (specific docs pages / sample files reviewed)

- Docs (site): https://slackhq.github.io/circuit/navigation/ , https://slackhq.github.io/circuit/deep-linking-android/
- Docs (source, cloned `0.34.0-SNAPSHOT`):
  - `docs/navigation.md` (Navigator, NavStack, deep-link init, results, nested nav)
  - `docs/deep-linking-android.md` (`parseDeepLink` recipe, intent-filter, adb test)
  - `docs/circuitx/navigation.md` (**`AuthInterceptor`**, `UrlRewriteInterceptor`, `FeatureFlagInterceptor`, `InterceptingNavigator`, `AndroidScreenAwareNavigationInterceptor`)
  - `docs/navigation-navstack-migration.md` (0.33.0 NavStack migration + breaking changes)
  - `docs/screen.md`, `docs/interop.md` (interop is Compose/View/Rx/Flow only — no androidx-nav)
  - `CHANGELOG.md` (version `0.34.0-SNAPSHOT`; latest release `0.33.1` 2026-02-19), `gradle.properties` (`VERSION_NAME=0.34.0-SNAPSHOT`)
- Source:
  - `circuit-runtime/src/commonMain/.../Navigator.kt` (full `Navigator` interface, `resetRoot`/`StateOptions`, `popUntil`/`popRoot`)
  - `circuit-runtime-screen/src/commonMain/.../Screen.kt` + `src/androidMain/.../Screen.android.kt` (`expect interface Screen` / actual `: Parcelable`)
  - `circuitx/android/src/main/.../AndroidScreenAwareNavigator.kt` (`AndroidScreen`, `IntentScreen`, `rememberAndroidScreenAwareNavigator`)
  - `circuit-runtime-navigation/.../NavStack.kt` (new bidirectional `NavStack` API)
- STAR sample (canonical multiplatform sample):
  - `samples/star/src/commonMain/.../StarCircuitApp.kt` (composition root, `rememberSaveableBackStack`, `NavigableCircuitContent`, gesture nav, `urlAwareNavigator`/`OpenUrlScreen`)
  - `samples/star/src/androidMain/.../MainActivity.kt` (**App-Link deep link → `listOf(HomeScreen, PetDetailScreen)`**, `rememberAndroidScreenAwareNavigator`, CustomTabs `UriHandler`)
  - `samples/star/src/iosMain/.../StarUiViewController.kt` (bare `ComposeUIViewController` — no iOS deep-link handling)
  - `samples/star/androidApp/src/main/AndroidManifest.xml` (no deep-link intent-filter in sample manifest) and `samples/star/iosApp/starIOS/Info.plist` (no `CFBundleURLSchemes`/`applinks`)
  - `samples/star/src/commonMain/.../navigation/OpenUrlScreen.kt` (cross-platform `expect class … : Screen` for URL-out)
