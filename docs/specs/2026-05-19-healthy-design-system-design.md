# FoodRats Design System — Healthy Refresh (v2)

**Status:** Approved 2026-05-19
**Owner:** Sebastián Cardona Henao
**Scope:** `core/designsystem` only (atoms keep their public API; only visuals change). No feature code, no domain code.

---

## 1. Why

The current palette (`#E6552F` tomato red over `#2F8F4A` leafy green) reads "fast-food brand," not "healthy meal-sharing with friends." Material's defaults are wired through `FoodRatsTheme` essentially untouched: `Typography()` is the unmodified default, and `darkColorScheme` reuses the light primary so there is no real dark mode. Atoms compose against `MaterialTheme.colorScheme`, so a clean swap at the theme layer propagates everywhere without touching feature modules.

This refresh aligns the look with the product's intent (healthy eating, vibrant social loop), introduces semantic color tokens the codebase currently lacks, and ships a proper dark theme.

This is a **visual refresh, not an API change.** No `Fr*` composable signature changes. No domain or feature code touched.

## 2. Direction

- **Mood:** Vibrant & Energetic — confident greens, citrus accents, berry celebration moments.
- **Type:** Friendly geometric sans (Plus Jakarta Sans target; system fallback until fonts are bundled).
- **Shape:** Softer rounding to match photo-heavy meal-card UI.
- **Themes:** Light + Dark, both first-class.

## 3. Color system

### 3.1 Brand palette (Option A: Avocado + Citrus)

