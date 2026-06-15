# Handoff — w3-shareable-cards-designsystem

The design-system share cards are DONE and on `develop` (uncommitted, with the rest of the wave work).
Downstream tasks (`w3-shareable-cards-platform`, `w3-shareable-cards-presentation`) consume these.

## Where

`core/designsystem/src/commonMain/kotlin/es/schsebastian/foodrats/core/designsystem/templates/FrShareCard.kt`

## EXACT public signatures

```kotlin
package es.schsebastian.foodrats.core.designsystem.templates

// Presentation enum — Square = 1:1, Story = 9:16. No domain type.
enum class ShareCardFormat { Square, Story }

@Composable
fun FrPlateShareCard(
    plate: ImageBitmap?,        // decoded plate photo; null -> branded dayEmote placeholder
    dishName: String,
    authorName: String,
    scoreLabel: String?,        // pre-formatted "8.4 ★ · 5" (FeedStringKey.RatingSummary); null hides the score pill
    dayEmote: String,           // DailyEmote.forDay(day) glyph
    footerBrand: String,        // resolve(ShareCardStringKey.BrandFooter)
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)

@Composable
fun FrAwardShareCard(
    plate: ImageBitmap?,
    awardLabel: String,         // resolve(ShareCardStringKey.AwardBestMeal / AwardBestCook)
    dishName: String,
    authorName: String,
    scoreLabel: String?,
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)

@Composable
fun FrStreakShareCard(
    streakDays: Int,            // the giant hero numeral
    headline: String,           // resolve(ShareCardStringKey.StreakHeadline, streakDays) — e.g. "14-day streak 🔥"
    subline: String,            // resolve(ShareCardStringKey.StreakSubline)
    dayEmote: String,
    footerBrand: String,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
)
```

These match spec §4.1 verbatim.

## Canvas dimensions

- The card itself is **ratio-locked, not pixel-locked**: it `fillMaxWidth().aspectRatio(ratio)`.
  - `ShareCardFormat.Square` → `1f` (1:1)
  - `ShareCardFormat.Story` → `9f / 16f` (9:16)
- The **fixed export pixel size** lives with the platform renderer, not here (spec §5):
  `STORY_WIDTH_PX = 1080`, `STORY_HEIGHT_PX = 1920` (Story); `1080 × 1080` (Square). The
  `w3-shareable-cards-platform` task owns those constants next to `StoryCardRenderer`.
- The platform renderer composes `Fr…ShareCard(format = …)` inside a container measured at the fixed
  pixel size and captures it. Because the card is `fillMaxWidth()` + `aspectRatio`, it fills that
  container exactly. Layout is deterministic (no scroll, no animation), so a one-shot off-screen
  capture renders the whole tree.

## How the image is supplied (the slot contract)

- The card takes a **decoded `androidx.compose.ui.graphics.ImageBitmap?`** — NOT a URL, NOT a painter,
  NOT `AsyncImage`. The card never does I/O / network.
- `plate == null` → the card renders a branded placeholder (the `dayEmote` on a solid `surfaceVariant`).
  So the platform task can safely pass `null` on a decode failure and never show a broken image.
- **Platform task (`w3-shareable-cards-platform`) must:** pre-decode the short-lived signed plate URL
  into an `ImageBitmap` BEFORE rendering (Coil `ImageRequest` + `execute()` reusing the installed
  singleton `ImageLoader`), then call `StoryCardRenderer.renderToPng { FrPlateShareCard(plate = bitmap, …) }`.
  On decode failure, render with `plate = null`.

## What presentation (`w3-shareable-cards-presentation`) must provide

The cards take **finished strings** — the design system has no i18n surface and creates NO
`ShareCardStringKey`. Presentation/shared must:

1. Create `ShareCardStringKey` + en/es strings per spec §10 (`BrandFooter`, `AwardBestMeal`,
   `AwardBestCook`, `StreakHeadline %1$d`, `StreakSubline`) and the feed/stats button/toast keys.
2. Resolve them and pass finished strings into the cards (`footerBrand`, `awardLabel`, `headline`,
   `subline`). Build `scoreLabel` at the call site via `FeedStringKey.RatingSummary` and pass it in
   (or `null` when there are no ratings).
3. Map domain → primitives in the **feature presentation layer** (e.g. `FeedMealUi.toPlateCard()`,
   `MealAward.toAwardCard()`, `HeroStats.toStreakCard()`) — NOT in the design system, which can't see
   domain types. Resolve `dayEmote` via `DailyEmote.forDay(day)`.
4. Drive `format`: `ShareCardFormat.Story` for the share-to-Stories export, `Square` for an in-app
   preview if desired.

## Catalog

Three entries already added to `catalogApp/.../stories/TemplateStories.kt` under
`CatalogGroup.TEMPLATES` (`template.{plate,award,streak}ShareCard`). They paint a static gradient
`ImageBitmap` (no Coil in the catalog by design). Nothing for downstream to do here.

## Verify commands (both green at handoff time)

- `./gradlew :core:designsystem:testAndroidHostTest` — 56 tests, BUILD SUCCESSFUL
- `./gradlew :catalogApp:assembleDebug` — BUILD SUCCESSFUL
