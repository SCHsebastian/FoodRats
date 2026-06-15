# Report — w3-recap-share-cta (TERMINAL)

Closes the gap the shareable-cards presentation task deferred: a "Share this recap" CTA on the
weekly-recap story player. `FrStoryScaffold`'s full-size gesture tap-zone `Row` used to consume every
touch, so a button placed inside a scene was un-tappable. Added a design-system overlay action slot
that sits ABOVE the tap zones (so a click in it shares instead of advancing) + wired the recap
scene → `Fr*ShareCard` share flow through the existing `StoryShareController`.

## Prior work check

Task was `doing` (cut off, re-dispatched). No prior recap-share work on disk — grep for
`ShareScene` / `RecapShareCard` / `RecapShared` / `action =` in `shared/.../recap/` + the scaffold
returned nothing. Started fresh. The recap player (w2), the share platform/controller (w3-platform),
the `Fr*ShareCard` templates + `ShareCardStringKey` (w3-designsystem/presentation) were all present
and unchanged — reused as-is.

## What was implemented

### Design system — overlay action slot (`:core:designsystem`)
- `FrStoryScaffold` gained an `action: (@Composable () -> Unit)? = null` slot, drawn AFTER (above) the
  tap-zone `Row` so its own pointer input wins; bottom-anchored, inset under the system bars. `null`
  (default) keeps every existing caller's advance/pause gestures untouched. Primitives-only — takes a
  composable slot, no domain types.
- `FrStoryScaffoldTest` (NEW, 3 cases): a click in the action slot fires the button and does NOT
  advance/rewind; a tap on the bare right region still advances; composes with no action slot.
- Catalog: extended `atom.storyscaffold` (AtomStories.kt) with a second `CatalogScene` showing the
  scaffold WITH an action-slot share button.

### Analytics (`:core:domain`)
- New `AnalyticsEvent.RecapShared(sceneKind)` leaf: `name="share"`, `content_type="recap"`,
  `item_id` = the scene-kind wire slug (`top_meal`/`streak`/`your_week` — already the snake_case
  PII-free analytics discriminator; the recap `TopMeal` scene has no `MealId`, so reusing
  `PlateShared(mealId)` was wrong — `RecapShared` keeps it id-free and PII-free). Reuses the `share`
  name per spec §7, distinguished by `content_type`.
- `AnalyticsTaxonomyTest`: added `RecapShared` to `allEvents` + the share-card reuse assertion
  (`content_type ∈ {plate, award, streak, recap}`). 6/6 green.
- `TRACKING_PLAN.md`: added the recap `share` row.

### Recap share wiring (`shared/.../app/recap/`)
- `RecapShareCard.kt` (NEW): a `RecapShareCard` sealed model (`Plate` / `Streak`), a pure
  `RecapScene.toShareCard(todayEmote)` mapper (TopMeal→plate, Streak/YourWeek→streak; cover/best-cook/
  most-prolific/badges/cuisines → `null`, exhaustive `when`), `RecapScene.isShareable()`, and the
  `@Composable RecapShareCardContent(card, plate)` that wraps the right `Fr*ShareCard` in
  `FoodRatsTheme` and resolves its own chrome (reuses `ShareCardStringKey` + the existing shared
  `recap_top_meal_score` string) — i18n stays out of the VM.
- `WeeklyStoryContract`: state gained `isPreparingShare` + `shareOutcome` + the derived
  `canShareCurrentScene`; new `ShareScene` / `DismissShareOutcome` intents; `ShareOutcomeUi` enum.
- `WeeklyStoryViewModel`: injected `storyShareController` + `clock` + `zone`; `shareScene()` handler —
  maps the current scene → card, pauses the clock, calls `StoryShareController.share(...)` (controller
  owns IO; NO `withContext` in the VM), fires `RecapShared` ONLY on a non-`Failed` outcome (CHARTER
  §9), sets the outcome toast, resumes. Non-shareable scene → no-op.
- `WeeklyStoryScreen`: passes the `action` slot (a `FrButton` "Share this recap" / spinner while
  preparing, only when `canShareCurrentScene`) into the scaffold + an auto-dismissing share-outcome
  toast (mirrors the feed/stats toast). Wrapped the body in a `Box(fillMaxSize)` so the toast overlays.
