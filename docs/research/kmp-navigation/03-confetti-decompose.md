# Confetti (Decompose) — KMP Navigation Review

**One-liner:** Confetti models navigation as a hand-written tree of Decompose components whose `ChildStack`s are driven by `@Serializable` `Config` sealed classes; deep links rebuild the root component from a URL, and "auth" is a reactive `StateFlow<User?>` woven into configs rather than a hard route gate.
**Repo:** https://github.com/joreilly/Confetti (cloned `--depth 1`, 2026-05-26; Decompose `3.4.0` per `gradle/libs.versions.toml:23`) **Approach:** Decompose (`com.arkivanov.decompose`)

---

## Why it's AAA-grade

- Maintained by John O'Reilly (Google GDE) and co-developed with Arkadii Ivanov (Decompose's author — note `AppDelegate.swift` header "Created by Arkadii Ivanov"). It is the de-facto reference for Decompose-on-KMP.
- Ships real apps on **six** targets from one shared component tree: Android (`androidApp`), iOS (`iosApp`), Wear OS (`wearApp`), Android Auto (`automotiveApp`/`common/car`), Compose Desktop (`compose-desktop`), Compose Web/Wasm (`compose-web`). The navigation logic lives in `shared/src/commonMain/.../decompose/` and is consumed by all of them.
- Production concerns are all present: predictive-back (Android + iOS), state restoration via Essenty `StateKeeper`, App Links + iOS Universal Links, Firebase auth, GraphQL/Apollo data.

## Navigation approach & route definitions

No navigation *library* in the androidx sense. Each screen (or screen group) is a **component**: an interface + a `Default…Component` class that does `ComponentContext by componentContext`. A parent owns a `StackNavigation<Config>` and exposes a `Value<ChildStack<*, Child>>`. Routes are the **`Config`** sealed classes — `@Serializable`, nested *privately* inside each component.

Root lives in `shared/src/commonMain/kotlin/dev/johnoreilly/confetti/decompose/AppComponent.kt`. Its stack and configs (`AppComponent.kt:45-64`, `159-173`):

```kotlin
private val navigation = StackNavigation<Config>()

override val stack: Value<ChildStack<*, Child>> =
    childStack(
        source = navigation,
        serializer = Config.serializer(),          // <- @Serializable config
        initialConfiguration = Config.Loading,
        childFactory = ::child,
    )

@Serializable
private sealed class Config {
    @Serializable data object Loading : Config()
    @Serializable data object Conferences : Config()
    @Serializable
    data class Conference(
        val uid: String?,                 // see auth section — forces recreation on user change
        val conference: String,
        val conferenceThemeColor: String?,
    ) : Config()
}
```

The tree is **three levels deep**, each level its own component + config + `ChildStack`:

1. `AppComponent` → `Loading | Conferences | Conference` (`replaceAll` between them; no back stack here).
2. `ConferenceComponent` (`ConferenceComponent.kt`) → `Home | SessionDetails | SpeakerDetails | Settings`. This is the real push/pop stack: `navigation.push(Config.SessionDetails(sessionId = it))` (`:66`), `navigation.pop()` (`:81`, `:102`), `bringToFront` for cross-links (`:83`). `handleBackButton = true` (`:53`).
3. `HomeComponent` (`HomeComponent.kt`) → the bottom-nav tabs `Sessions | Speakers | Bookmarks | Venue | Search | Recommendations`. Tab switches use `navigation.bringToFront(Config.Speakers)` etc. (`:133-159`) so each tab keeps its place in the stack.

Navigation is invoked by calling **methods on the component** (`onSessionsTabClicked()`, `onBackClicked()`), and children talk to parents via **constructor lambdas** (`onSessionSelected: (id) -> Unit`, `onFinished = navigation::pop`) — not via a global nav controller. Child components are built lazily in a `child(config, ctx)` factory (`AppComponent.kt:116`, `ConferenceComponent.kt:57`, `HomeComponent.kt:65`).

The Compose side renders the stack with Decompose's `Children` / `ChildStack` composables (`shared/.../ui/App.kt:67`, `:80`, `:143`):

```kotlin
Children(stack = component.stack) {
    when (val child = it.instance) {
        is AppComponent.Child.Loading      -> LoadingView()
        is AppComponent.Child.Conferences  -> ConferenceListView(child.component)
        is AppComponent.Child.Conference   -> ConferenceView(child.component)
    }
}
```

