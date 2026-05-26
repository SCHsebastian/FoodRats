# Decompose (library) — KMP Navigation Review

**One-liner:** A component-based, lifecycle-aware KMP navigation library where navigation is plain Kotlin state (a `ChildStack`/`ChildSlot` of `@Serializable` configs) you build and mutate yourself — UI-toolkit-agnostic, with first-class state restoration but only Android deep-link plumbing (iOS universal links and route-catalog/auth-gate logic are hand-written).

**Repo:** https://github.com/arkivanov/Decompose  **Docs:** https://arkivanov.github.io/Decompose/

## What it is

Decompose (by Arkadii Ivanov, built on the **Essenty** library for `Lifecycle`/`StateKeeper`/`InstanceKeeper`/`BackHandler`) models an app as a tree of **components**, each holding a `ComponentContext`. A component is a plain class — typically `class DefaultXComponent(...) : XComponent, ComponentContext by componentContext` — that owns its state, business logic, and child navigation. Navigation is **not** a graph DSL; it is observable state: a `Value<ChildStack<Config, Child>>` (back stack) or `Value<ChildSlot<Config, Child>>` (0-or-1 child). You mutate that state via a `StackNavigation`/`SlotNavigation` "navigator" and the UI re-renders. Rendering is a separate, pluggable concern: `extensions-compose` renders a stack with a `Children(...)` composable; `extensions-android` renders into Android Views; on iOS the components are bridged into SwiftUI. The component logic lives entirely in `commonMain` and is unit-testable with no UI (see `RootComponentIntegrationTest` below).

This is the inverse of androidx Navigation: there, the NavController + graph is the source of truth and screens are leaves. Here, **your component tree is the source of truth** and navigation state is just serializable data you control.

## Navigation approach & route definitions

The route catalog is a `@Serializable sealed interface Config` declared **privately inside the parent component** (not a global `Route` registry). Each destination is a `data object` (no args) or `data class` (typed args). From the official sample `DefaultRootComponent.kt`:

```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val featureInstaller: FeatureInstaller,
    deepLinkUrl: Url? = null,
) : RootComponent, ComponentContext by componentContext {

    private val nav = StackNavigation<Config>()

    private val _stack =
        childStack(
            source = nav,
            serializer = Config.serializer(),
            initialStack = { getInitialStack(deepLinkUrl) },  // or initialConfiguration = Config.Tabs()
            childFactory = ::child,
        )
    override val stack: Value<ChildStack<*, Child>> = _stack

    private fun child(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Tabs   -> TabsChild(DefaultTabsComponent(componentContext, ...))
            is Config.Pages  -> PagesChild(DefaultPagesComponent(componentContext, ...))
            // exhaustive when over the sealed Config
        }

    override fun onBackClicked()            { nav.pop() }
    override fun onBackClicked(toIndex: Int){ nav.popTo(index = toIndex) }

    @Serializable
    private sealed interface Config {
        @Serializable data class Tabs(val deepLinkUrl: Url? = null) : Config
        @Serializable data object DynamicFeatures : Config
        @Serializable data class Pages(val deepLinkUrl: Url? = null) : Config
    }
}
```

Two `childStack` overloads (`ChildStackFactory.kt`): one takes `initialConfiguration: C` (single root), the other `initialStack: () -> List<C>` (a pre-built multi-entry back stack — the deep-link entry point). `serializer` is optional; passing `null` opts out of state preservation.

