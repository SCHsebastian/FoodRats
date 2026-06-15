# Report — w3-shareable-cards-presentation (TERMINAL shareable-cards task)

Wires the shareable story cards into the app: share entry points, domain→props mappers, the
`ShareCardStringKey` i18n, and the `share` analytics. Consumes the renderer/launcher/decoder built by
`w3-shareable-cards-platform` and the `Fr*ShareCard` templates from `w3-shareable-cards-designsystem`.

## Prior work check

No prior presentation work existed on disk (grep for `toPlateCard`/`StoryCardRenderer`/
`ShareCardStringKey`/`PlateShared` in `feature/`+`shared/`+`core/domain/` returned nothing). The
renderer/launcher/decoder + their per-platform Koin bindings were present and unchanged. Started fresh.

## Scope decision (spec wins over the brief)

Spec §8.1 lists exactly three entry surfaces: **meal detail (in `:feature:feed`)** → plate card;
**stats award + streak (`:feature:stats`)** → award/streak cards; **the weekly-digest story
(`shared`)** → plate/streak card. The brief also mentioned `:feature:achievements` (share a badge)
and `:feature:meal`, but neither appears in §5/§8 — CHARTER says the spec wins, so those were NOT
touched. The §12 test list also only covers feed + stats (mappers + VM share flow).

**Recap-story-share (§8 row 4) deliberately DEFERRED** — see Decisions/Blockers. Feed + stats are
fully implemented, tested, and verified green.

## What was implemented

### Analytics (`:core:domain`)
- Added three `share` leaves to `core/domain/.../analytics/AnalyticsEvent.kt`: `PlateShared(mealId)`,
  `AwardShared(mealId)`, `StreakShared(streakDays)` — all `name = "share"`, `content_type ∈
  {plate,award,streak}`, `item_id` = meal id (text) or streak length (count). NO PII (reuses the
  existing `CrewInviteShared` shape).
- `AnalyticsTaxonomyTest`: added the three to `allEvents` + a dedicated
  `share_card_events_reuse_the_share_name_with_content_type_and_item_id` test (§11).

### i18n
- New shared `ShareCardStringKey` in `:core:i18n` (both feed + stats reuse it): on-card chrome
  (`BrandFooter`, `AwardBestMeal`, `AwardBestCook`, `StreakHeadline %1$d`, `StreakSubline`) + share
  toasts (`ShareSucceeded`, `ShareOpenedSheet`, `ShareFailed`). en + es populated in
  `core/i18n/.../composeResources/values{,-es}/strings.xml`.