## Auth gating (public vs protected routes, redirect-to-login)

**Confetti does NOT gate routes on auth.** There is no "protected vs public route" classification, no redirect-to-sign-in interceptor. Every screen is reachable while signed out. This is the single biggest gap vs. FoodRats' goal #2 — the pattern would have to be added, not adopted.

How auth actually works:

- `Authentication` (`shared/src/commonMain/.../auth/Authentication.kt`) is a port exposing `val currentUser: StateFlow<User?>`, `suspend fun signIn(idToken)`, `fun signOut()`. The Default impl wraps **GitLive Firebase** (`Firebase.auth.authStateChanged.map { it?.toUser() }.stateIn(...)`) — i.e. the same binding family FoodRats already uses.
- The root component subscribes to it and rebuilds the stack when the user changes (`AppComponent.kt:66-72`, `105-114`):

  ```kotlin
  init {
      coroutineScope.launch {
          authentication.currentUser.collect { setUser(it) }
      }
  }
  private fun onUserChanged(uid: String?) {
      navigation.navigate { oldStack ->
          oldStack.map { config ->
              when (config) {
                  is Config.Conference -> config.copy(uid = uid) // uid change => Decompose recreates the component
                  else -> config
              }
          }
      }
  }
  ```

  The `uid` field in `Config.Conference` is documented as *"Unused, but needed to recreate the component when the user changes"* (`:169`) — changing a serializable config value is how you force Decompose to rebuild a subtree.

- Sign-in is **advisory and bubbles to the platform**, not a navigation event. `HomeComponent.onSignInClicked()` just calls the injected `onSignIn` lambda (`HomeComponent.kt:161`), which on Android runs `signInProcess.signIn(this@MainActivity)` (`MainActivity.kt:53-57`, native Credential/Google flow) and on iOS runs `Authentication().googleOauth()` (`AppDelegate.swift:38-46`). Protected *content* (e.g. Bookmarks) renders an in-place sign-in prompt (`SignInDialog` in `ui/SigninDialog.kt`, `BookmarksView.kt:46`) rather than blocking the route.
- A thin `SignInComponent` exists (`SignInComponent.kt`) but only wraps `authentication.signIn(idToken)` + a close callback; it is **not** wired into the mobile component tree (no `Config.SignIn` in `AppComponent`/`ConferenceComponent`). It's used by the Wear/other surfaces.

**Takeaway for FoodRats:** to get redirect-to-login you'd implement it yourself — e.g. in each `child(config)` factory or in `onUserChanged`, inspect `user == null` and `navigation.replaceAll(Config.SignIn)` for configs you tag as protected. Decompose gives you the primitives (a reactive user flow + imperative `navigate`/`replaceAll` over typed configs) but ships no gating abstraction.

## Deep links / universal links / app links (Android + iOS)

Deep links are supported on **both** mobile platforms, but the depth differs by surface. Two distinct strategies appear in the repo:

### Mobile (Android + iOS) — recreate the root from `initialConferenceId`

The mobile apps support exactly one deep-link shape: `https://confetti-app.dev/conference/{id}`. They do **not** build a multi-entry back stack; they pass a parsed id into the root component constructor and let it `replaceAll` to that screen.

- **Android App Links** — verified intent filter in `androidApp/src/main/AndroidManifest.xml:50-57`:

  ```xml
  <intent-filter android:autoVerify="true">
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="https" android:host="confetti-app.dev" />
  </intent-filter>
  ```

  `MainActivity.onCreate` uses Decompose's **official** `handleDeepLink { uri -> ... }` API plus `defaultComponentContext(discardSavedState = ...)` (`MainActivity.kt:40-60`):

  ```kotlin
  val appComponent =
      handleDeepLink { uri ->
          val initialConferenceId = uri?.extractConferenceIdOrNull()
          val rootComponentContext =
              defaultComponentContext(discardSavedState = initialConferenceId != null)
          DefaultAppComponent(
              componentContext = rootComponentContext.childContext("app"),
              initialConferenceId = initialConferenceId,
              onSignOut = { ... }, onSignIn = { ... },
          )
      } ?: return
  ```

  `extractConferenceIdOrNull()` (`:70-79`) validates host `confetti-app.dev`, path `/conference/{id}`, alnum id. `discardSavedState = true` on a deep link throws away the restored stack so the link wins.