**Navigation operations** (extension functions on `StackNavigation<C>`, all built on the primitive `navigate(transformer: (List<C>) -> List<C>, onComplete)`):
- `push(config)` — add to top (throws if the config already exists in the stack; duplicates are forbidden by default).
- `pushNew(config)` — push only if not already on top (no-op if it is).
- `pushToFront(config)` — push to top, removing any existing duplicate from the back stack.
- `pop()` / `pop { isSuccess -> ... }` — remove top; the callback is how you deliver a result back to the previous screen.
- `popWhile { predicate }`, `popTo(index)`, `popToFirst()`.
- `replaceCurrent(config)` — swap the top entry.
- `replaceAll(vararg configs)` — **set the entire stack at once** (kept components retain state; removed ones are destroyed). This is what `onNewIntent` deep-link handling uses to rebuild the stack.
- `bringToFront(config)` — remove all entries of that config's class, then push it on top (the bottom-nav / tab-switch idiom).
- `navigate { stack -> newStack }` — arbitrary transform for anything bespoke.

Child callbacks are wired by passing lambdas down (`onPagesItemSelected = { nav.pushNew(Config.Pages()) }`), so children never reference the parent's navigator directly — clean separation, no shared singleton router.

## Auth gating (public vs protected routes, redirect-to-login) — idiomatic pattern

**There is no built-in "protected route" concept** (unlike a NavController interceptor). Auth gating is done by *modelling it in the component tree* — the canonical Decompose answer. The root holds an auth state and exposes either an authed or unauthed child; because the child is chosen by a `when` over a sealed `Config`, an unauthenticated user simply cannot be handed a protected component. Two idiomatic shapes:

**(a) Root stack swaps between an Auth subtree and a Main subtree** based on an injected auth port. The redirect-to-login is a `replaceAll` driven by an auth-state subscription:

```kotlin
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val auth: AuthProvider,            // a :core:domain port, observable
) : RootComponent, ComponentContext by componentContext {

    private val nav = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, Child>> =
        childStack(
            source = nav,
            serializer = Config.serializer(),
            initialStack = {
                if (auth.isSignedIn) listOf(Config.Main) else listOf(Config.SignIn)
            },
            handleBackButton = true,
            childFactory = ::child,
        )

    init {
        // redirect-to-login on sign-out; jump to main on sign-in.
        auth.isSignedInFlow.subscribe { signedIn ->
            nav.navigate { stack ->
                when {
                    !signedIn -> listOf(Config.SignIn)                 // wipe protected stack
                    stack.none { it is Config.Main } -> listOf(Config.Main)
                    else -> stack
                }
            }
        }
    }

    private fun child(config: Config, ctx: ComponentContext): Child = when (config) {
        Config.SignIn -> Child.SignIn(DefaultSignInComponent(ctx, onSignedIn = { /* flow above reacts */ }))
        Config.Main   -> Child.Main(DefaultMainComponent(ctx, auth = auth))   // only reachable when authed
    }

    @Serializable private sealed interface Config {
        @Serializable data object SignIn : Config
        @Serializable data object Main : Config
    }
}
```

**(b) `Child Slot` overlay** — `childSlot` manages "one child or none" and is the documented primitive for an auth overlay: activate the auth component when signed-out, `dismiss()` it on success (`ChildSlotFactory.kt`, slot overview doc):

```kotlin
private val authNav = SlotNavigation<AuthConfig>()
override val authSlot: Value<ChildSlot<*, AuthComponent>> =
    childSlot(source = authNav, serializer = AuthConfig.serializer(), handleBackButton = true) { _, ctx ->
        DefaultAuthComponent(ctx, onAuthSuccess = authNav::dismiss)
    }
private fun requireAuth() { authNav.activate(AuthConfig) }   // gate
```

The "classify routes as protected vs public" logic is whatever Kotlin you write in `child(...)`/`getInitialStack(...)` — there's no annotation or registry. For FoodRats this maps cleanly onto a `:core:domain` `SessionProvider`/`ActiveCrewProvider` port: the root subscribes and `replaceAll`s. Trade-off: it's explicit and fully testable, but it's **code you own and must get right**, not a framework guarantee.

## Deep links / universal links / app links (Android + iOS) — what library provides vs per-platform

