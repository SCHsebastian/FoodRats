# Report — w3-shareable-cards-designsystem

**Task:** Design-system layer of shareable story cards (the `Fr*ShareCard` composables).
**Scope:** `:core:designsystem` only — pure, primitives-in. NOT the bitmap renderer, share launcher, or
feature wiring (those are `w3-shareable-cards-platform` / `w3-shareable-cards-presentation`).
**Status:** DONE. Both verify commands green.

## Prior-work check

No prior work existed for THIS task — no `Fr*ShareCard.kt`, no report/handoff. The recap recap
(`FrStoryScaffold`, `FrStoryProgressBar`, `FrBadge`) was already complete on `develop` and served as
the convention reference (it explicitly notes "what lets Wave 3 reuse a single scene composable …
as a rendered-to-bitmap share card"). Nothing to finish; built fresh.

## What was built (spec §4.1, exactly)

`core/designsystem/src/commonMain/.../templates/FrShareCard.kt` — one file holding:

- `enum class ShareCardFormat { Square, Story }` — presentation enum (no domain type). `Square` → 1:1,
  `Story` → 9:16. Mapped to an `aspectRatio()` float so the on-screen catalog preview matches the
  off-screen export ratio.
- `FrPlateShareCard(...)` — meal/plate highlight: full-bleed plate photo (or branded `dayEmote`
  placeholder when `plate == null`), bottom protection gradient, dish headline, author subline,
  optional score pill, brand footer.
- `FrAwardShareCard(...)` — same as plate plus a `celebration`-colored award banner ("Best meal" /
  "Best cook" — label passed in).
- `FrStreakShareCard(...)` — no photo; the streak count is the hero in `streakHot`, headline + subline +
  brand footer on a solid `surface`.

All three take **primitives / `ImageBitmap?` only** — no domain types. All user-visible text is a
**pre-resolved String parameter** (the design system has no i18n surface; the feature resolves
`StringKey`s and passes finished strings — confirmed: `find core/designsystem/src -path '*i18n*'`
returns nothing).

### Token / color conformance

- Surface = `RoundedCornerShape(Radius.lg)`, padding from `Spacing.*`, type from
  `MaterialTheme.typography`, brand palette inherited via `MaterialTheme.colorScheme` (Iron & Ember
  propagates automatically).
- Meaning colors via `LocalFrSemanticColors.current` exactly as the spec dictates: streak count =
  `streakHot`, award banner = `celebration`/`onCelebration`, the white-on-photo protection gradient +
  text use `scrim`/`onScrim`. **No raw `Color(0x…)` anywhere in the card file.**
- The image is supplied as a decoded `ImageBitmap?` slot (`Image(bitmap = …)`), never loaded from
  network in the card — this is the primitive boundary that keeps the cards catalogable AND lets the
  platform renderer pre-decode the signed URL off-screen (spec §5: `AsyncImage` can't resolve in a
  one-shot capture).
- Layout is deterministic: fixed-ratio `Box` with a backdrop + bottom-anchored column, no scroll, no
  animation — exactly what the off-screen renderer needs.

## Catalog (required by the catalog-per-`Fr*` rule)

`catalogApp/.../stories/TemplateStories.kt`:
- Three `CatalogEntry`s under `CatalogGroup.TEMPLATES`, IDs `template.plateShareCard` /
  `template.awardShareCard` / `template.streakShareCard`.
- Each story uses `CatalogSceneSplit` (light/dark side-by-side) for the 9:16 Story variant and
  `CatalogScene` for the Square variant, covering: with-photo, no-photo placeholder, long title
  (wrap/ellipsize), null score, 1-day streak.
- The catalog depends only on `:core:designsystem` (no Coil/Koin/features by design), so the stories
  paint a **static gradient `ImageBitmap`** via `CanvasDrawScope().draw { … }` — never a URL. This is
  precisely why the card takes `plate: ImageBitmap?`.

## Tests

`core/designsystem/src/androidHostTest/.../FrShareCardTest.kt` — 5 Robolectric Compose-UI tests
(mirrors `FrBadgeTest` / `FrStoryProgressBarTest`, `createComposeRule` v2):
- plate card renders dish + author + score + brand;
- plate card with `plate=null` + `scoreLabel=null` still renders chrome;
- award card renders the award banner + dish + author;
- streak card renders the day count + headline + subline + brand;
- 1-day streak still renders.

Test note: `ImageBitmap(64,64)` NPEs under Robolectric (`Bitmap.createBitmap` returns null in the
host JVM — a shadow limitation, NOT a card bug). Switched to
`Bitmap.createBitmap(...).asImageBitmap()`, which Robolectric shadows correctly. Green after that.

## Verification (both quoted in full at the bottom)

1. `./gradlew :core:designsystem:testAndroidHostTest` → **BUILD SUCCESSFUL**, `56 tests` (51 prior + 5
   new), 0 failed.
2. `./gradlew :catalogApp:assembleDebug` → **BUILD SUCCESSFUL** (confirms the three catalog entries
   compile).

```
> Task :core:designsystem:testAndroidHostTest
BUILD SUCCESSFUL in 6s
47 actionable tasks: 4 executed, 43 up-to-date
```
```
> Task :catalogApp:assembleDebug
BUILD SUCCESSFUL in 2s
73 actionable tasks: 9 executed, 64 up-to-date
```

## Files changed

- `core/designsystem/src/commonMain/.../templates/FrShareCard.kt` (new)
- `core/designsystem/src/androidHostTest/.../FrShareCardTest.kt` (new)
- `catalogApp/src/main/.../stories/TemplateStories.kt` (edited — 3 entries + 3 story composables +
  static-bitmap helper)
- `docs/session/reports/w3-shareable-cards-designsystem.md` (this)
- `docs/session/handoffs/w3-shareable-cards-designsystem.md` (signatures + canvas dims + slot contract)

## Decisions / notes for downstream

- One file `FrShareCard.kt` holds all three cards + `ShareCardFormat` + the shared private building
  blocks, instead of three files. The cards share ~80% of their chrome (surface, scrim column,
  headline, score pill, footer); splitting them would duplicate it. They're still three independent
  public composables.
- The square variant exists per spec (§4.1: "each has a square 1:1 export variant and a Story 9:16
  variant"). The platform renderer chooses which to render off-screen.
- `ShareCardStringKey` (the i18n enum from spec §10) is NOT created here — it's a feature/shared
  concern, and the design system has no i18n surface by design. The cards take finished strings.
- No new dependency added (no Coil in the card; `aspectRatio` + `ImageBitmap` are standard Compose).
- Out of scope and untouched: `StoryCardRenderer`, `StoryShareLauncher`, the analytics leaves,
  feature mappers, manifest/plist.