- `FeedStringKey.ShareMeal`, `StatsStringKey.ShareAward` + `StatsStringKey.ShareScoreFormat`
  (`%1$s ★ · %2$d` — stats can't import `FeedStringKey.RatingSummary`). en + es populated in each
  feature's strings.xml.

### Design system
- Added `FrIcons.Share` (`Icons.Filled.Share`, in `material-icons-core` — iOS-safe, no vendoring
  needed).

### Share seam (`:core:data/share/`)
- `StoryShareController` interface + `StoryShareControllerImpl` (decode → render → launch) — the
  single TESTABLE dependency feed/stats VMs inject (the renderer/launcher are `expect class`/final,
  so a VM depending on them directly is un-fakeable). Bound per platform alongside the existing trio
  (`FoodRatsApplication.androidShareModule()` + `storyShareIosModule`).
- `RecordingStoryShareController` test double in `commonMain` (mirrors `RecordingAnalyticsTracker`).

### Feed (meal detail = plate share, spec table row 1)
- `FeedMealUi.toPlateCard(scoreLabel)` mapper + `PlateShareCardModel` (next to `FeedMealUi`).
- `PlateShareCardContent(meal, plate)` composable — the off-screen card content; resolves `scoreLabel`
  (`FeedStringKey.RatingSummary`) + `footerBrand` IN composition (the renderer composes this lambda
  off-screen, so `resolve` works — i18n stays out of the VM).
- `MealDetailViewModel`: `+storyShareController`, `ShareTapped`/`DismissShareOutcome` intents,
  `isPreparingShare`/`shareOutcome` state, `shareMeal()` handler (fires `PlateShared` only on a
  non-`Failed` outcome; NO `withContext`). `FeedModule` → explicit named `viewModel{}`.
- `MealDetailScreen`: `FrGlassPill(FrIcons.Share)` in the photo-hero action row (spinner while
  preparing) + an auto-dismissing `ShareOutcomeToast`.

### Stats (award + streak share, spec table rows 2–3)
- `MealAward.toAwardCard()` + `HeroStats.toStreakCard(todayEmote)` mappers +
  `AwardShareCardModel`/`StreakShareCardModel`; `AwardShareCardContent`/`StreakShareCardContent`.
- `StatsViewModel`: `+storyShareController/clock/zone/analytics`, `ShareAwardTapped(mealId)`/
  `ShareStreakTapped`/`DismissShareOutcome` intents, `isPreparingShare`/`shareOutcome` state,
  `shareAward()`/`shareStreak()` handlers (fire `AwardShared`/`StreakShared` only on non-`Failed`).
  `StatsModule` → explicit `viewModel{}` (was `viewModelOf`).
- `StatsScreen`: an `FrButton(ShareAward)` under the best-plate podium and under the streak hero
  (shown only when `personalStreak.days > 0`), + the `ShareOutcomeToast`.

### Module-verify
- `FeedModuleVerifyTest` + `StatsModuleVerifyTest` extraTypes gained `StoryShareController::class`
  (+ `AnalyticsPort::class` for stats).

## Tests added
- `FeedMealUiShareTest` (mapper), `MealDetailShareTest` (4 cases: success fires `PlateShared` w/ plate
  url; `Failed` fires nothing; fallback-sheet still fires; dismiss clears toast).
- `StatsShareMappersTest` (`toAwardCard`/`toStreakCard`, incl. `Streak(0)`), + 3 VM share cases in the
  existing `StatsViewModelTest`.
- `AnalyticsTaxonomyTest` share-card assertions.

## Verify (all green)

`./gradlew :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest :core:domain:testAndroidHostTest :core:designsystem:testAndroidHostTest`
```
> Task :feature:feed:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 16s
```
(FeedModuleVerifyTest 1/1, StatsModuleVerifyTest 1/1, MealDetailShareTest 4/4, FeedMealUiShareTest
2/2, StatsShareMappersTest 3/3, StatsViewModelTest 60/60, AnalyticsTaxonomyTest all green.)

`./gradlew :androidApp:assembleDebug`
```
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 7s
```

`./gradlew :core:data:compileIosMainKotlinMetadata`
```
BUILD SUCCESSFUL in 6s
```
(iOS metadata for the new `StoryShareController` + share module edit compiles; only pre-existing
KLIB/expect-actual Beta warnings.)

## Decisions
- **Shared `ShareCardStringKey` in `:core:i18n`** (spec §10 default) so feed + stats + a future recap
  reuse the same on-card text without duplication.
- **`StoryShareController` seam** introduced (not in the platform handoff): the renderer/launcher are
  `expect class`/final and un-fakeable, so a single common interface in the adapter layer makes the
  VMs unit-testable. It bundles decode+render+launch into one suspend call.
- **i18n in composition, not the VM**: card-chrome + scoreLabel are resolved inside the
  `@Composable` content lambda the renderer composes off-screen (the handoff's endorsed pattern), so
  the VM stays Compose-free and has no `withContext`.

## Blockers / deferred
- **Recap-story-share CTA (spec §8 row 4) NOT implemented.** `FrStoryScaffold` draws its full-size
  tap-zone gesture `Row` ON TOP of the scene content, so a Share button placed in a scene would be
  un-tappable (taps advance the story). A working CTA needs an overlay slot ABOVE the tap zones — a
  `:core:designsystem` change owned by another task and out of scope for this presentation task; the
  surface is also underspecified in the spec (no named mapper, no §12 test, reuses `StreakShared`).
  Deferred to avoid regressing the working recap player. The two concrete, tested entry points (feed
  plate, stats award/streak) are complete.

## MANUAL steps (restated for human.md — on-device share check + Xcode wiring)
- **On-device share smoke** (the ONLY check of the real Instagram intent / URL scheme +
  rasterization — spec §15.11): Android + iPhone, Instagram installed → Share opens IG Stories with
  the card as background; without Instagram → system sheet shows the PNG. Walk it on meal detail
  (plate), stats best-plate (award), stats streak (streak).
- **iOS Xcode:** add `iosApp/iosApp/StoryShareBridge.swift` to the `iosApp` target; confirm
  `Info.plist`'s `LSApplicationQueriesSchemes` includes `instagram-stories` in the built plist. (Swift
  glue not xcodebuild-verifiable here; matches the generated ObjC header per the platform handoff.)