**Decompose's deep-link model = "build the initial back stack from external data."** There is **no URL parser, no path-pattern matcher, no `<deepLink>`-equivalent in the library's `commonMain`** (verified: `grep` for `Uri|deepLink|pathSegment|matchPath` in `decompose/src/commonMain` returns nothing). What the library gives you is the `initialStack: () -> List<Config>` hook plus `replaceAll` for runtime links. You parse the URL yourself and decide the stack. The sample does this with a hand-rolled `Url` value object and a recursive `consumePathSegment()` that lets each component peel one segment and pass the rest down:

```kotlin
// DefaultRootComponent.getInitialStack — turns a Url into a multi-entry stack
private fun getInitialStack(deepLinkUrl: Url?): List<Config> {
    val (path, childUrl) = deepLinkUrl?.consumePathSegment() ?: return listOf(Config.Tabs())
    return when (path) {
        pathSegmentOf<Config.Pages>() -> listOf(Config.Tabs(), Config.Pages(deepLinkUrl = childUrl)) // nested!
        pathSegmentOf<Config.CustomNavigation>() -> listOf(Config.Tabs(), Config.CustomNavigation)
        else -> listOf(Config.Tabs(deepLinkUrl = childUrl))
    }
}
```

This is **fully testable in `commonTest`** (`RootComponentIntegrationTest.kt`):

```kotlin
@Test fun WHEN_created_with_deeplink_PagesChild_THEN_PagesChild_active() {
    val component = createComponent(deepLink = Url(url = "https://example.com/pages"))
    component.stack.assertActiveInstance<PagesChild>()
}
```

**Android — the library DOES help.** `extensions-android`'s `Activity.handleDeepLink { uri -> ... }` (in `DeeplinkUtils.kt`, `@ExperimentalDecomposeApi`) does three real things: (1) hands you `intent.data` to parse, (2) ensures the deep link is consumed **only once** (guards against re-applying on config-change/process-death restore via a `SavedStateRegistry` flag — `discardSavedState = itemId != null`), and (3) handles the `FLAG_ACTIVITY_NEW_TASK` case by restarting the activity with a clean task stack. Runtime links to a *running* app use `onNewIntent` + `navigation.replaceAll(...)`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = handleDeepLink { uri ->
        val itemId = uri?.extractItemId()
        DefaultRootComponent(defaultComponentContext(discardSavedState = itemId != null), itemId = itemId)
    } ?: return          // null => activity restart initiated; bail
    setContent { RootContent(root) }
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val itemId = intent.data?.extractItemId() ?: return
    root.onDeepLink(itemId)     // -> navigation.replaceAll(Config.List, Config.Details(itemId))
}
```

You still author the `<intent-filter>` (App Links / `autoVerify`) in `AndroidManifest.xml` yourself — note the **sample's manifest has only a LAUNCHER filter**, so the deep-link wiring above is documented but not exercised end-to-end in the shipped sample app.

**iOS — the library provides nothing; it's 100% per-platform glue.** Both Swift entry points pass `deepLinkUrl: nil` hard-coded:

```swift
// app_iosApp.swift  AND  app-ios-compose/iOSApp.swift
lazy var root: RootComponent = DefaultRootComponent(
    componentContext: DefaultComponentContext(lifecycle: ApplicationLifecycle(), ...),
    featureInstaller: DefaultFeatureInstaller.shared,
    deepLinkUrl: nil)            // <-- you must populate this from the URL/userActivity yourself
