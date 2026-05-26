# Voyager (library) — KMP Navigation Review

**One-liner:** A pragmatic, screen-as-class Compose Multiplatform navigator with great DI/ScreenModel ergonomics, but no built-in deep-linking, no built-in auth gating, and a stale release cadence (no stable release since Dec 2023).
**Repo:** https://github.com/adrielcafe/voyager  **Docs:** https://voyager.adriel.cafe/

## What it is
Voyager (by Adriel Café) is a navigation library "built for, and seamlessly integrated with, Jetpack Compose" (`README.md`). A screen is a *class* implementing the `Screen` interface with a `Content()` composable; a `Navigator` holds a stack of those instances. It is deliberately minimal/"pragmatic" — no XML, no code-gen, no route-string DSL. It ships a family of modules (`docs/setup.md`):

- `voyager-navigator` (core stack + `Navigator`), `voyager-screenmodel` (ViewModel analog), `voyager-tab-navigator`, `voyager-bottom-sheet-navigator`, `voyager-transitions`
- DI bridges: `voyager-koin`, `voyager-kodein`, `voyager-hilt` (Android-only)
- `voyager-lifecycle-kmp`, `voyager-livedata` (Android), `voyager-rxjava` (Android/Desktop)

Supported platforms: Android, iOS, Desktop (JVM), Web (JS/wasm), macOS Native (`README.md`, `docs/setup.md`).

**Maintenance status (be honest):** the latest **stable** release is **1.0.1 (Dec 19 2023)**. The `1.1.0` line never left beta (last `1.1.0-beta03`, Oct 2023); `2.0.0-alpha01` (Aug 17 2024, Kotlin 2.2 / CMP 1.8) is the only newer artifact and is alpha. The repo still receives commits (HEAD `d7b826b`, "Fixed samples that have compilation errors", **Feb 18 2026**), so it is not abandoned — but a team adopting it today is choosing between a 2-year-old stable and an alpha. (Source: GitHub releases page.)

## Navigation approach & route definitions
Screens are classes; args are plain constructor params (`docs/navigation/index.md`):

```kotlin
class PostListScreen : Screen {
    @Composable override fun Content() { /* ... */ }
}

data class PostDetailsScreen(val postId: Long) : Screen {   // args = constructor params
    @Composable override fun Content() { /* ... */ }
}
```

Entry point — set an initial screen; navigate via `LocalNavigator`:

```kotlin
setContent { Navigator(HomeScreen) }

// inside a Screen's Content():
val navigator = LocalNavigator.currentOrThrow
navigator.push(PostDetailsScreen(post.id))   // also: navigator push X / navigator += X
```

Stack ops (`docs/stack-api.md`), backed by a `SnapshotStateStack`: `push`, `replace`, `replaceAll`, `pop`, `popAll`, `popUntil { }`, plus `canPop` and `lastEvent` (`Push`/`Replace`/`Pop`/`Idle`) for choosing transitions.

**Typed args caveat (load-bearing for FoodRats).** Args are ordinary Kotlin params — there is **no `@Serializable`-route concept** like androidx-nav's typed routes. Type-safety comes from the constructor, but state restoration is bolted on via Java `Serializable`/`Parcelable`, *not* kotlinx-serialization (see restoration section). Voyager's "type-safe multi-module navigation" is a different mechanism — a DI-style registry, not serialized routes:

```kotlin
// navigation module (shared by features) — docs/navigation/multi-module-navigation.md
sealed class SharedScreen : ScreenProvider {
    object PostList : SharedScreen()
    data class PostDetails(val id: String) : SharedScreen()
}
// feature module registers the concrete Screen for each provider:
val featurePostsScreenModule = screenModule {
    register<SharedScreen.PostList> { ListScreen() }
    register<SharedScreen.PostDetails> { provider -> DetailsScreen(id = provider.id) }
}
// Application.onCreate: ScreenRegistry { featurePostsScreenModule() }
// caller (in another feature, no direct dep on the screen class):
val postDetails = rememberScreen(SharedScreen.PostDetails(id = postId))
navigator.push(postDetails)
```
This `ScreenProvider`/`ScreenRegistry` indirection is genuinely useful for FoodRats' "features can't depend on other features" rule (a feature pushes another feature's screen via a shared `ScreenProvider` without importing its `Screen` class) — but it's a *runtime* registry resolved in `Application.onCreate`, not a compile-time port, and it leans on a global singleton.

