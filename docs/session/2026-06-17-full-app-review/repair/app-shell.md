# app-shell repair report

## app-shell-04 (APPLIED)

**File:** `shared/src/commonMain/kotlin/es/schsebastian/foodrats/app/root/RootNavViewModel.kt`

**What changed:**
- Line ~122: simplified `if (!route.requiresSession() || ready)` to `if (ready)`.
- Added a one-line KDoc comment above the `if` explaining the invariant: all routes returned by `parseDeepLink` are `Route.Protected`, so `requiresSession()` is always `true` and the `!route.requiresSession()` branch was dead code that could never be taken.
- Removed the now-unused `import es.schsebastian.foodrats.app.navigation.requiresSession` import.

**Verification of dead-code claim:**
`parseDeepLink` in `DeepLink.kt` can only return four route types: `Route.MealDetail`, `Route.CrewSettings`, `Route.WeeklyStory`, and `Route.InvitePreview`. All four are `Route.Protected` and return `true` from `requiresSession()`. `Route.Public` routes (`Splash`, `SignIn`) are never returned by the parser, confirming the first clause was always `false`.

**Tests added:** None — this is a LOW cleanup (dead code removal). Behavior is identical on every live path; existing `RootNavViewModelTest` cases continue to cover the deep-link stash/resume logic.

**Skipped fixes:**
- **app-shell-01** (push-banner separator i18n): skipped as instructed — `resolve()` is not callable in the `collect` lambda; deferred.
- **app-shell-02** (FrLog.installSink outside Koin guard): skipped — `MainViewController.kt` is in DIRTY_FILES.txt; do not touch.
- **app-shell-03** (double screen_view on Feed landing): skipped as instructed — GA4 event-shape decision; deferred.

**Build risk:** None. The simplification removes a boolean subexpression that was always `false`; the compiled bytecode for the `true` arm is unchanged. No public API change.
