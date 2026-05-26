# Appyx (library, Bumble) — KMP Navigation Review

**One-liner:** A tree-based, model-driven, gesture-capable Compose navigation library where navigation is a tree of `Node`s (each owning a swappable "AppyxComponent" like a back stack), genuinely multiplatform (Android/iOS/Desktop/Web) — but effectively frozen at v2.0.1 (mid-2024) with deep linking and auth-gating left as per-platform/app-code patterns rather than library-provided machinery.

**Repo:** https://github.com/bumble-tech/appyx (branch `2.x` is the multiplatform line; `main`/HEAD is the legacy `1.7.1` Android-only line)
**Docs:** https://bumble-tech.github.io/appyx/ — *the 2.x source-of-truth markdown lives in-repo under `documentation/` on the `2.x` branch; the public docs site mixes 1.x and 2.x.*

> **Version note (important):** Two parallel lines exist. **Appyx 1.x** (`com.bumble.appyx:core`, Android-only, last doc'd release 1.3.0 on the site, repo tag `1.7.1`) is *not* the candidate. The KMP candidate is **Appyx 2.x** — artifact group `com.bumble.appyx:appyx-navigation` et al. Latest on Maven Central is **`2.0.1`, published ~2 years ago** (the `2.0.0` changelog entry is dated **19 Apr 2024**; last `2.x` branch commit **16 Jul 2024**). Built against Kotlin 2.0.20 / Compose BOM 2025.04.01 in the repo, but no releases since.

## What it is

Not a route-table navigator. Appyx models your whole app as a **tree of `Node`s**. A `Node<NavTarget>` (`ParentNode` in 1.x) holds an **`AppyxComponent`** — a navigation *model* such as `BackStack`, `Spotlight` (pager), `Cards`, `Tiles` — that owns the list of children and their transition state. A `LeafNode` has no children. The component's observable state drives Compose; operations on the component (`push`, `pop`, `replace`, `activate`) mutate state and the tree re-renders with transitions. The "screen" concept is deliberately absent: there's only a viewport, and a node fills it (`documentation/navigation/concepts/model-driven-navigation.md`). This makes it strong for nested/embedded navigation and custom gesture transitions, at the cost of being conceptually heavier than a flat route list.

Three artifact groups: **Appyx Navigation** (the tree/Node framework), **Appyx Components** (prebuilt `backstack`, `spotlight` — stable; `cards`, `tiles`, etc. — experimental), **Appyx Interactions** (build your own gesture/transition components + KSP).

## Navigation approach & route definitions

A node declares its route catalog as a **sealed class of `@Parcelize` `NavTarget`s** (multiplatform `@Parcelize`/`Parcelable` via `com.bumble.appyx.utils.multiplatform.*` typealiases — `kotlinx.parcelize` on Android, platform-backed elsewhere). Routes **can carry payload** (data class targets). The node maps target → child via `buildChildNode`. From `documentation/navigation/quick-start.md` and the demo `RootNode.kt`:

```kotlin
class RootNode(
    nodeContext: NodeContext,
    private val backStack: BackStack<NavTarget> = BackStack(
        model = BackStackModel(
            initialTargets = listOf(NavTarget.LoggedOut),
            savedStateMap = nodeContext.savedStateMap,   // <- state restoration hook
        ),
        visualisation = { BackStackFader(it) }           // swap for BackStackSlider/Parallax/3D
    ),
) : Node<NavTarget>(nodeContext = nodeContext, appyxComponent = backStack) {

    sealed class NavTarget : Parcelable {
        @Parcelize object LoggedOut : NavTarget()
        @Parcelize data class Main(val user: User) : NavTarget()   // routes carry typed args
    }

    override fun buildChildNode(navTarget: NavTarget, nodeContext: NodeContext): Node<*> =
        when (navTarget) {
            is NavTarget.LoggedOut -> LoggedOutNode(nodeContext, onLogin = { onLogin(it) })
            is NavTarget.Main      -> MainNode(nodeContext, user = navTarget.user,
                                               onLogout = { backStack.replace(NavTarget.LoggedOut) })
        }

    @Composable
    override fun Content(modifier: Modifier) {
        AppyxNavigationContainer(appyxComponent = backStack, modifier = Modifier)
    }
}
```

Navigation = component operations: `backStack.push(NavTarget.X)`, `backStack.replace(...)`, `backStack.pop()`; `spotlight.activate(index)`. Two navigation styles are documented:
- **Implicit** — a node mutates its own component (the common case).
- **Explicit** — chain `attachChild { … }` / `waitForChildAttached<T>()` from the root down to drive to a deep node programmatically (`documentation/navigation/concepts/explicit-navigation.md`). Deep linking and cross-tree jumps build on this.

**vs FoodRats today:** FoodRats' `Route` sealed interface of `@Serializable data object`s + `composable<Route.X>{}` maps conceptually to Appyx's `NavTarget` sealed class + `buildChildNode`. But Appyx uses `@Parcelize` (not kotlinx-serialization) for nav keys, and there is **no flat `NavHost`** — navigation is distributed across the node tree.

## Auth gating (public vs protected routes, redirect-to-login) — idiomatic pattern

This is a **first-class, demonstrated pattern** in Appyx and arguably its best fit for FoodRats goal #2. There is no "route metadata = protected" table; instead a **parent node holds auth state as the navigation model** and swaps the authed/unauthed subtree. The official demo's `RootNode` (`demos/appyx-navigation/mainApp/.../node/root/RootNode.kt`) *is* the auth gate — its two `NavTarget`s are `LoggedOut` and `Main(user: User)`:

```kotlin
fun onLogin(user: User): RootNode {
    backStack.replace(NavTarget.Main(user))   // login swaps the whole subtree to authed
    return this
}
// logout, from MainNode's callback:  onLogout = { backStack.replace(NavTarget.LoggedOut) }
```

Because `NavTarget.Main` **carries the `User`**, the authed subtree is *unrepresentable without a user* — illegal "authed but no user" states can't be constructed. The redirect-to-login-then-resume case uses the explicit-navigation `waitForChildAttached` primitive (`documentation/navigation/concepts/explicit-navigation.md`, "Wait for login"):

```kotlin
// RootNode:
suspend fun waitForLoggedIn(): LoggedInNode = waitForChildAttached<LoggedInNode>()

// Navigator: a deep link to a protected screen suspends until the user authenticates,
// then continues — no manual "are we logged in?" branching at each call site:
fun navigateToProfile() = lifecycleScope.launch {
    rootNode.waitForLoggedIn().attachMain().attachProfile()
}
```

This is genuinely nice: "navigate to a protected destination" becomes a suspend chain that *blocks on the login node appearing*. It maps cleanly onto FoodRats' SignIn-gated `Route`s and the `Session.activeCrewId` requirement. Trade-off: it's a *convention you assemble*, not a declarative `requiresAuth = true` flag — you encode the policy in the node tree shape.

## Deep links / universal links / app links (Android + iOS) — what library provides vs per-platform

**Honest take: Appyx provides almost nothing automatic here — it's a documented hand-rolled pattern, but it is demonstrated working on BOTH Android and iOS in the same sample.** There is no URL-pattern DSL, no path-param parsing, no `<deepLink>`-style matcher. The library gives you (a) `NodeReadyObserver` to grab the root node once the tree is built, and (b) the explicit-navigation primitives to drive to any node. You write the URL→navigation mapping yourself, per platform.

**Android** (`demos/appyx-navigation/mainApp/.../MainActivity.kt`): you still register `intent-filter`s in the manifest yourself (App Links). The Activity parses the `Intent`/`Uri` and calls shared navigation methods:

```kotlin
RootNode(
    nodeContext = it,
    // when launched from a link, skip the dummy login so we exercise "wait for login":
    allowDummyLogin = (intent?.data == null),
    plugins = listOf(navigator, NodeReadyObserver { handleDeepLinks(intent?.data) })
)
// ...
private fun handleDeepLinks(uri: Uri?) {
    if (intent?.action == Intent.ACTION_VIEW) when {
        // adb shell am start -a android.intent.action.VIEW -d "appyx://randomcake"
        uri?.host == "randomcake" -> navigator.goToARandomCakeWithDummyUser()
        // ...
    }
}
```

**iOS** (`demos/appyx-navigation/mainApp/src/iosMain/kotlin/main.ios.kt`): symmetric — a shared-Kotlin `handleDeepLinks(url: NSURL)` parses the URL and calls the **same `Navigator`**. The Swift `AppDelegate`/SceneDelegate (or `openURL`) forwards the URL into this function; you still configure Universal Links / `CFBundleURLSchemes` in Xcode yourself.

```kotlin
fun handleDeepLinks(url: NSURL) {
    if (url.scheme == "appyx") when (url.host) {
        // xcrun simctl openurl booted 'appyx://randomcake'
        "randomcake"      -> navigator.goToARandomCakeWithDummyUser()
        "randomcake-wait" -> navigator.goToARandomCake()   // exercises waitForMainAttached()
    }
}
```

The deep-linking docs page (`documentation/navigation/features/deep-linking.md`) only shows the Android snippet and is thin — but the **sample app proves the cross-platform story**: the *parsing* is per-platform (Intent vs NSURL, both small), and the *navigation* is shared Kotlin through one `Navigator`. **Universal Links / App Links association files, intent-filters, and Xcode URL types are 100% your responsibility** — Appyx does not generate or validate them. Maturity of deep linking *specifically* is low-as-a-feature but the architecture makes it tractable and the "deep-link into an auth-gated screen, wait for login, then resume" flow is explicitly demonstrated end-to-end.

## Back stack & state restoration

- **Back stack model** (`documentation/components/backstack.md`): a `BackStack` is never empty; exactly one `active` element, the rest `stashed` (kept alive off-screen), plus `created`/`destroyed` transient sets. `State<InteractionTarget>` is `@Parcelize`. Configurable back-press strategies.
- **State restoration across process death:** each `BackStackModel`/`SpotlightModel` takes `savedStateMap = nodeContext.savedStateMap`; because `NavTarget`s are `@Parcelize`, the whole tree's nav state is saved/restored. `documentation/releases/changelog.md` (2.0.0) lists explicit fixes here (`#671` UI state saving, `#694` appyxComponent state saving).
- **Retained instances across config change:** `RetainedInstanceStore` via `nodeContext.getRetainedInstance(factory, disposer)` / `getRetainedDisposable` (`documentation/navigation/features/surviving-configuration-changes.md`) — Android-centric (Activity recreation), backed by a singleton.
- **Back handling** is automatic on Android (`NodeActivity`); on iOS/Desktop/Web you feed a `Flow<Unit>`/key events into the NodeHost (see below) — i.e. **iOS back is manual** (you wire a back button or gesture to a `Channel`).
- **Signature capability — gestures & transitions:** this is Appyx's actual differentiator. Visualisations are swappable one-liners (`BackStackFader` → `BackStackSlider`/`BackStackParallax`/`BackStack3D`), and Appyx Interactions lets you build **drag-gesture-driven** navigation (the Tinder-like card stack origin). Far beyond androidx-nav's fixed enter/exit transitions.

## iOS / multiplatform wiring & maturity

**Targets are really published.** `appyx-navigation/common/build.gradle.kts` declares `androidTarget`, `jvm("desktop")`, `js(IR)`, `wasmJs`, **`iosX64()`, `iosArm64()`, `iosSimulatorArm64()`**, all on Compose Multiplatform (`compose.runtime/foundation/material`). Maven coordinates per `documentation/releases/downloads.md`: common `com.bumble.appyx:appyx-navigation:$v` (multiplatform), or platform artifacts incl. `appyx-navigation-iosarm64`, `-iosx64`, `-iossimulatorarm64` (same for `backstack`, `spotlight`, `appyx-interactions`, `utils-material3`). No CocoaPods/Swift-package wrapper needed — it's a normal Kotlin framework you link via your existing KMP setup.

**iOS host wiring** (`appyx-navigation/common/src/iosMain/.../IosNodeHost.kt` + demo `main.ios.kt`): you plug the root node into a `ComposeUIViewController` via `IosNodeHost`, passing a `MainIntegrationPoint` and a `Flow<Unit>` of back events:

```kotlin
private val integrationPoint = MainIntegrationPoint()
fun MainViewController() = ComposeUIViewController {
    AppyxSampleAppTheme {
        IosNodeHost(modifier = Modifier, onBackPressedEvents = flowOf(), integrationPoint = integrationPoint) { nodeContext ->
            RootNode(nodeContext = nodeContext, plugins = listOf(navigator))
        }
    }
}.also { integrationPoint.setViewController(it) }
```

**Maturity, honestly:**
- iOS is a **real, demonstrated target** (the 2.x sample runs on iOS sim; changelog `#670` "Fixes ios lifecycle", `#697` "Fix missing resources in iOS sample app").
- But it carries **`2.0.x` rough edges**: iOS back navigation is manual (no system swipe-back integration out of the box — you wire a `BackButton`/channel), `IosNodeHost` uses `@OptIn(ExperimentalForeignApi)`, and lifecycle is a custom `LifecycleListener` rather than a platform-native one.
- **Maintenance is the headline risk:** latest release `2.0.1` is **~2 years old (mid-2024)**; the `2.x` branch's last commit is 16 Jul 2024. 2.x is described in docs as **"experimental."** No releases tracking Compose Multiplatform 1.7/1.8, the newer CMP lifecycle/back-gesture APIs, or recent Kotlin. Adopting it today means betting on an effectively dormant library and likely maintaining a fork.

## Strengths

- **Best-in-class auth-gating ergonomics for our case:** authed/unauthed-as-tree-subtrees + payload-carrying `NavTarget` makes "authed without a user/crew" unrepresentable; `waitForChildAttached` gives "go to protected screen, wait for login, resume" for free. Directly serves FoodRats goal #2.
- **Genuine Compose-MP multiplatform** with real published iOS artifacts and a working iOS sample — not Android-with-an-iOS-fig-leaf.
- **State preservation across process death is built in** via `savedStateMap` + `@Parcelize` nav keys (no `@Serializable` route plumbing needed).
- **Signature gesture/transition system** (parallax/3D/drag-driven) far exceeds androidx-nav — relevant if FoodRats ever wants a gestural meal browser.
- **Composable, nested navigation** is first-class (a node tree), better than flat NavHost for embedded flows.
- Production pedigree (Bumble/Badoo at scale), DI/ViewModel/RIBs/Rx integrations, testing artifacts (junit4/5, UI).

## Weaknesses

- **Effectively unmaintained:** `2.0.1` (~2 yrs old), branch dormant since Jul 2024, docs label 2.x "experimental." This is the dominant risk for a new long-lived dependency.
- **Deep linking is not a library feature** — no URL/path matcher, no param binding, no Universal-Links/App-Links scaffolding. You hand-write per-platform URL parsing (Intent + NSURL) and *all* the manifest/Xcode association config. Doable and demonstrated, but it's app code, not framework.
- **Highest conceptual + migration cost of the candidates:** there is no `NavHost`/route-table; you rewrite navigation as a `Node` tree with `buildChildNode`, `AppyxComponent` per parent, `NodeHost` per platform. Mental-model shift for the whole team.
- **`@Parcelize` for nav keys**, not kotlinx-serialization — diverges from FoodRats' existing `@Serializable` `Route` convention and pulls in `kotlin-parcelize`.
- **iOS back navigation is manual** (no native swipe-back); lifecycle/back rely on custom multiplatform shims with experimental opt-ins.
- Smaller community / fewer current Q&A resources than androidx/JetBrains nav or Voyager/Decompose.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)

