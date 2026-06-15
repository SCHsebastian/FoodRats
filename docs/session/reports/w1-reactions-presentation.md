# Report — w1-reactions-presentation

**Status:** DONE, verified green. Terminal task of the reactions feature (domain → data → presentation).

## What was built

The feed UI for the daily-emote meal reaction: a react button on each feed list row that toggles
the viewer's reaction, the live reaction count as the compact "who reacted" affordance, the
`meal_reacted` analytics event, and full i18n + error surfacing.

### Prior work detected
No prior reactions-presentation work existed on disk (no report, no reaction code in
`FeedViewModel`/`FeedMealUi`/`FrFeedMealRow`). The diff already carried OTHER tasks' feed edits
(blind-voting, analytics base, reactions-data). Notably `FeedModuleVerifyTest.extraTypes` already
listed `MealReactionPort::class` (added by w1-reactions-data per its handoff) and `AnalyticsPort::class` —
so the graph check stayed green once I actually injected the port.

## Files changed

### Analytics taxonomy (`:core:domain`)
- `core/domain/.../analytics/AnalyticsEvent.kt` — new leaf `MealReacted(mealId, reactionKind)`
  → wire name `meal_reacted`, params `meal_id` + `reaction_kind` (the kind discriminator, e.g.
  `daily_glyph` — NOT the glyph, no PII). No suitable leaf pre-existed.
- `core/domain/.../analytics/AnalyticsTaxonomyTest.kt` — added `MealReacted` to `allEvents`.

### Feed presentation (`:feature:feed`)
- `presentation/feed/FeedViewModel.kt` —
  - Injected `reactions: MealReactionPort` (9th ctor arg) + kept `analytics: AnalyticsPort = NoopAnalyticsTracker` (10th, default keeps tests green).
  - `observeReactions()`: multiplexes ONE live `MealReactionPort.observe(crewId, mealId)` listener per
    visible meal, **deduped by mealId** + `combine`d — mirrors `MealDetailViewModel`'s per-author
    flow pattern. Derived purely from `state` (`state.map { it.meals.map { it.mealId } }`), no parallel
    `MutableStateFlow` (MVI single source of truth). Folds `count` + `hasReacted(viewer)` back into
    `FeedState.meals` via `FeedMealUi.withReactions(...)`.
  - `react(mealId)`: calls `toggle(crewId, mealId, reactorId, ReactionKind.DailyGlyph)`; on
    `Ok(Added)` fires `AnalyticsEvent.MealReacted` (NEVER on `Removed`, never in a use case — CHARTER
    rule 9). On `Err` sets `state.reactError`. No optimistic mutation — the live `observe()` stream
    re-emits the new count/flag. No use case (single-step port call per the handoff).
- `presentation/feed/FeedContract.kt` — `FeedIntent.ReactMeal(mealId)` + `FeedState.reactError: ReactionError?`; `DismissError` also clears `reactError`.
- `presentation/components/FeedMealUi.kt` — added `reactionCount: Int = 0`, `viewerReacted: Boolean = false`,
  and `withReactions(count, viewerReacted)`. The react glyph reuses the existing `dayEmote`
  (= `DailyEmote.forDay(meal.day)`, identical for every crew member — derived at render, never stored).
- `presentation/components/FrFeedMealRow.kt` — new feature-local `ReactionButton`: a pill carrying
  the day glyph + count, celebration-tinted + bordered when `viewerReacted`, with
  `Role.Button`/`selected`/`contentDescription` semantics. Its own `clickable` swallows the tap so it
  doesn't trigger the card's open-detail `onClick`. New `onReact: () -> Unit = {}` param.
- `presentation/feed/FeedScreen.kt` — wires `onReact = { vm.onIntent(FeedIntent.ReactMeal(ui.mealId)) }`
  and a second `FrErrorBanner` for `state.reactError`.
- `presentation/FeedErrorToStringKey.kt` — new exhaustive `ReactionError.toStringKey()` over all 6
  leaves (Read.Unauthorized/Unavailable + Toggle.Unauthorized/MealNotFound/Offline/Unavailable).
