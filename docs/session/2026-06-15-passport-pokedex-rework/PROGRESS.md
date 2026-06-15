# Passport / Pokédex / Achievement-icons rework — 2026-06-15

## Brief (user)
1. Passport view → **fixed 4 items per row** + **load little by little** (lazy/pagination) to stop wasting memory.
2. **Develop distinct icons** for different achievements.
3. **Pokédex** (ingredient bingo) → a **new way of viewing**, NOT the same layout as the food passport.
4. **Food passport** → use **country-flag icons**.

## Map of the existing code
- "Passport view" = `feature/stats/.../presentation/passport/PassportScreen.kt` — a `LazyColumn` holding two `FlowRow` sections:
  - `FrCuisinePassport` (food passport, 14 cuisines) — `FrBadge` + globe icon, no flags.
  - `FrIngredientBingo` (the "Pokédex", ~226 ingredients) — `FrBadge` + fork icon.
- Memory problem: a `FlowRow` inside a `LazyColumn` item composes **every** child at once → ~226 `FrBadge` Canvas/Surface trees alive even off-screen.
- Achievements = separate `feature/achievements` (`LazyVerticalGrid(3)`, `FrBadge`, 9 `AchievementIcon` glyphs). 15 catalog rows; `first_plate` shares `Plate` with the meal-count tiers, `best_cook` reuses `Crown` (collides with stats "best plate").

## Decisions
- **One `LazyVerticalGrid(GridCells.Fixed(4))`** for the whole Passport screen → windowed composition = the real "load little by little" fix (off-screen cells dispose). Full-span items for headers.
- **Food passport cell** = new `FrCuisineFlagCell`: rounded flag tile (full-colour when collected, **desaturated + dimmed** when locked via `ColorMatrix` saturation 0), name, collected-date/Locked caption. 4 per row.
- **Flags** = new `core/designsystem/atoms/FrFlags.kt`: simplified, recognisable multi-colour flag `ImageVector`s (tricolours / discs / stars / saltire) for the 14 cuisines + neutral fallback. Mirrors the vendored-`FrIcons` pattern (no per-flag catalog entry needed). Flag colours are asset data (national colours), defined as private vals — not theme "meaning" colours.
- **Pokédex cell** = new `FrPokedexCell`: a **circular specimen disc** with a dex index `#NNN`; caught → celebration disc + monogram + name + caught-date; **locked → "?" silhouette + "???"** (the dex reveal mechanic). Grouped by **`IngredientCategory`** with section sub-headers → a genuinely different "browse the dex" experience vs the flat flag passport.
- **Achievement icons**: add `AchievementIcon.FirstPlate` (→ existing `FrIcons.Star`) and `ChefHat` (→ new vendored `ChefHatVector`); remap `first_plate`→FirstPlate, `best_cook`→ChefHat. Exhaustive `when` forces the mapper update at compile time.

## i18n added (StatsStringKey + en/es)
`CollectionTitle`, `BingoMysteryGlyph` ("?"), `BingoMysteryName` ("???"), `BingoIndexFormat` ("#%1$s" — `resolve()` can't do %0Nd, so index is padded in Kotlin and passed as %s), 12 × `BingoCategory*`.

## Files
- NEW `core/designsystem/.../atoms/FrFlags.kt`
- EDIT `core/designsystem/.../atoms/FrIcons.kt` (+ChefHat)
- EDIT `feature/achievements/.../domain/model/AchievementIcon.kt`, `domain/AchievementCatalog.kt`, `presentation/components/AchievementVisuals.kt`
- NEW `feature/stats/.../presentation/components/FrCuisineFlagCell.kt`, `FrPokedexCell.kt`
- DELETE `feature/stats/.../presentation/components/FrCuisinePassport.kt`, `FrIngredientBingo.kt` (FlowRow versions superseded)
- REWRITE `feature/stats/.../presentation/passport/PassportScreen.kt`
- EDIT `feature/stats/.../i18n/StatsStringKey.kt` + `composeResources/values{,-es}/strings.xml`

## Status
DONE — all four asks implemented; Android + iOS both compile, host suites green.

## Verify (actual)
- `:core:designsystem :feature:achievements :feature:stats testAndroidHostTest` + `:androidApp:assembleDebug` → **BUILD SUCCESSFUL in 12s**.
- Host-test counts: designsystem **73**, achievements **56**, stats **60** — **0 failures / 0 errors**.
- `:shared:linkDebugFrameworkIosSimulatorArm64` → **BUILD SUCCESSFUL in 38s** (stats + achievements + shared iOS compile).

## Not done on purpose / follow-ups
- Flags are simplified geometric gestalts (recognisable at ~56dp), not pixel-accurate vexillology. If a designer wants exact flags, swap the `FrFlags` vectors for vendored SVG paths (same `object`/resolver API).
- `IngredientCategory` grouping assumes the seeded catalog carries categories (it does). Empty/unseeded catalog → pokédex section simply doesn't render (bingo == null).
- On-device smoke (real flags + reveal scroll) not run here — needs the device walk-through.
- `AchievementIcon.Chef` (→ Crown) is now unused by the catalog (best_cook moved to ChefHat) but kept as a valid enum/mapping; harmless.

## Follow-up fix — insignias (achievements) celebration dialog showed the same button twice (2026-06-15)
- **Bug.** The badge-unlock celebration passed `confirmLabel = dismissLabel = DetailCloseCta` ("Close"/"Cerrar") to `FrConfirmDialog`, which always renders both buttons → two identical buttons, no real second action.
- **Fix.** `FrConfirmDialog.dismissLabel` is now `String? = null`; null/blank → the dismiss button isn't rendered (single-action acknowledge dialog; `onDismiss` still backs back-press/scrim). Celebration now uses one button via a new `AchievementStringKey.CelebrationAck` ("Nice!" / "¡Genial!"). All 8 existing two-button callers unchanged (named `dismissLabel = …`).
- **Tests/catalog.** Added `FrConfirmDialogTest.confirmDialog_acknowledgeMode_rendersSingleButton` (asserts exactly one button + only onConfirm fires); added a single-action scene to the catalog `ConfirmDialogStory`.
- **Verify.** `:core:designsystem:testAndroidHostTest :feature:achievements:testAndroidHostTest :catalogApp:assembleDebug :androidApp:assembleDebug` → **BUILD SUCCESSFUL in 19s**; designsystem host tests **74** (was 73), **0 failures / 0 errors**; both `FrConfirmDialog` testcases present + green.