- **Goal #1 (deep links / universal links / app links, both platforms):** *Partially.* The runtime navigation is cross-platform and the "link → deep node, possibly behind auth" flow is demonstrated on Android **and** iOS via a shared `Navigator`. **But** Appyx provides zero URL-matching or platform association tooling — you'd build URL parsing + App Links/Universal Links config yourself (same per-platform OS work you'd do with any library, minus a matcher DSL). Net: enables it, doesn't accelerate it.
- **Goal #2 (auth-gated routing, redirect-to-login):** *Strongly yes* — arguably the standout among candidates. The authed/unauthed-subtree pattern + `waitForChildAttached` is exactly redirect-to-login-then-resume, and payload-carrying `NavTarget` enforces "no authed screen without a User/Crew" structurally, aligning with FoodRats' `Session` model.
- **Migration cost from androidx/JetBrains nav:** **High.** FoodRats would discard the `NavGraph` + `composable<Route.X>{}` + `popUpTo<Route.SignIn>()` model and re-express every destination as a `Node`/`NavTarget`/`buildChildNode`, install `NodeHost`/`NodeActivity` on Android and `IosNodeHost` in `MainViewController`, and switch nav keys from `@Serializable` to `@Parcelize`. The Koin-per-node story is supported but is new wiring. The bigger cost is conceptual (tree-of-nodes vs flat routes), not lines of code.
- **Verdict:** Adopt **only** if FoodRats specifically wants Appyx's gestural/nested-navigation model or its auth-gating ergonomics enough to accept (a) a dormant, "experimental"-labelled, ~2-yr-stale dependency you may have to fork, and (b) a high-effort rewrite away from its current `@Serializable` route model — while still hand-rolling deep-link URL parsing per platform. For FoodRats' stated priorities (cross-platform deep links + auth gating on a maintained stack), the **maintenance risk outweighs the genuinely excellent auth-gating story**; prefer it over keeping androidx-nav only if a more actively maintained alternative (Decompose, Voyager, or JetBrains nav itself) doesn't meet the bar.

