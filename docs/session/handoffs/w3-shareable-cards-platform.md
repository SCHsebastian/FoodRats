# Handoff — w3-shareable-cards-platform

The platform layer (off-screen card → PNG renderer + Instagram-Stories/system-sheet launcher) is
DONE and compiles on Android + iOS. `w3-shareable-cards-presentation` consumes the API below.

## Where

`core/data/src/commonMain/kotlin/es/schsebastian/foodrats/core/data/share/`
(actuals in `androidMain/.../share/` + `iosMain/.../share/`).

## The API your ViewModel calls (props in → share action)

Inject all three via Koin (already bound: Android `FoodRatsApplication.androidShareModule()`, iOS
`storyShareIosModule` in `MainViewController`):

```kotlin
class StoryCardRenderer       // expect; suspend fun renderToPng(widthPx, heightPx, content): ByteArray
class StoryShareLauncher      // expect; fun shareToStories(imagePng: ByteArray): StoryShareOutcome
class PlateImageDecoder       // common; suspend fun decode(url: String?): ImageBitmap?
enum class StoryShareOutcome { OpenedInstagram, OpenedFallbackSheet, Failed }
```

The full share handler in a ViewModel (mirror the spec §8.2 sequence):

```kotlin
// 1. (optional) toggle a transient isPreparingShare = true in State
// 2. decode the plate URL to a bitmap (null on failure → card shows a branded placeholder)
val plate: ImageBitmap? = plateImageDecoder.decode(model.photoUrl)
// 3. render the chosen Fr*ShareCard at Story size, off-screen, to PNG
val png: ByteArray = storyCardRenderer.renderStory {   // 1080×1920; renderSquare {} for 1:1
    FoodRatsTheme {                                     // cards compose against MaterialTheme/FrSemanticColors
        FrPlateShareCard(
            plate = plate,
            dishName = model.dishName,
            authorName = model.authorName,
            scoreLabel = scoreLabel,                    // resolve(FeedStringKey.RatingSummary, …) or null
            dayEmote = model.dayEmote,
            footerBrand = resolve(ShareCardStringKey.BrandFooter),  // YOU create this key (presentation §10)
            format = ShareCardFormat.Story,
        )
    }
}
// 4. hand it to the OS — never throws, always an outcome
val outcome = storyShareLauncher.shareToStories(png)
// 5. on OpenedInstagram/OpenedFallbackSheet → fire the `share` analytics event + show a toast;
//    on Failed → show ShareFailed. (Analytics leaves + toast StringKeys are YOUR job — see below.)
// 6. set isPreparingShare = false
```

Notes:
- `renderToPng` is `suspend` (call it from a use case / VM coroutine, NOT inside `update {}`). It
  switches dispatchers internally (Compose capture on main, PNG encode on io) — do NOT wrap it in
  your own `withContext`.
- `renderToPng` has NO default sizes (an `expect` actualized via typealias can't carry defaults).
  Use `renderStory { … }` (1080×1920) or `renderSquare { … }` (1080×1080) extensions, or pass the
  `STORY_WIDTH_PX` / `STORY_HEIGHT_PX` / `SQUARE_SIDE_PX` constants explicitly.
- `FrPlateShareCard` / `FrAwardShareCard` / `FrStreakShareCard` live in `:core:designsystem`
  templates — exact signatures in `w3-shareable-cards-designsystem.md`. Wrap them in `FoodRatsTheme`
  inside the `content` lambda so the Iron & Ember palette + `FrSemanticColors` resolve during capture.

## How to provide the plate image

`PlateImageDecoder.decode(url)` takes the SAME short-lived signed plate URL the feed already uses
(`FeedMealUi.photoUrl`, resolved via `ImageUrlPort`/`mintPlateUrls`). It reuses the installed
singleton Coil `ImageLoader`, so caching/HTTP engine are shared. Returns `null` on any failure — pass
that straight into the card's `plate` slot; the card renders a branded `dayEmote` placeholder, never
a broken image. For `FrStreakShareCard` (no photo) skip the decode entirely.

## What YOU (presentation task) still own

- `ShareCardStringKey` + en/es strings (`BrandFooter`, `AwardBestMeal`, `AwardBestCook`,
  `StreakHeadline %1$d`, `StreakSubline`) — spec §10. The cards take FINISHED strings.
- `FeedStringKey.ShareMeal`, `StatsStringKey.ShareAward`, `SharedStringKey.ShareSucceeded /
  ShareOpenedSheet / ShareFailed` (toasts) — spec §10.
- The analytics leaves `PlateShared(mealId)` / `AwardShared(mealId)` / `StreakShared(streakDays)` in
  `:core:domain/analytics/AnalyticsEvent.kt` (name `"share"`, `content_type ∈ {plate,award,streak}`,
  `item_id`, NO display names) + `AnalyticsTaxonomyTest` assertions — spec §7/§11. Fire from the VM
  ONLY on `OpenedInstagram`/`OpenedFallbackSheet`, never on `Failed`, never in a use case.
- The feature→primitive mappers (`FeedMealUi.toPlateCard()`, `MealAward.toAwardCard()`,
  `HeroStats.toStreakCard()`) + the share buttons/intents + VM `*ModuleVerifyTest` `extraTypes`
  additions (`StoryCardRenderer`, `StoryShareLauncher`, `PlateImageDecoder`, `AnalyticsPort`).

## MANUAL verification the user must do (not codeable / not xcodebuild-verifiable here)

- **On-device share smoke** — the ONLY check of the real intent/URL-scheme + rasterization (spec
  §15.11). Android + iPhone, with Instagram installed: Share → Story opens with the card as
  background; without Instagram: system sheet shows the PNG.
- **iOS Xcode:** add `iosApp/iosApp/StoryShareBridge.swift` to the `iosApp` target; confirm
  `Info.plist`'s `LSApplicationQueriesSchemes` (instagram-stories) is in the built plist. The Swift
  glue is not xcodebuild-verified here but matches the generated ObjC header
  (`storyShare:(FRSInt *(^)(FRSKotlinByteArray *))`).