- **iOS Universal Links** — entitlement `applinks:confetti-app.dev` in `iosApp/iosApp/iosApp.entitlements:7`. The URL is caught in SwiftUI `.onOpenURL` (`iOSApp.swift:22-31`): it validates `pathComponents == ["/", "conference", id]`, then calls `appDelegate.onConferenceDeepLink(conferenceId:)`. That handler (`AppDelegate.swift:52-67`) **destroys the old lifecycle and rebuilds `DefaultAppComponent`** with `initialConferenceId: conferenceId` — the iOS analogue of `discardSavedState`.

- **Common landing logic** — both feed `initialConferenceId` into `DefaultAppComponent.init` (`AppComponent.kt:74-89`), which branches to `selectAndNavigateToDeepLinkedConference()` (`:98-103`) → `showConference()` → `navigation.replaceAll(Config.Conference(...))`.

### Wear — the canonical Decompose "build the whole back stack from a URI" pattern

`wearApp/.../navigation/WearAppComponent.kt` is the richer example and the one most relevant if FoodRats wants *arbitrary-depth* deep links. It builds the **initial stack** from the launch intent and also handles warm-start intents:

```kotlin
override val stack: Value<ChildStack<Config, Child>> = childStack(
    source = navigation,
    serializer = Config.serializer(),
    initialStack = { initialConfig(intent) },          // <- stack, not single config
    childFactory = this::buildChild,
)
private fun initialConfig(intent: Intent) = deeplinkStack(intent) ?: listOf(Config.Loading)

override fun handleDeeplink(intent: Intent): Boolean { // warm start (onNewIntent)
    val stack = deeplinkStack(intent)
    if (stack != null) navigation.replaceAll(*stack.toTypedArray())
    return stack != null
}

private fun buildConfig(user: String?, uri: String): Config? {
    val path = uri.substringAfter("confetti://confetti")
    return when {
        path == "/settings"          -> Config.Settings
        path.startsWith("/session/") -> { val (c, s) = ...; Config.SessionDetails(user, c, s) }
        path.startsWith("/speaker/") -> { ... Config.SpeakerDetails(...) }
        // ...
        else -> null
    }
}
```

(`WearAppComponent.kt:144-151`, `186-248`; tested in `wearApp/.../app/NavigationTest.kt` against URIs like `confetti://confetti/session/test/session1`.) `buildStack(target)` is where you'd expand a single target into a multi-entry back stack (it's currently a stub returning `listOf(target)`). This is the shape FoodRats should copy if it needs deep links that land deep *with* a sensible back stack.

## Back stack & state restoration

- **State restoration** is Essenty `StateKeeper`, automatic because configs are `@Serializable` and passed to `childStack(serializer = Config.serializer(), ...)`. Android gets the `StateKeeper` from `defaultComponentContext()` (`MainActivity.kt:43`); on a deep link it's deliberately discarded via `discardSavedState = true`. iOS passes `stateKeeper: nil` (`AppDelegate.swift:25`) — Confetti opts out of restoration on iOS.
- **Instance retention** (the "survive config change without re-fetch" job that `ViewModel` does in androidx) is Essenty `InstanceKeeper` / `coroutineScope()` tied to the component lifecycle (`DecomposeUtils.kt:18-25`: `coroutineScope` cancelled in `lifecycle.doOnDestroy`).
- **Back handling** is owned by the component: `handleBackButton = true` on the inner stacks, `BackHandlerOwner` on `ConferenceComponent` (`ConferenceComponent.kt:19`), `onBackClicked()` → `navigation.pop()`, and `onBackClicked(toIndex)` → `navigation.navigate { it.take(toIndex + 1) }` for pop-to (`:101-107`).
- **Predictive back** on both platforms: Android via `predictiveBackAnimation(backHandler, onBack = ::onBackClicked)` in `App.kt:82`; iOS via `PredictiveBackGestureOverlay(backDispatcher = ...)` in `MainViewController.kt:20-31`.

## iOS / multiplatform wiring