- `i18n/FeedStringKey.kt` + `composeResources/values{,-es}/strings.xml` — 7 new keys:
  `ReactionCta`, `ReactionCount` (`%1$d`), `ReactionsLabel` (`%1$d reactions` / `%1$d reacciones`,
  the accessible "who reacted" label), and the 4 reaction-error strings. Both locales populated.

### Tests (all green)
- `commonTest/.../feed/FeedViewModelTest.kt` — new `FakeMealReactionPort` (toggle is intent-idempotent,
  mutates its `byMeal` flow so the live stream reflects the toggle). 4 new cases: observed-reactions
  reflect into state; react-Added bumps count + viewerReacted + tracks exactly one `MealReacted`;
  react-Removed clears state + tracks NOTHING; react-failure populates `reactError`. Existing cases
  use `expectMostRecentItem()` and stay green (14 total).
- `commonTest/.../components/FeedMealUiTest.kt` — 3 new cases: reactions default to empty;
  `dayEmote` IS the reaction glyph; `withReactions` merges count + flag (15 total).
- `commonTest/.../ReactionErrorToStringKeyTest.kt` — new exhaustiveness test (locks all 6 arms).
- `androidHostTest/.../components/FrFeedMealRowTest.kt` — 3 new Robolectric cases: glyph shown / no
  count when unreacted; count shown when reacted; tapping the react button invokes `onReact` (7 total).

### Koin
- `di/FeedModule.kt` — `viewModel { FeedViewModel(...) }` rewritten with named args, `analytics = get()`
  EXPLICIT (so the Noop default never short-circuits graph resolution — CHARTER rule 9) and
  `reactions = get()`. `FeedModuleVerifyTest` already had both port types in `extraTypes`.

## Decisions

- **No new `:core:designsystem` atom.** The react affordance is domain-aware (renders the
  feed-meal's `dayEmote` glyph + count, resolves `FeedStringKey` i18n), so it lives feature-local in
  `FrFeedMealRow` — consistent with `FrFeedMealCard`/`FrScoreBadge` already being feature-owned and
  the rule that the catalog depends only on `:core:designsystem`. No catalog entry needed.
- **"Who reacted" = the count**, not a name list (roadmap §1.3 allows names-OR-count). Resolving
  reactor names would need a per-reaction `AccountReadPort` join on every feed row — heavy for a
  list; the count is the tasteful, cheap choice. The full reactor list is available on the meal-detail
  screen surface if a future task wants names there.
- **No optimistic UI.** `toggle` returns fast and the live `observe()` re-emits the new count, so the
  button updates from the single source of truth (the stream) rather than a hand-rolled optimistic
  copy that could drift from the server.
- **No use case** for toggle — single-step port call, per both handoffs. The one pure invariant
  (one-per-member) already lives on `MealReactions`.

## Verify (quoted)

`./gradlew :feature:feed:testAndroidHostTest`
```
> Task :feature:feed:compileAndroidHostTest
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 5s
```
Per-suite: FeedViewModelTest 14/14, FeedMealUiTest 15/15, FrFeedMealRowTest 7/7,
ReactionErrorToStringKeyTest 1/1 (all `failures="0" errors="0"`).

`./gradlew :core:domain:testAndroidHostTest --rerun-tasks` (touched the taxonomy)
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 16s
```
AnalyticsTaxonomyTest 5/5.

`./gradlew :androidApp:assembleDebug` (full Koin graph + composables compile)
```
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 8s
```

No `:core:designsystem:testAndroidHostTest` run — no designsystem atom was added (verification matrix
only requires it if an atom changes).

## Blockers
None.

## Pending MANUAL step (restated — already in docs/session/human.md)

Deploy the Firestore rules before reactions work against prod (the `reactions/{uid}` write is denied
until then; reads are unaffected as no data exists yet):

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```

(Shared with w1-streak-nudges-function, w1-blind-voting-data/presentation, w1-reactions-data — one
deploy covers all.)

## Handoff
None — terminal task of reactions; nothing downstream depends on this UI.