```

There is **no** `onOpenURL` / `application(continue: userActivity:)` handler anywhere in either iOS sample (verified by grep). To support universal links on iOS you write the SwiftUI/UIKit handler, parse the `NSUserActivity.webpageURL` into a `KotlinString`, construct a `Url(...)` (or call a Kotlin parser), and either pass it into `DefaultRootComponent(deepLinkUrl:)` at cold start or expose a Kotlin `root.onDeepLink(url)` for warm links. **Bottom line: Decompose unifies the *consequence* of a deep link (building the stack, in common code), but the *delivery* — intent filters on Android, the universal-links handler + `apple-app-site-association` on iOS — is entirely yours, and there's no ready-made cross-platform URL→config matcher.**

## Back stack & state restoration

- **State preservation** uses Essenty's `StateKeeper` (every `ComponentContext` is a `StateKeeperOwner`). The navigation stack is auto-saved/restored because you passed `serializer = Config.serializer()` to `childStack` — `ChildStackFactory.kt` wires `saveStack`/`restoreStack` through a `SerializableContainer(ListSerializer(serializer))`. Component-internal state is saved with `stateKeeper.register(key, strategy) { state }` + `stateKeeper.consume(key, strategy)`, or the v3.2+ delegate `var state by saveable(serializer, init = ::State)`. Backing store is platform-native: Android `onSaveInstanceState`/`SavedStateHandle`, iOS `NSCoder`, JVM files, Web `localStorage`. **This is a genuine strength** — survives Android config changes *and* process death, and iOS state restoration, from one common declaration.
- **Instance retaining** (`InstanceKeeper`, ViewModel-equivalent) keeps non-serializable objects across config changes via `retainedInstance { ... }`.
- **Back handling** uses Essenty's `BackHandler`. `childStack(handleBackButton = true)` auto-pops on system back. On Android `defaultComponentContext()` wires the platform back dispatcher; on iOS Swift creates a `BackDispatcher()` and passes it into both the component context and the Compose overlay.
- **Predictive back** is first-class in `extensions-compose`: `predictiveBackAnimation(backHandler, fallbackAnimation = stackAnimation(fade() + scale()), onBack = component::onBackClicked)`. On non-Android Compose targets (incl. iOS) you wrap content in `PredictiveBackGestureOverlay(backDispatcher = ...)` to get the edge-swipe gesture — see `RootViewController.kt`. Stack transitions: `stackAnimation(slide()/fade()/scale())`, combinable with `+`.

## iOS / multiplatform wiring

Two supported paths; **the Compose path (`app-ios-compose`) is what matters for FoodRats** since FoodRats is Compose Multiplatform:

**Compose-on-iOS (recommended, mirrors FoodRats):** Kotlin exposes `fun rootViewController(root, backDispatcher): UIViewController = ComposeUIViewController { PredictiveBackGestureOverlay(backDispatcher) { RootContent(root) } }` (`RootViewController.kt`). Swift wraps it in a `UIViewControllerRepresentable` and constructs the root component + `BackDispatcher` in the `AppDelegate` (`app-ios-compose/RootView.swift`, `iOSApp.swift`). This is essentially identical to FoodRats' current `MainViewController()` pattern — the same `ComposeUIViewController` entry, just rendering a Decompose root instead of a Nav-Compose `NavHost`. Deep-link delivery would be one added line wiring a parsed `Url` into `DefaultRootComponent(deepLinkUrl:)`.

**SwiftUI-native rendering (`app-ios`):** if you render screens in *SwiftUI* instead of Compose, Decompose components are bridged via hand-written helpers the sample ships in `DecomposeHelpers/`: `ObservableValue` (subscribes a Decompose `Value<T>` to an `@Published` SwiftUI `ObservableObject`), `StateValue` (a property wrapper over it), and `StackView` (renders `root.stack.items` into a SwiftUI `NavigationStack` ≥ iOS 16.1, falling back to a `UINavigationController` interop for older iOS). These helpers are **not part of the library** — you copy them into your app. Root cold-start (`app_iosApp.swift`) builds `DefaultComponentContext(lifecycle: ApplicationLifecycle(), stateKeeper:, instanceKeeper: nil, backHandler:)` and the `AppDelegate` forwards iOS state-restoration callbacks (`shouldSave/RestoreSecureApplicationState`) into `StateKeeperDispatcher`.

## Strengths

- **UI-toolkit-agnostic & truly multiplatform logic.** Navigation + screen logic is pure Kotlin in `commonMain`, unit-testable with zero UI (the integration tests assert deep-link stacks directly). Renders to Compose, Android Views, SwiftUI, or web.
- **Navigation state is data you fully control.** `replaceAll`, `navigate { }`, conditional `initialStack` make auth-gating and deep-link stack-building straightforward and explicit — no fighting a graph DSL or interceptor API.
- **Best-in-class state restoration & lifecycle.** Real process-death survival on Android and `NSCoder` restoration on iOS from one `@Serializable` config declaration; `InstanceKeeper` covers retained objects.
- **Excellent predictive-back support**, including on iOS via the gesture overlay.
- **Strong DDD/Clean-Architecture fit.** Components are constructor-injected with ports/use-cases; `ComponentContext by componentContext` keeps them thin. Matches FoodRats' "feature owns its logic, depends on `:core:domain` ports" ethos better than a centralized NavController.
- Mature, single-maintainer-but-active, good docs, DeepWiki, multiple official samples.

## Weaknesses

- **No URL/path matching anywhere.** You hand-write the `Url` parser and the path→config mapping (the sample literally maps by `class.simpleName.snakeCase()`). For many deep-link routes this is real, ongoing boilerplate that androidx Nav's `deepLink {}`/URI patterns give for free.
- **iOS deep-link/universal-link delivery is entirely DIY** — no helper at all; you write the `NSUserActivity` handler and thread the URL into Kotlin. (Same is true of androidx Nav on iOS, but worth stating.)
- **Auth gating is your code, not a framework guarantee.** No "this route requires auth" declaration; correctness depends on your `when`/subscription logic.
- **SwiftUI-native rendering requires copying ~5 bridge files** (`ObservableValue`/`StateValue`/`StackView`) into the app — not a packaged dependency. (N/A if you stay on Compose-iOS, which FoodRats does.)
- **Conceptual shift / learning curve.** "Components own navigation state" is a different mental model from typed-route NavHost. The team must learn `ComponentContext`, `Value`, `childStack/childSlot`, Essenty lifecycle, and the manual Compose wiring (`Children(...)`).
- **Verbose root-component setup** vs a declarative `NavHost { composable<Route.X>{} }` graph; more ceremony per feature (a `Component` interface + `DefaultComponent` + `Child` sealed class).
- `handleDeepLink` and several APIs are `@ExperimentalDecomposeApi`.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav? learning curve?)

**Goal 1 — deep links / universal links on both platforms: PARTIAL, with real work.** Decompose gives a clean, testable way to *turn a URL into a back stack in common code* and solid Android plumbing (`handleDeepLink` one-shot consumption + new-task handling) — strictly more than FoodRats has today. But it provides **no URL parser and nothing for iOS link delivery**: you'd write a `Url`/path matcher in `:core:domain` (or a feature) and the iOS `NSUserActivity` handler yourself. Net vs androidx Nav: you trade Nav's free URI patterns for explicit, unit-testable Kotlin — and you'd hand-roll the iOS side either way.

**Goal 2 — auth-gated routing: STRONG fit, but self-authored.** The "root swaps Auth vs Main subtree, driven by a `:core:domain` session/active-crew port via `replaceAll`/`navigate{}`" pattern is idiomatic and exactly matches FoodRats' MVI + ports architecture, and it's fully testable in `commonTest` (matching the project's Turbine/host-test conventions). FoodRats' current hack (`Session.activeCrewId = "test-crew-1"` to keep publishing non-null, and the manual `popUpTo<Route.SignIn>()` on sign-in) becomes a single declarative auth subscription on the root component. Caveat: it's correctness-by-your-code, not by framework.

**Migration cost from androidx Nav: HIGH.** This is an architectural change, not a swap. Today's `Route` sealed interface + `NavGraph` + per-screen `composable<Route.X>` would be rebuilt as a root `DefaultRootComponent` + per-feature `XComponent`/`DefaultXComponent`/`Child`, with each ViewModel's responsibilities folded into (or injected into) its component. Every feature's navigation callbacks change. The Compose-iOS entry (`MainViewController` → `rootViewController` + `PredictiveBackGestureOverlay`) is a small, well-matched change; the bulk of the cost is restructuring all six features into the component model. **Learning curve: moderate-to-high** for a team currently on typed NavHost — new core concepts (ComponentContext, Value, child routers, Essenty lifecycle) and more setup ceremony per feature.

**Verdict:** Architecturally the *best-aligned* candidate for FoodRats' DDD/ports/MVI style and the strongest on state-restoration and auth-gating ergonomics — but it does **not** meaningfully reduce the cross-platform deep-link/universal-link effort (no URL matcher, DIY iOS delivery) and carries a high one-time migration + learning cost. Justified only if the team also wants Decompose's deeper benefits (testable navigation logic, lifecycle/state rigor, SwiftUI-rendering optionality); for deep-links + auth-gating alone it's heavier than the payoff.

## Sources (specific docs pages / sample files reviewed)

Docs (https://arkivanov.github.io/Decompose/):
- `navigation/stack/overview/` — ChildStack, Config, StackNavigation, childStack setup
- `navigation/stack/navigation/` — push/pushNew/pushToFront/pop/popWhile/popTo/replaceCurrent/replaceAll/bringToFront/navigate
- `navigation/stack/deeplinking/` — `initialStack`, `handleDeepLink`, `onNewIntent`+`replaceAll`
- `navigation/slot/overview/` — childSlot / SlotNavigation / activate / dismiss (auth-overlay primitive)
- `extensions/compose/` — `Children(...)`, `stackAnimation`, `predictiveBackAnimation`, `PredictiveBackGestureOverlay`
- `component/state-preservation/` — StateKeeper register/consume, `saveable`, platform backing stores

Library source (cloned `arkivanov/Decompose` @ depth-1):
- `decompose/src/androidMain/.../DeeplinkUtils.kt` — `handleDeepLink`, one-shot consumption, NEW_TASK restart (the only deep-link helper; Android-only)
- `decompose/src/commonMain/.../router/stack/ChildStackFactory.kt` — `childStack` overloads (`initialStack` vs `initialConfiguration`, optional `serializer`)
- `decompose/src/commonMain/.../router/slot/ChildSlotFactory.kt` — `childSlot` signatures
- Verified absence of any URL/path matcher: `grep -r 'Uri|deepLink|pathSegment|matchPath' decompose/src/commonMain` → empty

Official samples (`/sample`):
- `shared/.../root/DefaultRootComponent.kt` + `RootComponent.kt` — route catalog, `getInitialStack(deepLinkUrl)`, `pushNew`/`pop`/`popTo`
- `shared/.../Url.kt` — hand-rolled URL value object + `consumePathSegment()` (the lib provides no parser)
- `shared/.../root/RootComponentIntegrationTest.kt` — deep-link stack asserted in commonTest
- `shared/compose/.../root/RootContent.kt` — `Children(...)` + predictive back
- `shared/compose/src/iosMain/.../root/RootViewController.kt` — `ComposeUIViewController` + `PredictiveBackGestureOverlay` (the FoodRats-relevant iOS path)
- `app-ios-compose/{iOSApp,RootView}.swift` — Compose-on-iOS Swift entry; `deepLinkUrl: nil` hard-coded
- `app-ios/app_iosApp.swift`, `app-ios/RootView.swift`, `app-ios/DecomposeHelpers/{ObservableValue,StateValue,StackView}.swift` — SwiftUI-native bridge helpers (copied into the app, not in the library)
- `app-android/.../MainActivity.kt` + `app-android/src/main/AndroidManifest.xml` — Android entry; **only a LAUNCHER intent-filter** (no deep-link filter shipped)
- Verified absence of iOS universal-link wiring: `grep -r 'onOpenURL|continueUserActivity|NSUserActivity' sample/app-ios*` → empty