- The root component is created **in Swift** in `AppDelegate.init()` (`AppDelegate.swift:18-50`): `KoinKt.doInitKoin()`, then `DefaultAppComponent(componentContext: DefaultComponentContext(lifecycle: ApplicationLifecycle(), stateKeeper: nil, instanceKeeper: nil, backHandler: backDispatcher), onSignIn:/onSignOut: { native Firebase }, ...)`. The `onSignIn` lambda is where iOS plugs its `Authentication().googleOauth()` native flow — same lambda the common component invokes.
- The Kotlin entry point is `MainViewController(component:backDispatcher:)` (`shared/src/iosMain/.../ui/MainViewController.kt`) returning a `ComposeUIViewController { ... App(component) ... }` wrapped in the predictive-back overlay. So the **whole UI is Compose Multiplatform**, hosted in a single `UIViewController`; SwiftUI is just the shell (`iOSApp.swift` `ConfettiIosApp` → `ConfettiApp(component:backDispatcher:)`).
- `BackDispatcher` is created once in Swift (`AppDelegate.swift:14`) and threaded into both the component context (`backHandler:`) and the Compose overlay — that's what makes the iOS edge-swipe drive the same Decompose back stack.
- Same `DefaultAppComponent` is reused by Android (`MainActivity`), Desktop, and Web; only the `ComponentContext`/lifecycle source differs per platform.

## Strengths

- **One component tree, six platforms.** The clearest demonstration that Decompose navigation is genuinely shared (UI + nav logic in `commonMain`), not just shared data.
- **Typed, serializable routes with payloads.** `Config` carries `sessionId`, `conference`, `uid` — type-safe args, no string routes, no manual bundle packing. Conceptually identical to FoodRats' `@Serializable Route`.
- **Navigation is unit-testable without a device.** Components are plain classes; `WearAppComponent` deep links are tested in pure JVM (`NavigationTest.kt`). FoodRats' androidx-nav graph can't be tested this directly.
- **Lifecycle/retention/state-restoration solved by the framework** (Essenty), cross-platform, including predictive back on iOS.
- **First-class, explicit deep-link API** (`handleDeepLink {}`, `initialStack = { ... }`, `replaceAll(*stack)`), with the "rebuild the back stack from a URI" pattern already worked out in `WearAppComponent`.

## Weaknesses

- **No auth-gating abstraction at all.** Redirect-to-login must be built by hand in the `child` factory / `onUserChanged` reducer. Confetti chose in-place sign-in prompts instead — so it is *not* a reference for FoodRats' goal #2.
- **Mobile deep linking is shallow.** Android/iOS only handle one `/conference/{id}` link and just `replaceAll` to it (no synthesized back stack); the richer parser lives only on Wear. You'd lift the Wear approach to mobile yourself.
- **Lots of boilerplate.** Every screen = interface + `DefaultXComponent` + nested `Config` + `Child` sealed class + factory branch + a callback lambda per navigation edge. Far more ceremony than `composable<Route.X> {}`.
- **Manual parent↔child wiring via lambdas** doesn't scale gracefully — deep trees thread many `onXSelected`/`onFinished` callbacks by hand.
- **iOS opts out of state restoration** (`stateKeeper: nil`) and rebuilds the entire component on deep link (`applicationLifecycle.destroy()`), discarding in-memory state — acceptable here, but a sharp edge.
- **Not the JetBrains-blessed default.** Decompose is third-party (Arkivanov). Strong, but a strategic bet distinct from `navigation-compose`, which JetBrains ships.

## Relevance to FoodRats (supports our 2 goals? migration cost from androidx nav?)

**Goal 1 — deep links / universal links / app links (both platforms): YES, well supported, and arguably better than androidx-nav.** Confetti proves the full chain on Android (verified App Links + `handleDeepLink {}`) and iOS (Universal Links entitlement + `.onOpenURL` → rebuild root with `initialConferenceId`). The `WearAppComponent` URI→`List<Config>` parser is a ready-made template for landing on a deep screen with a real back stack — exactly FoodRats' need (e.g. open a specific Meal/Crew from a link). Because configs are already `@Serializable`, deep-link construction is just "parse URL → list of configs → `replaceAll`". This is more explicit and more testable than wiring `deepLinks { navDeepLink { uriPattern = ... } }` per `composable<>` and is identical across both platforms (androidx-nav's deep-link story on iOS/CMP is far less proven).

**Goal 2 — auth-gated routing: NOT demonstrated by Confetti.** Confetti deliberately does *not* gate; it shows sign-in prompts inline and rebuilds subtrees on user change. The building blocks transfer (auth is a `StateFlow<User?>` — same GitLive Firebase binding FoodRats uses; `onUserChanged` reducer pattern; imperative `replaceAll`), so you *can* implement redirect-to-login in the root `child()` factory by checking `user == null` for protected configs. But you'd be inventing it, not copying it. FoodRats' current androidx-nav approach (classify `Route`s, redirect in a `LaunchedEffect`/nav listener) is at least as easy for this specific goal.

