# AndroidX/JetBrains Compose Multiplatform Navigation — Patterns Review (FoodRats' current baseline)

**One-liner:** The multiplatform port of AndroidX Navigation Compose gives FoodRats type-safe `@Serializable` routes, an idiomatic observe-auth-state → `navigate { popUpTo(inclusive) }` gating pattern, and — as of CMP 1.8.2 + nav-compose **2.9.2** (the exact version FoodRats pins) — working iOS deep links via a small `ExternalUriHandler` singleton wired in Swift, so the "stay and do it right" path needs **zero migration**.

**Library:** `org.jetbrains.androidx.navigation:navigation-compose` (FoodRats pins `navCompose = "2.9.2"` in `gradle/libs.versions.toml`)
**Docs:**
- Navigation and routing — https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html (was jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-routing.html; 301→kotlinlang.org)
- Deep links — https://kotlinlang.org/docs/multiplatform/compose-navigation-deep-links.html
- Navigation in Compose (overview) — https://kotlinlang.org/docs/multiplatform/compose-navigation.html
- What's new in CMP 1.8.x — https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html
- AndroidX type safety — https://developer.android.com/guide/navigation/design/type-safety
- AndroidX conditional/auth nav — https://developer.android.com/guide/navigation/use-graph/conditional
- AndroidX deep links — https://developer.android.com/guide/navigation/design/deep-link

---

## What it is & current CMP maturity

`navigation-compose` is JetBrains' KMP republication of AndroidX `androidx.navigation:navigation-compose`. Same packages (`androidx.navigation.*`, `androidx.navigation.compose.*`), same API surface (`NavHost`, `NavController`, `composable<T>`, `toRoute<T>`, `navDeepLink`, `popUpTo`), available in `commonMain` across Android / iOS / Desktop / Wasm / JS. This is what FoodRats already uses — `NavGraph.kt` imports `androidx.navigation.compose.NavHost`, `androidx.navigation.compose.composable`, `androidx.navigation.toRoute`, `androidx.navigation.compose.rememberNavController`.