## Sources (specific docs pages / sample files reviewed)

In-repo on branch `2.x` (`git clone --depth 1 --branch 2.x https://github.com/bumble-tech/appyx`; `library.version=2.0.1`, last commit 2024-07-16):
- `documentation/navigation/index.md` — overview, "type-safe navigation from code", concept/feature index
- `documentation/navigation/quick-start.md` — `NavTarget` sealed class, `buildChildNode`, `BackStack` + `savedStateMap`, push/pop/replace, visualisation swap
- `documentation/navigation/concepts/model-driven-navigation.md` — viewport-not-screen, AppyxComponent model
- `documentation/navigation/concepts/explicit-navigation.md` — `attachChild`/`waitForChildAttached`, **"Wait for login"** auth pattern, `Navigator` + `NodeReadyObserver`
- `documentation/navigation/features/deep-linking.md` — (Android-only) deep-link-on-top-of-explicit-nav snippet
- `documentation/navigation/features/surviving-configuration-changes.md` — `RetainedInstanceStore`
- `documentation/navigation/integrations/compose-navigation.md` — coexistence/gradual migration with Jetpack Compose Navigation
- `documentation/navigation/multiplatform.md` — `NodeHost` per platform (Android/Desktop/Web/**iOS** `IosNodeHost`), back handling, supported-platform badges incl. iOS
- `documentation/components/index.md`, `documentation/components/backstack.md` — BackStack/Spotlight, `State` model, visualisations, restoration fixes
- `documentation/releases/downloads.md` — full Maven coordinates incl. `*-iosarm64/-iosx64/-iossimulatorarm64`
- `CHANGELOG.md` — `2.0.0` dated 19 Apr 2024; iOS lifecycle/state-saving fixes
- Sample sources:
  - `demos/appyx-navigation/mainApp/src/commonMain/.../node/root/RootNode.kt` — **the auth gate** (LoggedOut vs Main(user), `onLogin`/`onLogout` via `backStack.replace`, `waitForMainAttached`)
  - `demos/appyx-navigation/mainApp/src/commonMain/.../navigator/Navigator.kt` — `Navigator: NodeReadyObserver<RootNode>`, suspend chains
  - `demos/appyx-navigation/mainApp/src/androidMain/.../MainActivity.kt` — Android `NodeHost`/`NodeActivity` + Intent deep-link parsing, `allowDummyLogin = (intent?.data == null)`
  - `demos/appyx-navigation/mainApp/src/iosMain/.../main.ios.kt` — iOS `IosNodeHost` + `MainIntegrationPoint` + **`handleDeepLinks(url: NSURL)`** (cross-platform deep-link proof)
  - `appyx-navigation/common/src/iosMain/.../integration/IosNodeHost.kt` — iOS host impl (ExperimentalForeignApi, custom lifecycle/back)
  - `appyx-navigation/common/build.gradle.kts` — declared `iosX64/iosArm64/iosSimulatorArm64` + Compose MP targets

Web:
- Maven Central `com.bumble.appyx:appyx-navigation` — latest **2.0.1**, published ~2 years ago
- https://bumble-tech.github.io/appyx/ — docs site (1.x + 2.x), confirms 2.x "experimental" labelling