**Migration cost from androidx-nav: HIGH.** This is a paradigm change, not a swap:
- Every `Route.X` becomes a component (interface + `Default…Component` + nested `Config` + `Child`). FoodRats has ~6 features → expect a component per screen plus parent aggregators.
- `composable<Route.X> {}` blocks become `child(config)` factory branches; `navController.navigate(...)` / `popUpTo<>()` become `navigation.push/replaceAll/pop` calls invoked through component methods.
- ViewModels (FoodRats' `MviViewModel`) would move *into* components or be held via `InstanceKeeper` — a real architectural decision, since Decompose components already are the lifecycle-scoped holder.
- New deps (`com.arkivanov.decompose:decompose` + `extensions-compose`, Essenty), Koin re-wiring to construct the root component per platform (`MainActivity` on Android, `AppDelegate` on iOS — Confetti's `KoinKt.doInitKoin()` + `DefaultAppComponent(...)` is the template).
- Upside that partly offsets the cost: navigation becomes pure-JVM unit-testable, and the deep-link + predictive-back + state-restoration machinery is bought off the shelf for both platforms.

**Verdict:** Best-in-class evidence that **cross-platform deep links** are solved by Decompose; but it offers **no auth-gate pattern** and the migration from androidx-nav is a full rewrite of the navigation layer. Adopt Decompose only if shared/testable nav + robust iOS deep links outweigh a one-time high-cost migration; for auth gating you're on your own either way.

## Sources (specific files/commits/docs reviewed)

Cloned `git clone --depth 1 https://github.com/joreilly/Confetti` on 2026-05-26.

- `gradle/libs.versions.toml:23,126-128` — Decompose `3.4.0` (decompose, extensions-compose, extensions-compose-experimental)
- `shared/src/commonMain/kotlin/dev/johnoreilly/confetti/decompose/AppComponent.kt` — root component, `StackNavigation`/`childStack`, `@Serializable Config`, `initialConferenceId` deep-link entry, `onUserChanged` auth reducer
- `.../decompose/ConferenceComponent.kt` — inner push/pop stack, `BackHandlerOwner`, `pop`/`bringToFront`/pop-to-index
- `.../decompose/HomeComponent.kt` — bottom-nav tab stack via `bringToFront`, `onSignInClicked` → bubble-up lambda
- `.../decompose/SignInComponent.kt` — thin sign-in component (not wired into mobile tree)
- `.../decompose/DecomposeUtils.kt` — `coroutineScope()` tied to lifecycle, `Flow.asValue`
- `shared/src/commonMain/kotlin/dev/johnoreilly/confetti/auth/Authentication.kt` — `StateFlow<User?>` port + GitLive Firebase impl
- `shared/src/commonMain/kotlin/dev/johnoreilly/confetti/ui/App.kt` — `Children`/`ChildStack` Compose rendering, predictive-back animation
- `shared/src/iosMain/kotlin/dev/johnoreilly/confetti/ui/MainViewController.kt` — `ComposeUIViewController`, `PredictiveBackGestureOverlay`, `BackDispatcher`
- `androidApp/src/main/java/dev/johnoreilly/confetti/MainActivity.kt` — `handleDeepLink {}`, `defaultComponentContext(discardSavedState)`, `extractConferenceIdOrNull`, Koin-injected `SignInProcess`
- `androidApp/src/main/AndroidManifest.xml:50-57` — verified App Links intent filter (`https`/`confetti-app.dev`, `autoVerify`)
- `iosApp/iosApp/iOSApp.swift:22-31` — SwiftUI `.onOpenURL` Universal Link parsing
- `iosApp/iosApp/AppDelegate.swift` — Swift-side root component creation, native sign-in lambdas, `onConferenceDeepLink` (lifecycle rebuild)
- `iosApp/iosApp/iosApp.entitlements:7` — `applinks:confetti-app.dev` associated domain
- `wearApp/src/main/java/dev/johnoreilly/confetti/wear/navigation/WearAppComponent.kt:144-248` — canonical `initialStack`/`deeplinkStack`/`buildConfig` URI→`List<Config>` deep-link back-stack pattern + `handleDeeplink`
- `wearApp/src/test/kotlin/dev/johnoreilly/confetti/wear/app/NavigationTest.kt` — pure-JVM deep-link unit tests
