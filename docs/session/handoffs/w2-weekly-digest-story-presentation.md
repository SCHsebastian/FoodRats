# Handoff — `w2-weekly-digest-story-presentation` → Wave 3 `w3-shareable-cards`

The weekly-recap story player is done. Wave 3 (shareable plate/award/streak cards) reuses the SCENE
composables + the recap data model below. Everything lives in `shared/.../app/recap/`.

## Reusable scene composables (render-ready, chrome-free)

`RecapSceneView(scene: RecapScene, modifier)` in `RecapScenes.kt` dispatches to per-scene composables
(`CoverScene`, `TopMealScene`, `BestCookScene`/`CenteredScene`, `MostProlificScene`, `StreakScene`,
`BadgesScene`, `CuisinesScene`, `YourWeekScene`). Each is:
- **Domain-aware but port-free** — takes a `RecapScene` leaf (plain primitives + i18n keys), no use
  cases, no Compose state. Resolves all copy via `SharedStringKey` and reuses design-system atoms
  (`FrBadge`, `LocalFrSemanticColors`).
- **Deliberately free of player chrome** (no progress bar, no gestures) — that lives in
  `FrStoryScaffold`. So Wave 3 can render a single `RecapSceneView(scene)` (or one specific scene
  like `TopMealScene`) OFF-SCREEN to a bitmap with `expect/actual StoryCardRenderer` (spec §3.1)
  without dragging in the player. Wrap it in `FoodRatsTheme` + a fixed 9:16 / square size box.
- Each scene already paints its own full-bleed background (`SceneSurface`), so a captured frame is
  self-contained and on-brand.

**To turn a scene into a share card:** wrap `RecapSceneView(scene, Modifier.fillMaxSize())` in the
Wave-3 off-screen renderer at the target aspect ratio; add a branded footer/sticker overlay there
(don't bake sharing into the scenes — keep them reusable). The top-meal scene already loads the plate
photo via Coil `AsyncImage`.

## Recap data model

- `WeeklyRecap(scenes: List<RecapScene>)` — ordered, non-empty-only scenes.
- `RecapScene` sealed interface, leaves: `Cover(weekLabel)`, `TopMeal(photoUrl, dishName,
  authorName, score, ratingCount)`, `BestCook(memberName, avgScore)`, `MostProlific(memberName,
  postCount)`, `Streak(streakDays)`, `Badges(titleKeys: List<AchievementStringKey>)`,
  `Cuisines(collectedCount, totalCount)`, `YourWeek(streakDays, cuisinesCollected,
  ingredientsCollected)`. Each carries `kind: RecapSceneKind` (stable snake_case `wire` slug — also
  the analytics `scene_kind`).
- `assembleWeeklyRecap(stats, achievements, weekLabel, weekWindowStart/EndEpochMs)` — PURE; folds
  `StatsSnapshot` + `AchievementsSnapshot?` into the ordered recap. Reuse this for a card source if a
  card needs the same award/streak derivation. Tested in `WeeklyRecapAssemblerTest`.
- `WeeklyRecapStream` (`fun interface`) — the read seam; `statsAndAchievementsRecapStream(...)` is the
  production impl. If a share-card entry needs live recap data, depend on this, not the use cases.

## Analytics for share (when you add it)

Follow the existing pattern: a new `AnalyticsEvent` leaf (e.g. `share` predefined with
`content_type` = `plate_card`/`award_card`/`streak_card`, `item_id`), fired AFTER the share
`Result` Ok, snake_case, NO PII. The digest-story events
(`digest_story_opened`/`scene_viewed`/`completed`) + `DigestStorySource` are already in the taxonomy
+ `AnalyticsTaxonomyTest` + `TRACKING_PLAN.md`.

## Entry points already wired (extend, don't rebuild)

- Notification tap → `foodrats://app/digest/{weekStart}` → `parseDeepLink` → `Route.WeeklyStory`.
- Stats "See your week" button → `Route.WeeklyStory(fromNotification=false)`.
- §3.1 says "end of digest story" is a share entry point: add a final share CTA to the player's last
  scene (the `YourWeek` scene) once the renderer exists — call the Wave-3 `StoryShareLauncher` from
  there; the player already knows when it's on the last scene (`WeeklyStoryState.isLastScene`).

## Design-system atoms added (reuse for the player frame on share cards if wanted)

- `FrStoryProgressBar(segmentCount, currentIndex, currentProgress, ...)` — primitives only; pass a
  fixed progress to render a static frame. Catalog `atom.storyprogress`.
- `FrStoryScaffold(... scene = {})` — full-screen chrome with a scene slot. Catalog `atom.storyscaffold`.
