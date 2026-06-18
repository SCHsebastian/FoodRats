# App-Shell Module Review

**Scope:** `shared/src`, `androidApp/src`, `baselineprofile/src`

## Health Summary

The app-shell is structurally sound. The `RootNavViewModel` correctly uses `Mutex`-serialized stage transitions and a CONFLATED `DeepLinkBus` to handle cold-start deep links, the `navigateTopLevel` extension correctly tracks the live bottom of the back stack instead of the stale configured-start, and `EventsEffect` correctly defers effects with `repeatOnLifecycle(RESUMED)` rather than dropping them. The MVI contract is clean (no parallel `MutableStateFlow`, no `withContext` in VMs). Four real issues found: one i18n rule violation (hardcoded em-dash separator in a user-visible string), one `FrLog.installSink` call outside the Koin idempotency guard on iOS that reruns on every UIViewController recreation, one analytics double-tracking on every landing to the Feed tab, and one dead code branch in `requiresSession()` that can never be true given the current deep-link parser contract.

---

## Findings

### app-shell-01 — Hardcoded `" — "` separator in `reminderToSnackbarMessage` (ARCHITECTURE / HIGH)

**File:** `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/notifications/InAppPushBanner.kt:65`

**Evidence:**
```kotlin
.joinToString(separator = " — ")
```
This em-dash separator is user-visible (it appears in the snackbar message) but is assembled in a non-Composable function, bypassing the `resolve(StringKey)` pipeline. The project rule says **all user-visible text including glyph/punctuation separators must go through `resolve(StringKey)`**. The current design locks the function to non-Composable context (it returns `String`), making it impossible to call `resolve()` inline.

**Fix:** Move the string assembly into the `LaunchedEffect` collect site inside `InAppPushBanner`, where `@Composable` context is available. Add a `SharedStringKey.PushNotificationSeparator` (value `" — "`) and use `resolve(SharedStringKey.PushNotificationSeparator)` there. The pure helper can remain for the non-Composable part (filtering blank fields), but the separator join should happen in the Composable with a resolved string.

**AutoFixable:** false  
**Risk:** low

---

### app-shell-02 — `FrLog.installSink` called outside the Koin idempotency guard in `MainViewController` (CORRECTNESS / MEDIUM)

**File:** `shared/src/iosMain/kotlin/es/schsebastian/foodrats/MainViewController.kt:104`

**Evidence:**
```kotlin
if (KoinPlatform.getKoinOrNull() == null) {
    startKoin { … }
}
// ← FrLog.installSink is HERE, outside the guard
FrLog.installSink(CrashReporterLogSink(KoinPlatform.getKoin().get<CrashReporter>()))
```
The idempotency guard prevents `startKoin` from running more than once. `FrLog.installSink` is outside it, so on every UIViewController recreation (scene reattach, backgrounded-and-resumed) it reinstalls the sink. Since `CrashReporter` is a Koin `single`, the same instance is reassigned each time — no observable bug today. However, if `FrLog.sink` were ever changed to hold a list of sinks rather than a single reference, this would accumulate duplicates. More practically, it fires a Koin `get()` on every configure-block run, which is unnecessary overhead.

**Fix:** Move `FrLog.installSink(…)` inside the `if (KoinPlatform.getKoinOrNull() == null)` block.

**AutoFixable:** true  
**Risk:** low

---

### app-shell-03 — Double `screen_view` event on every Feed landing (PERFORMANCE / MEDIUM)

**File:** `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/navigation/NavGraph.kt:66,286`

**Evidence:**
```kotlin
// Line 66 — outer NavHost
TrackScreenViews(controller)   // fires "main" when Route.Main is navigated to

// Line 286 — inside MainScaffold
LaunchedEffect(selectedTab) {  // fires immediately on first composition with selectedTab=Feed
    analytics.track(AnalyticsEvent.ScreenViewed(ScreenName("feed")))
}
```
Every time the user lands on the Feed tab (first open, sign-in, deep-link return), two `screen_view` events fire back-to-back: one for `"main"` (from `TrackScreenViews` detecting the `Route.Main` back-stack entry) and one for `"feed"` (from `LaunchedEffect(selectedTab)` firing immediately on first composition). This inflates `screen_view` counts in GA4 and makes the `"main"` screen name meaningless.

**Fix:** In `MainScaffold`, guard the analytics track so it only fires on subsequent tab changes rather than on initial composition. One way: track the previously selected tab with a `var previous by remember { mutableStateOf<MainTab?>(null) }`, emit only when `selectedTab != previous`, then assign `previous = selectedTab`. Alternatively, suppress the `"main"` route from `TrackScreenViews` by returning early when the route maps to the empty-route `MainTab` container.

**AutoFixable:** false  
**Risk:** low

---

### app-shell-04 — Dead code: `!route.requiresSession()` branch in `observeDeepLinks` is unreachable (CLEANUP / LOW)

**File:** `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/root/RootNavViewModel.kt:122-124`

**Evidence:**
```kotlin
if (!route.requiresSession() || ready) {
    emit(RootNavEffect.NavigateDeepLink(route))
```
`parseDeepLink` returns only `Route.MealDetail`, `Route.CrewSettings`, `Route.WeeklyStory`, and `Route.InvitePreview` — all of which are `Route.Protected`, so `requiresSession()` is always `true` for any non-null result. The `!route.requiresSession()` branch evaluates to `false` on every call and can never be taken with the current parser contract.

**Fix:** Remove the `!route.requiresSession()` guard and simplify the condition to `if (ready)`. Add a KDoc note: "All parseable deep links are `Route.Protected`; stash if not ready, otherwise navigate immediately." If a public deep-linkable route is ever added to `parseDeepLink`, the guard can be re-added at that time.

**AutoFixable:** true  
**Risk:** low