Maturity timeline (from the CMP "what's new" pages):
- **1.8.0** — navigation library promoted out of alpha; type-safe routes recommended.
- **1.8.2** — bundled nav `2.9.0-beta03`, "based on Jetpack Navigation 2.9.0". Crucial line for this review:
  > "By using Compose Multiplatform 1.8.2 along with `org.jetbrains.androidx.navigation.navigation-compose` 2.9.2, you can implement **deep linking on iOS** in the usual Compose manner: assigning deep links to destinations and navigating to them using a `NavController`."

  So iOS deep links are **officially supported at exactly the version FoodRats already pins (2.9.2)** — they are no longer the historical gap they were through 2024 (issue [JetBrains/compose-multiplatform#5003](https://github.com/JetBrains/compose-multiplatform/issues/5003), opened 2024-06-21, assigned to MatkovIvan, now closed).
- **Breaking change to be aware of** (1.8.2 / nav 2.9.*): state storage moved from `Bundle` to `SavedState`. Code that reads raw args via `arguments?.getString("x")` must become `arguments?.read { getStringOrNull("x") }`. **FoodRats is unaffected** — it never touches the raw arg bundle; it uses `entry.toRoute<Route.X>()` everywhere (`NavGraph.kt:101`, `:145`), which is the typed path that hides `SavedState`.

Predictive back: the overview page documents back-gesture handling on both Android and iOS, but the 1.8.x notes I reviewed do **not** claim full Android-13 predictive-back animation parity in commonMain — treat predictive-back polish as platform-dependent and not a guaranteed CMP feature. (Honest gap; see Weaknesses.)

---

## Route definitions (type-safe) — fits FoodRats' existing Route sealed interface

FoodRats already follows the official type-safe pattern verbatim. Official shape (developer.android.com/guide/navigation/design/type-safety + the routing page):

```kotlin
@Serializable object Home                       // no-arg destination
@Serializable data class Profile(val id: String) // destination with args

NavHost(navController, startDestination = Home) {
    composable<Home> { HomeScreen(/* … */) }
    composable<Profile> { backStackEntry ->
        val profile: Profile = backStackEntry.toRoute()   // typed args
        ProfileScreen(profile.id)
    }
}
navController.navigate(Profile(id = "123"))               // typed navigate
```

FoodRats' `Route.kt` is already idiomatic:
```kotlin
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data object SignIn : Route
    @Serializable data class CrewSettings(val crewId: String) : Route
    @Serializable data class MealDetail(val mealId: String, val dayIso: String) : Route
    // …
}
```
and consumes it correctly: `composable<Route.MealDetail> { entry -> val args = entry.toRoute<Route.MealDetail>() }` (`NavGraph.kt:144-145`), `popUpTo<Route.CaptureMeal> { inclusive = true }` (`:123`).

**Nested graphs** (`navigation<Graph>()`): the type-safe nested-graph DSL is `navigation<ParentRoute>(startDestination = ChildRoute) { composable<ChildRoute> { … } }`. FoodRats currently approximates a nested graph by nesting a *second* `NavHost` (the `MainScaffold` inner `rememberNavController()` + `NavHost(startDestination = MainTab.Feed)` at `NavGraph.kt:291`). That works, but the canonical type-safe alternative for a bottom-nav section is a single `navigation<Route.Main>` subgraph inside the root host. Note FoodRats' `MainTab.Feed/Stats` are declared `: Route` (not a separate marker) — fine, but see the auth-gating section for a cleaner marker split.

---

## Auth gating (public vs protected routes, redirect-to-login) — concrete pattern for FoodRats

**Official AndroidX guidance** (developer.android.com/guide/navigation/use-graph/conditional): auth logic lives in a shared `ViewModel`, the gate **observes** auth state, and redirect uses `NavOptions` with `popUpTo(..., inclusive = true)` so the protected destination is removed from the back stack and can't be re-entered with Back. Verbatim principle from the doc:
> "All logic pertaining to authentication is held within `UserViewModel`. … it is not the responsibility of either `LoginFragment` or `ProfileFragment` to determine how users are authenticated."

Redirect shape (doc, adapted to Compose typed API):
```kotlin
navController.navigate(SignIn) {
    popUpTo(navController.graph.startDestinationId) { inclusive = true }
    launchSingleTop = true
}
```

**FoodRats already implements exactly this, well.** `RootNavViewModel` is the single source of auth truth — it `combine`s `session.current`, `activeCrew.current`, `notifications.prompted` into a `RootStage` and `emit(RootNavEffect.NavigateTo(route))` only on stage change (`RootNavViewModel.kt:21-54`). `FoodRatsApp` collects those effects and calls `rootController.navigateTopLevel(eff.route)` (`FoodRatsApp.kt:22-32`). And `navigateTopLevel` is a *better-than-the-doc* implementation of the inclusive-popUpTo redirect — it reads the **live** back stack and pops inclusive of its current bottom rather than a configured start id, which (per its own docstring at `NavGraph.kt:155-169`) fixes the real bug where `popUpTo<Route.Splash>` becomes a silent no-op after the first top-level transition:
```kotlin
fun NavHostController.navigateTopLevel(route: Route) {
    val bottomDestId = currentBackStack.value
        .firstOrNull { it.destination !is NavGraph }?.destination?.id
    navigate(route) {
        bottomDestId?.let { popUpTo(it) { inclusive = true; saveState = false } }
        launchSingleTop = true
        restoreState = false
    }
}
```

**"Doing it right" enhancement — model public vs protected as a marker on `Route`.** FoodRats currently classifies stages imperatively inside `RootNavViewModel`. The clean, idiomatic upgrade is a marker so the gate is data-driven and exhaustiveness-checked by the compiler. It fits the existing `sealed interface Route` with no restructuring:

```kotlin
sealed interface Route {
    /** Reachable without a session (sign-in, splash, deep-link landing while logged out). */
    sealed interface Public : Route
    /** Requires an authenticated session; gate redirects to SignIn otherwise. */
    sealed interface Protected : Route

    @Serializable data object Splash : Public
    @Serializable data object SignIn : Public

    @Serializable data object Main : Protected
    @Serializable data object CrewPicker : Protected
    @Serializable data class  CrewSettings(val crewId: String) : Protected
    @Serializable data class  MealDetail(val mealId: String, val dayIso: String) : Protected
    // …
}
```

Gate in the root composable (e.g. a `LaunchedEffect` keyed on session + current destination, or folded into `RootNavViewModel`):
```kotlin
// when a deep link or nav lands on a Protected route with no session, bounce to SignIn
LaunchedEffect(session, currentRoute) {
    if (currentRoute is Route.Protected && session == null) {
        navController.navigateTopLevel(Route.SignIn)
    }
}
```
This matters specifically for **deep links into protected screens** (e.g. a `MealDetail` universal link opened while logged out): the marker lets the gate intercept *after* the NavController resolves the deep-link destination but *before* the protected screen renders, then resume to the intended target post-login (stash the pending `Route` in `SessionProvider`/`SavedState` and replay it when `session != null`). FoodRats' reactive `RootNavViewModel` already gives the resume-after-login backbone — the marker just makes the protected/public decision total instead of a hand-maintained `when`.

---

## Deep links / universal links / app links (Android + iOS) — exact wiring, and iOS limitations

### Declaring deep links on a destination (common code)
`navDeepLink` attaches one or more URI patterns to a `composable<T>`. Both the generated-pattern form (`navDeepLink<T>(basePath = …)`) and the explicit-pattern form (`navDeepLink { uriPattern = … }`) are supported (deep-links page, verbatim):
```kotlin
@Serializable @SerialName("dlscreen")
data class DeepLinkScreen(val name: String)

composable<DeepLinkScreen>(
    deepLinks = listOf(
        navDeepLink { uriPattern = "$firstBasePath?name={name}" },     // explicit pattern
        navDeepLink { uriPattern = "demo://example2.org/name={name}" },
        navDeepLink<DeepLinkScreen>(basePath = "$firstBasePath/dlscreen"), // generated from route shape
    )
) {
    val deeplink: DeepLinkScreen = backStackEntry.toRoute()  // typed extraction
}
```
For a route like `data class PlantDetail(val id: String, val name: String, val colors: List<String>, val latinName: String? = null)`, `navDeepLink<PlantDetail>(basePath = "demo://example.com/plant")` generates:
```
<basePath>/{id}/{name}/?colors={color1}&colors={color2}&latinName={latinName}
```
(required args → path segments; nullable/defaulted args → optional query params.)

### How CMP actually delivers an external URI into the NavController (the key cross-platform fact)
**CMP does NOT use `NavController.handleDeepLink(intent)` as the common entry point.** (`handleDeepLink`/`onNewIntent` are the Android-only AndroidX mechanism — see below.) The JetBrains common pattern is a tiny singleton that buffers the incoming URI and replays it into a listener that calls `navController.navigate(NavUri(uri))`. Verbatim from the deep-links page:

```kotlin
// commonMain — the cross-platform funnel for external URIs
object ExternalUriHandler {
    private var cached: String? = null
    var listener: ((uri: String) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                cached?.let { value.invoke(it) }   // replay a URI that arrived before the NavHost existed
                cached = null
            }
        }
    fun onNewUri(uri: String) {
        cached = uri
        listener?.let { it.invoke(uri); cached = null }
    }
}
```
```kotlin
// commonMain — register the listener for the NavHost's lifetime
internal fun App(navController: NavHostController = rememberNavController()) = AppTheme {
    DisposableEffect(Unit) {
        ExternalUriHandler.listener = { uri -> navController.navigate(NavUri(uri)) }
        onDispose { ExternalUriHandler.listener = null }
    }
    NavHost(navController = navController, startDestination = FirstScreen) { /* … */ }
}
```
The `cached`-replay is load-bearing: a cold-start universal link can arrive *before* Compose has created the NavController, so the singleton holds it until the `listener` is set.

> Note: the routing page also documents an `App(onNavHostReady: suspend (NavController) -> Unit = {})` callback that runs in `LaunchedEffect(navController) { onNavHostReady(navController) }`. That's the general mechanism for handing the live `NavController` out of common Compose to native code (used for web `bindToBrowserNavigation()` and native-iOS interop). For deep links specifically, the `ExternalUriHandler` singleton above is the documented route — it avoids holding a `NavController` reference in Swift.

### Android wiring
Declare an `<intent-filter>` on the launcher activity in `AndroidManifest.xml` (the CMP deep-links page defers to developer.android.com for the XML). App Links shape:
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
          android:host="foodrats.app"
          android:pathPrefix="/meal" />
</intent-filter>
```
`android:autoVerify="true"` makes it a verified **App Link** (requires `https://foodrats.app/.well-known/assetlinks.json`); drop it for a plain custom-scheme/implicit deep link.

Two ways to feed the intent in:
1. **AndroidX-native:** `navController.handleDeepLink(intent)` (auto on `launchMode="standard"`; manual in `onNewIntent` for `singleTop`):
   ```kotlin
   override fun onNewIntent(intent: Intent) {
       super.onNewIntent(intent)
       navController.handleDeepLink(intent)
   }
   ```
2. **CMP common funnel (matches iOS):** in the Activity, forward the URI string into the shared singleton so both platforms share one code path:
   ```kotlin
   // MainActivity.onCreate / onNewIntent
   intent?.data?.let { ExternalUriHandler.onNewUri(it.toString()) }
   ```
   FoodRats' `MainActivity` would also need `android:launchMode="singleTop"` (currently absent) so a deep link to a running app reuses the task instead of stacking a new Activity.

**FoodRats' manifest today has NO deep-link intent-filter** (`androidApp/src/main/AndroidManifest.xml` — only `MAIN`/`LAUNCHER`). Adding App Links is purely additive.

### iOS wiring — exactly what JetBrains gives you vs. what you write in Swift
iOS has **no intent system**; the OS hands the URL to your app delegate and you forward it. JetBrains provides the `ExternalUriHandler` singleton (above, exposed to Swift via the shared framework). **You write ~3 lines of Swift.** Verbatim from the deep-links page:
```swift
import SharedUI   // your shared framework (FoodRats: `import FoodRatsShared`)

func application(
    _ application: UIApplication,
    open uri: URL,
    options: [UIApplication.OpenURLOptionsKey: Any] = [:]
) -> Bool {
    ExternalUriHandler.shared.onNewUri(uri: uri.absoluteString)
    return true
}
```
Plus the iOS-side registration:
- **Custom URL scheme** → `Info.plist` `CFBundleURLTypes` (the deep-links page: "deep link schemes are declared in `Info.plist` files, in the `CFBundleURLTypes` key"; editable directly or via the Xcode GUI). Delivered via `application(_:open:options:)` as above.
- **Universal links** (`https://…`, the iOS equivalent of App Links) → an **Associated Domains** entitlement (`applinks:foodrats.app`) + an `apple-app-site-association` file hosted on the domain. These arrive via `NSUserActivity`, so you also forward from the continue-activity callback:
  ```swift
  func application(_ application: UIApplication,
                   continue userActivity: NSUserActivity,
                   restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
      if userActivity.activityType == NSUserActivityTypeBrowsingWeb,
         let url = userActivity.webpageURL {
          ExternalUriHandler.shared.onNewUri(uri: url.absoluteString)
          return true
      }
      return false
  }
  ```
  (SwiftUI equivalents: `.onOpenURL { ExternalUriHandler.shared.onNewUri(uri: $0.absoluteString) }` and `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`.)

**FoodRats fit:** `iosApp/iosApp/AppDelegate.swift` already implements `application(_:open:options:)` for `GIDSignIn.handle(url:)` (Google Sign-In) — the deep-link forward is an **additional line in the same method** (return-OR the two handlers). FoodRats is CocoaPods-integrated and runs on a real device today, so the framework export of `ExternalUriHandler` and the Swift bridge are well-trodden ground.

### iOS deep-link maturity — honest status
- iOS deep links are **officially supported and documented as of CMP 1.8.2 + nav-compose 2.9.2** (the version FoodRats pins). They were a real gap through 2024 (issue #5003); that gap is **closed**.
- What JetBrains provides is the `ExternalUriHandler` plumbing + `NavUri`-based navigation; what you own is (a) the iOS registration (Info.plist scheme and/or Associated Domains + AASA file) and (b) the 1–3 lines of Swift forwarding. There is no automatic, zero-Swift iOS deep-link path — and there can't be, because URL delivery is an OS-app-delegate concern.
- Cold-start ordering is handled by the singleton's `cached` replay; you don't need to special-case "link before NavHost exists."

---

## Back stack & state restoration

- Standard AndroidX back stack: `popBackStack()`, `popBackStack(route = …, inclusive = …)`, and `popUpTo<T> { inclusive; saveState }` + `restoreState = true` for tab state preservation. FoodRats uses all of these correctly — the bottom-nav tabs save/restore via `popUpTo<MainTab.Feed> { saveState = true }; launchSingleTop = true; restoreState = true` (`NavGraph.kt:243-247`, `:278-282`), the textbook multi-tab pattern.
- Each back-stack entry is a `LifecycleOwner` (routing page): switching screens moves entries between `RESUMED`/`STARTED`; "navigation is considered finished when the new screen is prepared and active." This is why FoodRats' MVI ViewModels (scoped per entry via `koinViewModel()`) behave correctly across nav.
- State storage now uses `SavedState` (nav 2.9.*). FoodRats reads args only through `toRoute<T>()`, so it inherits restoration for free and is unaffected by the `Bundle`→`SavedState` breaking change.
- `rememberNavController()` works in `commonMain` on all targets (FoodRats calls it in `FoodRatsApp.kt:20` and the inner `MainScaffold`).

---

## iOS / multiplatform wiring (the Swift-side bridge for universal links)

Minimal, concrete steps for FoodRats (all additive — no restructuring):

1. **commonMain:** add `object ExternalUriHandler { … }` (verbatim above) and register its `listener` in `FoodRatsApp` via `DisposableEffect`, calling `rootController.navigate(NavUri(uri))` — or route it through `RootNavViewModel` so the Protected/Public gate runs first.
2. **commonMain routes:** attach `navDeepLink<Route.MealDetail>(basePath = "https://foodrats.app/meal")` (and a `demo://`/`foodrats://` custom scheme if you want non-verified links) to the `composable<Route.MealDetail>`.
3. **Android:** add the `<intent-filter android:autoVerify="true">` block to `AndroidManifest.xml`, set `MainActivity` `launchMode="singleTop"`, and forward `intent.data` via `ExternalUriHandler.onNewUri(...)` in `onCreate`/`onNewIntent` (or call `navController.handleDeepLink(intent)` directly — pick one path, not both).
4. **iOS:** in `AppDelegate.swift`, add `ExternalUriHandler.shared.onNewUri(uri:)` to the existing `application(_:open:options:)` (custom scheme) and add `application(_:continue:restorationHandler:)` (universal links). Add the **Associated Domains** entitlement `applinks:foodrats.app` and host `apple-app-site-association`.
5. **Host both verification files:** `/.well-known/assetlinks.json` (Android) and `/.well-known/apple-app-site-association` (iOS) on `foodrats.app`.

What JetBrains gives you: `ExternalUriHandler`, `NavUri`, the `App(onNavHostReady)` callback, and the entire nav graph in common code. What you write in Swift: the 1–3 forwarding lines. No `NavController` reference ever crosses into Swift for deep links.

---

## Strengths

- **Zero migration for FoodRats** — already on `navigation-compose:2.9.2`, already type-safe (`@Serializable` `sealed interface Route`, `composable<T>`, `toRoute<T>`, typed `popUpTo<T>`), already running on a physical iPhone.
- **One nav graph in commonMain** for Android + iOS; deep-link patterns declared once.
- **Type safety end-to-end:** compile-time-checked args; no string routes, no manual `NavArgument`.
- **iOS deep links are now first-class** at the pinned version — the historical gap is closed.
- **Auth gating is idiomatic and already correct** — `RootNavViewModel` (single source of truth) + the live-back-stack `navigateTopLevel` inclusive-pop is *more* robust than the literal doc example.
- **Familiar AndroidX semantics** — `popUpTo`/`saveState`/`restoreState`/`launchSingleTop` behave exactly as on Android; the whole AndroidX knowledge base and Google's own conditional-nav guide apply directly.

## Weaknesses / current gaps

- **iOS deep links need hand-written Swift** (1–3 lines) + Associated-Domains/AASA setup + Info.plist scheme. JetBrains can't and doesn't fully abstract URL delivery on iOS. (Mitigated: FoodRats already owns `AppDelegate.swift` for Google Sign-In.)
- **`SavedState` breaking change** in nav 2.9.* for anyone reading raw `arguments` — a trap if FoodRats ever drops to the untyped arg API (it currently doesn't).
- **Predictive-back parity** isn't a guaranteed commonMain feature in the 1.8.x notes I reviewed; treat the polished Android-13 predictive-back animation as platform-dependent / not assured cross-platform.
- **No native-iOS transition look** out of the box — screens animate with Compose transitions, not UIKit `UINavigationController` push (you can opt into native nav via `onNavHostReady` + `UINavigationController`, but that's extra work and outside the deep-link path).
- **Cross-host doc redirects** — JetBrains help URLs now 301 to kotlinlang.org; cite the kotlinlang.org canonical URLs.

## Relevance to FoodRats (we already use this — what "doing it right" looks like, zero migration)

FoodRats is the baseline this whole review measures alternatives against, and the verdict is that the baseline is already strong. "Doing it right" from here is **additive, not a rewrite**:

1. **Keep** `navigation-compose:2.9.2`, the `@Serializable sealed interface Route`, `composable<T>`/`toRoute<T>`, and the `RootNavViewModel`-driven gating — these are exactly the documented best practices.
2. **Add a `Route.Public` / `Route.Protected` marker** so auth gating becomes total/compiler-checked and so deep links into protected screens (e.g. `MealDetail`) are intercepted-then-resumed cleanly. This is the single highest-value upgrade and fits the existing sealed interface with no churn.
3. **Add deep links** by: declaring `navDeepLink<Route.MealDetail>(basePath = "https://foodrats.app/meal")` on its `composable`; adding the `ExternalUriHandler` singleton + `DisposableEffect` listener in `FoodRatsApp` (routing through `RootNavViewModel` so the gate runs first); adding the Android `<intent-filter autoVerify>` + `singleTop`; adding the iOS `application(_:open:)` / `application(_:continue:)` forwards in the existing `AppDelegate.swift`; and hosting `assetlinks.json` + `apple-app-site-association`.
4. **Migration cost: zero.** No library swap, no route rewrite, no NavGraph restructuring. The only new code is the deep-link plumbing FoodRats hasn't needed yet and the optional Protected/Public marker.

**Verdict:** FoodRats should stay on `navigation-compose` — it is the officially-supported, type-safe, now-iOS-deep-link-capable baseline, and everything the app wants (universal links + auth gating) is achievable additively at the version already pinned.

---

## Sources (specific docs pages reviewed)

- JetBrains/Kotlin — **Navigation and routing**: https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html — type-safe routes (`@Serializable`, `composable<T>`, `toRoute<T>`), `rememberNavController`, `App(onNavHostReady: suspend (NavController) -> Unit)` + `LaunchedEffect`, `bindToBrowserNavigation()`, back-stack `LifecycleOwner` lifecycle.
- JetBrains/Kotlin — **Deep links**: https://kotlinlang.org/docs/multiplatform/compose-navigation-deep-links.html — `navDeepLink`/`uriPattern`/`basePath`, generated-vs-explicit patterns, the `ExternalUriHandler` singleton (verbatim), `navController.navigate(NavUri(uri))`, the iOS Swift `application(_:open:options:)` → `ExternalUriHandler.shared.onNewUri(...)` bridge, Android intent-filter + iOS `Info.plist CFBundleURLTypes` registration.
- JetBrains/Kotlin — **Navigation in Compose (overview)**: https://kotlinlang.org/docs/multiplatform/compose-navigation.html — NavHost/NavController/NavGraph basics, back-gesture handling Android/iOS, alternatives.
- JetBrains/Kotlin — **What's new in CMP 1.8.x**: https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html — "deep linking on iOS … with navigation-compose 2.9.2" support statement, `Bundle`→`SavedState` breaking change, nav `2.9.0-beta03`/`2.9.0` basis, `window.bindToNavigation()` for web.
- AndroidX — **Type-safe navigation**: https://developer.android.com/guide/navigation/design/type-safety — `@Serializable` routes, `composable<T>()`, `toRoute<T>()`, typed `navigate(T)`, "Requires Navigation 2.8.0+".
- AndroidX — **Conditional navigation / require login**: https://developer.android.com/guide/navigation/use-graph/conditional — observe auth state in a shared ViewModel, redirect with `NavOptions.setPopUpTo(start, inclusive = true)`, `SavedStateHandle` login-result tracking, "auth logic held within `UserViewModel`."
- AndroidX — **Deep links**: https://developer.android.com/guide/navigation/design/deep-link — `<intent-filter>` (`scheme`/`host`/`pathPrefix`), `<nav-graph>` auto-generation, App Links `android:autoVerify="true"` + `assetlinks.json`, `navController.handleDeepLink(intent)` / `onNewIntent`, implicit vs explicit vs App Links.
- GitHub — **JetBrains/compose-multiplatform#5003 "Deep Linking Support - iOS"**: https://github.com/JetBrains/compose-multiplatform/issues/5003 — opened 2024-06-21, assigned MatkovIvan, now closed; confirms iOS deep links were a tracked gap, resolved by the 2.9.x line.
- Community corroboration (architecture only, not cited for API specifics) — Pink Room "Handling Deep Links in Compose Multiplatform" (`DeepLinkHelper`/DI/`onNewIntent` flow): https://medium.com/pink-room-club/handling-deep-links-in-compose-multiplatform-87b269a8f1a1

### FoodRats files referenced
- `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/navigation/Route.kt`
- `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/navigation/NavGraph.kt`
- `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/root/FoodRatsApp.kt`
- `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/root/RootNavViewModel.kt`
- `androidApp/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml` (`navCompose = "2.9.2"`)