## Auth gating (public vs protected routes, redirect-to-login) — idiomatic pattern
**There is no built-in route classification or auth-guard API.** No doc page covers it, and a repo-wide grep of the samples for `auth|login|guard|gate|signin` matched only concurrency helpers and Hilt internals — i.e. **none of the official samples demonstrate auth gating.** The idiomatic approaches are all hand-rolled:

1. **Conditional initial screen** — pick the root at composition time:
   ```kotlin
   setContent {
       val start = if (session.isAuthenticated) HomeScreen else SignInScreen
       Navigator(start)
   }
   ```
2. **Gate inside a Screen's `Content()`** — observe session and `replaceAll` the stack:
   ```kotlin
   class HomeScreen : Screen {
       @Composable override fun Content() {
           val navigator = LocalNavigator.currentOrThrow
           val authed by sessionModel.isAuthenticated.collectAsState()
           LaunchedEffect(authed) {
               if (!authed) navigator.replaceAll(SignInScreen)   // boot back to login
           }
           if (!authed) return
           // protected content…
       }
   }
   ```
   Both are constructed from primitives Voyager *does* give you (`replaceAll`, `LocalNavigator`, a `ScreenModel` holding session state). There is no equivalent of a central "this route requires auth → redirect" table; each protected screen (or a shared wrapper composable) must implement the check. Compared to androidx-nav, you also lose declarative route metadata, so "is this destination protected?" lives in code, not in a route definition you can enumerate.

## Deep links / universal links / app links (Android + iOS) — what library provides vs per-platform
**Voyager provides nothing here.** The deep-links doc is explicit (`docs/deep-links.md`, top of file):

> "Currently Voyager does not provided a built in solution to handle Deeplink and URIs. see #149 and #382"

The only library-side mechanism is initializing the `Navigator` with a **pre-built stack** so the deep-linked screen sits on top and Back walks up the synthetic chain:

```kotlin
val postId = getPostIdFromIntent()      // YOU parse the URL/intent
setContent {
    Navigator(
        HomeScreen,
        PostListScreen(),
        PostDetailsScreen(postId)        // visible screen; pop() returns to PostList → Home
    )
}
```

Everything else is per-platform and entirely on you:
- **Android (App Links/deep links):** declare `<intent-filter>` with `<data android:scheme/host/path>` + `android:autoVerify` in `AndroidManifest.xml`, then read `intent.data` in the Activity and translate it into the initial `Navigator(...)` stack (or `navigator.push(...)` on a warm start). Voyager has no `NavDeepLink`, no `<deepLink>` graph element, no URI matcher.
- **iOS (Universal Links / custom scheme):** you wire `application(_:open:options:)` / `continue userActivity:` in Swift `AppDelegate`, hold the parsed target somewhere (e.g. a shared state object), and feed it into the `Navigator` start screen or push into it. Issue **#382 ("iOS URL scheme → Voyager Navigator") is OPEN with no maintainer answer**, labeled `deeplink` + `docs` — i.e. the maintainers acknowledge it's an unsolved documentation/feature gap. There is no library bridge from a UIKit URL callback to the Compose `Navigator`.

