# KMP Navigation Research — Summary & Recommendation

**Date:** 2026-05-26 · **For:** FoodRats navigation (universal/deep links + auth-vs-public routes)
**Method:** 8 parallel agents, each reviewing one AAA-grade KMP app or navigation library from actual source. Per-project files: `01`–`08` in this folder.

## The question
FoodRats wants navigation that supports:
1. **Universal links (iOS) / App Links (Android) / deep links** — open the app to a screen from a URL, both platforms.
2. **Auth-vs-public routes** — classify routes as protected vs public, redirect unauthenticated users to sign-in.

FoodRats already runs **`org.jetbrains.androidx.navigation:navigation-compose` 2.9.2** with a typed `sealed interface Route` of `@Serializable data object`s.

## Bottom line
**Stay on the library FoodRats already uses.** The decisive finding (file `08`): cross-platform deep links — including iOS universal links — became officially supported in CMP nav-compose **2.9.2, the exact version FoodRats already pins**, via JetBrains' `ExternalUriHandler` singleton + `NavUri` + `navDeepLink<T>`. Both goals are reachable **purely additively, zero migration**. Every alternative that's *better* at one goal (Circuit/Appyx for auth-gating, Decompose/Confetti for proven deep links) costs a **full navigation-layer rewrite** to get there.

## Ranked shortlist — the ones I'd actually pick

### 🥇 1. Stay: JetBrains/AndroidX Compose Multiplatform Navigation (current) — file `08`
- **Goal 1 (links):** ✅ iOS deep links now work (2.9.2). Android = `<intent-filter autoVerify>` + `navDeepLink<T>`. iOS = ~1–3 lines of Swift in `AppDelegate` (`ExternalUriHandler.shared.onNewUri(...)` in `open url` + `continue userActivity`) — and FoodRats **already owns** `application(_:open:)` for Google Sign-In, so it's an added line. No `NavController` crosses into Swift.
- **Goal 2 (auth):** ✅ Official conditional-nav pattern; FoodRats' `RootNavViewModel` already does the inclusive-pop redirect. Recommended upgrade: a `Route.Public` / `Route.Protected` marker on the existing sealed interface so gating is compiler-exhaustive and protected deep links get intercepted-then-resumed.
- **Migration:** **zero.** Type-safe routes, back stack, state restoration already in place.
- **Confirmed by DroidKaigi (file `02`):** the largest community KMP app uses this exact library — proof it scales.

### 🥈 2. Circuit (Slack) — file `07` — *only if a broader architecture move is on the table*
- **Best-in-class auth gating:** ships a first-party `AuthInterceptor` (`circuitx-navigation`) that rewrites a protected `Screen` → `LoginScreen(afterLoginDestination=…)`, plus `resetRoot(SignIn)` on sign-out. This is the cleanest "redirect-then-resume" primitive of anything reviewed.
- **Links:** DIY (parse URL → `List<Screen>`); iOS universal links unsampled.
- **Cost:** replaces androidx-nav **and** reframes FoodRats' MVI ViewModel layer into Presenter/UI/Screen. Pre-1.0, churning API. Proven in prod (Slack, Tivi).

### 🥉 3. Decompose — files `04` (library) + `03` (Confetti, real app) — *best architectural fit if rewriting*
- **Aligns with FoodRats' DDD/ports/MVI:** auth gating is a root component that subscribes to a session port and `replaceAll`s between an Auth subtree and a Main subtree — fully unit-testable in `commonTest`. Best state restoration; predictive back on iOS.
- **Links proven cross-platform:** Confetti ships verified Android App Links (`https://confetti-app.dev`) + iOS `applinks:` entitlement + `.onOpenURL`. **But** no URL-matcher in the library (DIY parser) and DIY iOS delivery — it doesn't *reduce* the deep-link effort vs. staying.
- **Cost:** full navigation rewrite + learning curve.

### Honorable mention / pass
- **Appyx (Bumble) — file `06`:** the *idea* I like most for auth — the root `Node`'s targets are `LoggedOut` vs `Main(user: User)`, so "authed without a user" is structurally unrepresentable, and `waitForChildAttached<>()` is exactly redirect-then-resume. Deep links work on both platforms in its sample. **But** the KMP line is effectively dormant (2.0.1, ~2 years old, "experimental") — too risky as a core dependency. *Steal the pattern, not the library.*
- **Voyager — file `05`: pass.** No deep-link support at all (issues open since 2022; iOS unanswered) — a **regression** on goal 1 — plus a Java-`Serializable` state-restoration tax that FoodRats' `@Serializable` routes don't satisfy, and no stable release in ~2 years.

## Patterns worth stealing regardless of the choice
1. **`Route.Public` / `Route.Protected` marker** on the existing sealed `Route` → gating becomes a compiler-exhaustive `when`, not a hand-maintained set. (file `08`)
2. **Pure-`commonMain` URL → route(s) parser**, fed by a thin per-platform shim — Tivi's `Navigator.applyDeeplink(Uri)` + `DeepLinker` flow is the cleanest example. (file `01`)
3. **Intercept-then-resume**: a deep link into a protected route should land on SignIn carrying an `afterLogin` destination, then continue. Circuit's `AuthInterceptor` (file `07`) and Appyx's `waitForChildAttached` (file `06`) are the two reference shapes.
4. **AASA / App Links setup is the real work** and is platform config, not Kotlin — Confetti (file `03`) is the end-to-end template for both `apple-app-site-association` + Android `autoVerify`.

## One-paragraph recommendation
Keep `navigation-compose`. Add a `Public`/`Protected` marker to `Route`, gate protected destinations in the existing `RootNavViewModel` with intercept-then-resume, declare `navDeepLink<T>` on the linkable routes, wire the Android `autoVerify` intent-filter + `apple-app-site-association`, and forward iOS URLs into `ExternalUriHandler` from the `AppDelegate` hook FoodRats already has. Revisit Circuit or Decompose only if a wider architecture change (beyond navigation) is independently justified.

## Score matrix
| Option | Goal 1: links | Goal 2: auth gating | iOS maturity | Migration cost | Verdict |
|---|---|---|---|---|---|
| **CMP Navigation (current)** | ✅ 2.9.2, additive | ✅ pattern + marker | ✅ official as of 2.9.2 | **none** | **Pick** |
| Circuit | ⚠️ DIY | ✅✅ first-party interceptor | ✅ prod (Tivi/Slack) | high (rewrite + MVI) | If bigger move |
| Decompose | ⚠️ DIY matcher, proven | ✅ tree-gate, testable | ✅ mature | high (rewrite) | If bigger move |
| Appyx | ✅ both (DIY parse) | ✅✅ node-as-gate | ⚠️ dormant 2yr | high (rewrite) | Steal pattern |
| Voyager | ❌ none | ⚠️ hand-rolled | ⚠️ restoration tax | high + regressive | Pass |
| Tivi (Circuit app) | ⚠️ custom-scheme only | ❌ none | reference only | — | Steal parser |
| DroidKaigi (app) | ❌ none | ❌ none | native SwiftUI islands | — | Confirms our lib |
| Confetti (Decompose app) | ✅ both platforms | ❌ inline dialog | reference only | — | Deep-link template |