- `WeeklyStoryModule`: explicit `viewModel { … }` now passes the controller/clock/zone (already
  explicit, not `viewModelOf`, per the analytics convention).
- `WeeklyStoryModuleVerifyTest`: added `StoryShareController::class` to `extraTypes`.

### i18n
- New `SharedStringKey.RecapShareCta` (`recap_share_cta`): en "Share this recap" / es "Compartir
  resumen" in BOTH `shared/.../values{,-es}/strings.xml`. The on-card chrome + share-outcome toasts
  reuse the existing `ShareCardStringKey` (`:core:i18n`) — no new card strings needed.

## Tests added/extended
- `FrStoryScaffoldTest` (3, NEW) — slot-click vs advance + no-slot compose.
- `WeeklyStoryViewModelTest` (+6 share cases: invokes controller & tracks `RecapShared`; top-meal
  passes its plate URL; `Failed` → failed toast & no analytics; fallback sheet still fires; non-
  shareable scene = no-op; dismiss clears toast). 13/13 total green.
- `AnalyticsTaxonomyTest` — `RecapShared` coverage. 6/6.

## Verify (all green)

`./gradlew :core:domain:testAndroidHostTest :core:designsystem:testAndroidHostTest`
```
> Task :core:designsystem:compileAndroidHostTest
> Task :core:designsystem:testAndroidHostTest
BUILD SUCCESSFUL in 6s
```
(FrStoryScaffoldTest 3/3, AnalyticsTaxonomyTest 6/6 — confirmed via the JUnit XML.)

`./gradlew :shared:testAndroidHostTest`
```
> Task :shared:compileAndroidHostTest
> Task :shared:testAndroidHostTest
BUILD SUCCESSFUL in 16s
```
(WeeklyStoryViewModelTest 13/13, WeeklyStoryModuleVerifyTest 1/1 — confirmed via the JUnit XML.)

`./gradlew :catalogApp:assembleDebug :androidApp:assembleDebug`
```
> Task :catalogApp:assembleDebug
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 4s
```

`./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS-reachable common code compiles)
```
> Task :shared:compileKotlinIosSimulatorArm64
BUILD SUCCESSFUL in 14s
```
(only pre-existing expect/actual-Beta + UIKitView deprecation warnings.)

## Decisions
- **`RecapShared(sceneKind)` not `PlateShared`.** The recap `TopMeal` scene carries no `MealId`
  (just photoUrl/dishName), so the spec's `PlateShared(mealId)` doesn't fit. Per §7 ("add an
  analogous `share` leaf per card kind"), `content_type=recap` with the PII-free scene-kind slug as
  `item_id` is the honest, no-PII mapping for both recap card kinds.
- **Overlay slot is `action: (@Composable)? = null`, drawn after the tap zones.** Z-order (not
  pointer-pass-through tricks) is what makes the button win the click — simplest primitives-only fix;
  zero impact on existing callers (default `null`).
- **Shareable scenes = TopMeal / Streak / YourWeek only.** Cover/best-cook/most-prolific/badges/
  cuisines are not shareable (no first-class card and/or another member's data — privacy); the CTA is
  hidden for them via `canShareCurrentScene`.
- **Reused `ShareCardStringKey` + the existing `recap_top_meal_score` string** for card chrome — no
  recap-specific card text needed; only the button label is new.

## Blockers / deferred
None. All required + optional verifications green.

## MANUAL steps (restated — already in human.md)
- **On-device share smoke** (the ONLY check of the real Instagram intent / URL-scheme +
  rasterization). human.md item updated to add the FOURTH surface: open the weekly-recap story player
  (notification tap or stats "See your week"), advance to a shareable scene (top-meal / streak /
  your-week), tap **"Share this recap"** in the overlay action bar, confirm the card rasterizes and
  Instagram Stories opens (or the system sheet without Instagram). Depends on the iOS Xcode wiring
  (`StoryShareBridge.swift` added to the target) + `instagram-stories` in the built plist, both
  already tracked in human.md from w3-shareable-cards-platform.