**Bottom line vs FoodRats' goal #1:** Voyager does **not** help you map a URL → screen stack on either platform. You'd build the entire URL parser + per-platform intent/universal-link plumbing yourself, then convert the result into a manual `Navigator(...)` stack. (For comparison, androidx-nav's typed `composable<Route.X>(deepLinks = …)` does provide URL-pattern matching out of the box.)

## Back stack & state restoration (note any process-death caveats)
- **Back handling:** automatic; override with `onBackPressed = { current -> true/false }` or disable with `null` (`docs/back-press.md`). No built-in predictive-back and **no iOS swipe-back** — the FAQ (`docs/faq.md`) says both are deliberately left to copy-paste community snippets (#144, #223). Inter-screen **result passing** is likewise not built in (FAQ → #128).
- **State restoration — the known weak spot.** On Android the `Screen` interface literally **extends `java.io.Serializable`** (verified in source, `voyager-core/.../screen/Screen.kt` androidMain actual):
  ```kotlin
  public actual interface Screen : Serializable { … }
  ```
  Restoration after Activity recreation / process death therefore works **only if every constructor param and every property is Java-`Serializable` (or you opt into Parcelable)** (`docs/state-restoration.md`). Non-serializable params (a `Context`, a service, a raw `Parcelable`) silently break restoration. This is the structural downside of class-based screens vs serialized routes:
  - DO: `data class ValidScreen(val userId: UUID, val post: Post /* : Serializable */) : Screen`
  - DON'T: hold `val postService = PostService()` as a property, or inject via property delegate — inject **inside** `Content()` with `get<T>()` instead.
  - Parcelable path: implement `Parcelable` + `@Parcelize`, optionally enforce with a custom `interface ParcelableScreen : Screen, Parcelable` and `LocalNavigatorSaver provides parcelableNavigatorSaver()`.
  - **KMP friction:** because `Serializable` is JVM-only, sharing screen-param models in `commonMain` requires the `expect interface JavaSerializable` / `actual typealias JavaSerializable = java.io.Serializable` dance (`docs/state-restoration.md`). This is exactly the kind of platform leak FoodRats keeps out of `:core:domain`. Note it is **not** kotlinx-serialization, so FoodRats' existing `@Serializable` VOs wouldn't satisfy the Android saver by themselves.
  - Each screen needs a stable `key` for subtree state-saving; use `uniqueScreenKey` or override `key`. Screens reused in one Navigator, or anonymous/local classes, **must** set their own key (`docs/state-restoration.md`).
- **ScreenModel lifecycle:** created via `rememberScreenModel { … }`; `screenModelScope` is a `SupervisorJob + PlatformMainDispatcher` cancelled in `onDispose()` (verified `voyager-screenmodel/.../ScreenModel.kt`). `StateScreenModel<S>` gives a `MutableStateFlow`/`StateFlow` pair. `Navigator.rememberNavigatorScreenModel { }` shares one across all screens in a Navigator, disposed when the Navigator leaves composition. Lifecycle callbacks (`LifecycleEffectOnce`, `ScreenLifecycleProvider`) are marked **Experimental API** (`docs/lifecycle.md`).

## iOS / multiplatform wiring
The iOS entry point is pure Compose — no native nav integration (`samples/multiplatform/src/iosMain/kotlin/MainViewController.kt`):
```kotlin
fun MainViewController() = ComposeUIViewController { SampleApplication() }   // SampleApplication() => Navigator(...)
```
The Navigator and all screens live in `commonMain`; the Swift side just hosts the Compose view. Consequences for iOS:
- No `UINavigationController` under the hood → **no native swipe-back gesture** and no native large-title/back-bar behavior unless you add a community shim (FAQ #144).
- Deep links / universal links: the UIKit `AppDelegate` callbacks have **no Voyager bridge** (issue #382). You hold parsed state in Swift→KMP shared state and drive `Navigator` start/push manually.
- Otherwise links/runs fine in CMP (the multiplatform sample targets Android/iOS/Desktop/JS/wasm/macOS from one `commonMain` `Navigator`).

## Strengths
- **Minimal, fast to learn:** screens are classes with `Content()`; navigation is `push/pop/replace` on a `SnapshotStateStack`. Very low ceremony.
- **First-class ScreenModel + DI:** clean `rememberScreenModel`, `screenModelScope`, navigator-scoped models, and official Koin/Kodein/Hilt bridges — a good fit for FoodRats' MVI ViewModels (could replace `MviViewModel` host or coexist).
- **Feature-module decoupling via `ScreenProvider`/`ScreenRegistry`:** lets one feature navigate to another's screen without importing its class — aligns with FoodRats' "features can't depend on other features."
- **Genuinely multiplatform** including web/macOS; tab + bottom-sheet navigators are built in.
- **Compose-native state model** (SnapshotStateList) → recomposition just works.

## Weaknesses
- **No deep-link / universal-link support at all** — the single biggest gap for FoodRats (goal #1). Issue #382 (iOS) open and unanswered; #149 too.
- **No auth-gating primitive** — must hand-roll conditional-initial-screen or per-screen `replaceAll` guards (goal #2); no enumerable route metadata.
- **State-restoration is the class-based-screen Achilles' heel:** relies on Java `Serializable`/Parcelable (not kotlinx-serialization), needs the `expect/actual JavaSerializable` hack in KMP, and silently breaks on non-serializable params under Android process death.
- **Stale releases:** no stable since 1.0.1 (Dec 2023); 1.1.0 stuck in beta; 2.0.0 only alpha. Adoption risk for a shipping app.
- **No built-in result-passing, predictive back, or iOS swipe-back** — all punted to copy-paste community snippets (FAQ).
- **Runtime registry over compile-time wiring:** `ScreenRegistry` is a global singleton resolved at `Application.onCreate`; mis-registration fails at runtime, not compile time.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)
- **Goal 1 (deep / universal / app links): NOT supported.** Voyager gives strictly less than what FoodRats already has. androidx-nav offers URL-pattern deep links on typed routes today; Voyager would mean writing the URL parser + Android intent-filter handling + iOS universal-link `AppDelegate` plumbing **and** the URL→`Navigator(...)`-stack translation entirely by hand, with an open unanswered issue on the iOS half. This alone is close to disqualifying for FoodRats' top priority.
- **Goal 2 (auth-gated routing): supported only by hand-rolled convention.** Achievable (`replaceAll(SignInScreen)` from a session-observing gate), but no library affordance and no route-level "protected" metadata — you build the policy yourself, same as you would on androidx-nav. No advantage here.
- **Migration cost from androidx-nav: high and partly backwards.** FoodRats' `Route` is a `sealed interface` of `@Serializable data object`s with `composable<Route.X> {}` and typed `popUpTo<Route.SignIn>()`. Moving to Voyager means: (a) rewrite every destination as a `Screen` class with a `Content()`, (b) replace the typed `Route`/NavGraph with `Navigator` + `LocalNavigator` calls (and a `ScreenRegistry` for cross-feature pushes), (c) **re-solve state restoration** — the existing `@Serializable` kotlinx routes don't satisfy Voyager's Java-`Serializable` Android saver, so you'd add the `expect/actual JavaSerializable` shim or go Parcelable, (d) re-wire iOS (today FoodRats uses CMP nav-compose `2.9.2` linking on device — Voyager also runs there, but you re-do any deep-link/back work), and (e) accept the stale-release risk. The one real upside for FoodRats is the `ScreenProvider`/`ScreenRegistry` cross-module pattern and the polished `ScreenModel`/Koin integration — neither outweighs losing built-in deep links.
- **Verdict:** **Poor fit for FoodRats.** It actively regresses the #1 requirement (cross-platform deep/universal links) and offers no edge on the #2 (auth gating) while imposing a non-trivial, partly-backwards migration and a Java-`Serializable` restoration tax. Pleasant DX, wrong tool for these two goals.

## Sources (specific docs pages / sample files reviewed)
- `docs/deep-links.md` — explicit "no built-in deep-link solution" + #149/#382; multi-screen `Navigator(...)` init workaround.
- `docs/state-restoration.md` — Java `Serializable`/Parcelable requirement; `expect/actual JavaSerializable`; `parcelableNavigatorSaver`; `key`/`uniqueScreenKey`.
- `docs/navigation/index.md` — `Screen` interface, `Navigator`, `LocalNavigator`, `CurrentScreen()`.
- `docs/navigation/multi-module-navigation.md` + `samples/multi-module/**` (`SharedScreen.kt`, `ScreenModule.kt`, `HomeScreen.kt`, `SampleApp.kt`) — `ScreenProvider` / `ScreenRegistry` / `rememberScreen`.
- `docs/stack-api.md` — `SnapshotStateStack`, push/pop/replace/popUntil, `StackEvent`.
- `docs/back-press.md` — `onBackPressed`.
- `docs/faq.md` — no iOS swipe-back, no predictive back, no result passing (community snippets only).
- `docs/lifecycle.md` — `LifecycleEffectOnce`, `ScreenLifecycleProvider` (Experimental).
- `docs/setup.md` — modules + platforms; version `1.1.0-beta02`.
- Source: `voyager-core/.../screen/Screen.kt` (`actual interface Screen : Serializable`); `voyager-screenmodel/.../ScreenModel.kt` (`screenModelScope`, `rememberScreenModel`, `StateScreenModel`); `rememberNavigatorScreenModel` + `NavigatorScreenModelDisposer`.
- `samples/multiplatform/src/iosMain/kotlin/MainViewController.kt` — `ComposeUIViewController { SampleApplication() }`; `Application.kt`; `BasicNavigationScreen.kt`.
- GitHub: releases page (latest stable 1.0.1 / Dec 2023; 1.1.0 beta-only; 2.0.0-alpha01 Aug 2024); issue #382 (iOS deep link, OPEN, no maintainer answer); repo HEAD `d7b826b` Feb 18 2026.