| Role | Light | Dark | Usage |
|---|---|---|---|
| Primary | `#3FB950` avocado leaf | `#6EDC78` | Brand identity, primary CTAs (Publish, Sign in), bottom-nav selection |
| Secondary | `#FFB020` honey citrus | `#FFCB78` | Streak counters, score highlights, badges, FAB accents |
| Tertiary | `#E84B6A` berry | `#FFB1BD` | Celebration moments ("you posted!" toasts, leaderboard #1) |

Tertiary is intentionally rare; it should not dilute by appearing on every screen.

### 3.2 Full Material 3 ColorScheme — Light

```
primary             = #3FB950
onPrimary           = #FFFFFF
primaryContainer    = #C5EFC9
onPrimaryContainer  = #06310C

secondary           = #FFB020
onSecondary         = #2C1A00
secondaryContainer  = #FFE9BD
onSecondaryContainer= #2C1A00

tertiary            = #E84B6A
onTertiary          = #FFFFFF
tertiaryContainer   = #FFD9DF
onTertiaryContainer = #410012

error               = #BA1A1A
onError             = #FFFFFF
errorContainer      = #FFDAD6
onErrorContainer    = #410002

background          = #FBFAF6   // warm cream
onBackground        = #1A1C18
surface             = #FBFAF6
onSurface           = #1A1C18
surfaceVariant      = #F2EFE6   // raised cards
onSurfaceVariant    = #44483D
surfaceTint         = #3FB950   // = primary, drives M3 elevation tint
outline             = #74796C
outlineVariant      = #C4C8B7
inverseSurface      = #2F312C
inverseOnSurface    = #F1F1E9
inversePrimary      = #6EDC78
scrim               = #000000
```

### 3.3 Full Material 3 ColorScheme — Dark

```
primary             = #6EDC78
onPrimary           = #003910
primaryContainer    = #1F5527
onPrimaryContainer  = #C5EFC9

secondary           = #FFCB78
onSecondary         = #432B00
secondaryContainer  = #5E3F00
onSecondaryContainer= #FFE0B2

tertiary            = #FFB1BD
onTertiary          = #5F1124
tertiaryContainer   = #82253A
onTertiaryContainer = #FFD9DF

error               = #FFB4AB
onError             = #690005
errorContainer      = #93000A
onErrorContainer    = #FFDAD6

background          = #0F1411
onBackground        = #E3E3DC
surface             = #0F1411
onSurface           = #E3E3DC
surfaceVariant      = #1B2520
onSurfaceVariant    = #C4C8BB
surfaceTint         = #6EDC78
outline             = #8E9387
outlineVariant      = #44483D
inverseSurface      = #E3E3DC
inverseOnSurface    = #2F312C
inversePrimary      = #3FB950
scrim               = #000000
```

### 3.4 Semantic tokens (new — exposed via CompositionLocal)

The codebase currently has zero semantic tokens. Add an `FrSemanticColors` data class provided by `LocalFrSemanticColors`:

| Token | Light | Dark | Used for |
|---|---|---|---|
| `success` | `#3FB950` (= primary) | `#6EDC78` | "Saved!" banners, validation OK |
| `warning` | `#F59E0B` | `#FCD34D` | Rate-limit, low-data warnings |
| `danger` | `#BA1A1A` (= error) | `#FFB4AB` | Destructive actions |
| `info` | `#3B82F6` | `#93C5FD` | Tooltips, FYI banners |
| `celebration` | `#E84B6A` (= tertiary) | `#FFB1BD` | Streak unlock, leaderboard #1 |
| `streakHot` | `#F97316` tangerine | `#FB923C` | Streak ≥ 7 days, the "on fire" state |

Access pattern in atoms:
```kotlin
val semantic = LocalFrSemanticColors.current
FrText(text = "+1", color = semantic.success)
```

### 3.5 Accessibility floor

All `on<Role>` / `<role>Container` pairs target **WCAG AA (≥4.5:1)** for body text. The honey-citrus secondary on white drops below AA, so secondary text **always** uses `onSecondary` (dark brown) on a `secondaryContainer` (pale honey) fill — never raw secondary on background.

## 4. Typography

Replace `Typography()` (Material default) with a custom ramp tuned for legibility at meal-card density and tabular numerals for streak/score counts.

### 4.1 Font family

Target **Plus Jakarta Sans** (Open Font License, both platforms).

This spec defines the type ramp using a `FrFontFamily` value that initially resolves to `FontFamily.SansSerif` (system default — works today). When font assets are bundled in `composeResources/font/`, `FrFontFamily` flips to load them. No call-site changes required.

Bundling fonts is a follow-up step explicitly outside this spec's scope (the binary download is a separate concern). The visual lift from the rest of the system is meaningful even before the family swap.

### 4.2 Type ramp

| Style | Size | LineHeight | Weight | Letter | Use |
|---|---|---|---|---|---|
| displayLarge | 57 | 64 | 700 | -0.25 | (unused today, reserved) |
| displayMedium | 45 | 52 | 700 | 0 | Empty-state hero |
| displaySmall | 36 | 44 | 700 | 0 | Onboarding headline |
| headlineLarge | 32 | 40 | 700 | 0 | Stats screen "30-day streak" |
| headlineMedium | 28 | 36 | 700 | 0 | Section headers |
| headlineSmall | 24 | 32 | 600 | 0 | Card titles |
| titleLarge | 22 | 28 | 600 | 0 | App-bar title |
| titleMedium | 16 | 24 | 600 | 0.15 | List-row title |
| titleSmall | 14 | 20 | 600 | 0.1 | Chip label |
| bodyLarge | 16 | 24 | 400 | 0.5 | Main body |
| bodyMedium | 14 | 20 | 400 | 0.25 | Secondary body |
| bodySmall | 12 | 16 | 400 | 0.4 | Captions, timestamps |
| labelLarge | 14 | 20 | 600 | 0.1 | Button labels |
| labelMedium | 12 | 16 | 600 | 0.5 | Tab labels |
| labelSmall | 11 | 16 | 600 | 0.5 | Overline |

All "title" / "label" weights bumped to 600 (default is 500) for a more confident, friendly feel; body stays 400.

### 4.3 Numeric display

Stats, scores, streaks, leaderboard positions render with tabular numerals so they don't shift mid-update. Provide a `FrTextStyles.statNumber` style with `FontFeatureSettings = "tnum"` for use by `FrScoreBadge`, leaderboard rows, and the streak counter.

## 5. Shapes

Slightly softer than the current scale to match the photo-heavy meal-card aesthetic.

| Token | Before | After |
|---|---|---|
| `Radius.xs` | — | `8.dp` (new) |
| `Radius.sm` | `4.dp` | `12.dp` |
| `Radius.md` | `8.dp` | `16.dp` |
| `Radius.lg` | `16.dp` | `24.dp` |
| `Radius.xl` | — | `32.dp` (new) |
| `Radius.pill` | `999.dp` | `999.dp` (unchanged) |

Material `Shapes` mapping:
```
extraSmall = sm   (12)
small      = sm   (12)
medium     = md   (16)
large      = lg   (24)
extraLarge = xl   (32)
```

## 6. Tokens

### 6.1 Spacing — unchanged

The existing `xxs/xs/sm/md/lg/xl/xxl` ramp is fine. No changes.

### 6.2 Elevation — extended

Add an M3-style elevation ramp aligned to `surfaceTint` overlay levels:

| Token | dp | M3 use |
|---|---|---|
| `Elevation.level0` | `0.dp` | Background |
| `Elevation.level1` | `1.dp` | Cards (was `Elevation.sm`) |
| `Elevation.level2` | `3.dp` | Top bar |
| `Elevation.level3` | `6.dp` | FAB (was `Elevation.md`) |
| `Elevation.level4` | `8.dp` | Modal sheet |
| `Elevation.level5` | `12.dp` | Bottom-sheet expanded (was `Elevation.lg`) |

Legacy aliases are kept so existing call-sites compile without edits, mapped as:
- `none = level0` (`0.dp`)
- `sm   = level1` (`1.dp`)
- `md   = level3` (`6.dp`, up from `4.dp` — closer to M3 FAB elevation)
- `lg   = level5` (`12.dp`, unchanged)

### 6.3 Sizes — additions

Add to the existing `Sizes` object:

```kotlin
val streakBadge       = 56.dp
val mealCardImage     = 220.dp
val bottomBarHeight   = 80.dp
val touchTarget       = 48.dp
```

## 7. Theme wrapper

`FoodRatsTheme` gains:
- Proper `dynamicColor: Boolean = false` parameter (default off — brand identity over Material You).
- `CompositionLocalProvider(LocalFrSemanticColors provides semanticColors)` wrapping the `MaterialTheme` call so semantic tokens are addressable anywhere underneath.

Signature change is additive only; existing callers (`shared/.../MainViewController.kt`, `androidApp/.../MainActivity.kt`) compile unchanged.

## 8. Atom impact

No `Fr*` composable changes its public API. Visual differences flow purely from the new `MaterialTheme.colorScheme` / `typography` / `shapes`. Spot-check list before declaring done:

- `FrButton` — primary, secondary, tonal, outlined, text variants all derive from new scheme.
- `FrChip` — `Radius.pill` pill, `secondaryContainer` fill.
- `FrScoreBadge` — uses `semantic.celebration` when score ≥ 4, `semantic.streakHot` reserved for streak ≥ 7.
- `FrShutterButton` — `primary` fill, `Sizes.shutter` unchanged.
- `FrErrorBanner` — `errorContainer` background, `onErrorContainer` content.
- `FrFeedLayout`, `FrCaptureLayout`, `FrScreenScaffold` — pick up new `background`/`surface` automatically.

## 9. Out of scope

- Bundling Plus Jakarta Sans font files (separate task — binary asset workflow).
- The three architecture audit findings (enum→sealed errors in `:core:domain`, `FeedViewModel` `day` MVI break, hardcoded `★`/`•` in feed/meal/stats cards) — captured in a parallel spec.
- `FrIcons` core-icon placeholders (CLAUDE.md tech-debt entry, unaffected by this refresh).
- Motion/animation tokens (deferred).

## 10. Acceptance

- `./gradlew :core:designsystem:assembleDebug` green.
- `./gradlew :core:designsystem:testAndroidHostTest` green (existing atom tests must still pass; they assert structure, not color values).
- `:androidApp:assembleDebug` green.
- Visual smoke: launch on Android, verify SignIn / Feed / Capture / Stats render coherent palette in both light and dark system themes.
- iOS framework links: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`.
- No imports of domain types in `core/designsystem` (Konsist rule is on `:core:domain`, but spot-grep `import es.schsebastian.foodrats.core.domain` under designsystem; expect zero).
