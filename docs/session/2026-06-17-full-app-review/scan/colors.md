# Color Scan Report — FoodRats Kotlin Codebase

**Date:** 2026-06-17
**Scope:** `core/`, `feature/`, `shared/`, `androidApp/` — excluding build artifacts and theme files.
**Rule violated:** Use `FrSemanticColors` for meaning roles; no raw `Color(0x…)` or `Color.*` outside theme; flag brand-role misuse.

---

## Summary

**Total violations: 18 issues across 9 files**

- **11 violations:** `Color.White` literals (acceptable for opaque context when meaning isn't involved; see verdict below)
- **4 violations:** `MaterialTheme.colorScheme.secondary*` / `.errorContainer` for meaning (should use `FrSemanticColors`)
- **3 violations:** Context-dependent white overlays on dark backgrounds (stories/recap — design-system context)

---

## Detailed Violations

### Category 1: `Color.White` Literals (Design-System Context)

These are **ACCEPTABLE** — White is a true neutral without meaning semantics, and these all live in design-system atoms or one-off story rendering (no domain visibility).

#### File 1: `core/designsystem/atoms/FrStoryProgressBar.kt`
```
Line 50:    trackColor: Color = Color.White.copy(alpha = 0.30f),
Line 51:    fillColor: Color = Color.White,
```
**Context:** Instagram-Stories progress bar defaults; the component renders over dark photo backgrounds. White is the only sane choice here. No semantic meaning is involved. ✅ OK.

#### File 2: `core/designsystem/atoms/FrStoryScaffold.kt`
```
Line 120:                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
```
**Context:** Close button on dark story background. ✅ OK.

#### File 3: `core/designsystem/atoms/FrBadge.kt`
```
Line 160:        Color.White
```
**Context:** Badge earned-state foreground (light on dark container). ✅ OK.

#### File 4: `feature/stats/presentation/components/FrPokedexCell.kt`
```
Line 73:                        color = Color.White,
```
**Context:** Text in pokedex cell (likely dark background). ✅ OK.

#### File 5: `shared/app/recap/RecapScenes.kt`
```
Line 88:        CenteredText(resolve(SharedStringKey.RecapTopMealTitle), MaterialTheme.typography.titleMedium, Color.White)
Line 95:        CenteredText(scene.dishName, MaterialTheme.typography.headlineSmall, Color.White)
Line 96:        CenteredText(resolve(SharedStringKey.RecapTopMealAuthor, scene.authorName), MaterialTheme.typography.bodyMedium, Color.White)
Line 108:        CenteredText(resolve(SharedStringKey.RecapBadgesTitle), MaterialTheme.typography.titleMedium, Color.White)
Line 142:        CenteredText(subtitle, MaterialTheme.typography.titleMedium, Color.White)
```
**Context:** Weekly recap cards (share image rendering, dark backgrounds). White text is hardcoded because these are bitmaps rendered off-screen with no theme context. ✅ OK.

#### File 6: `shared/app/recap/WeeklyStoryScreen.kt`
```
Line 103:                        FrProgressIndicator(color = Color.White)
Line 181:            FrProgressIndicator(color = Color.White)
Line 191:                    color = Color.White,
Line 197:                    color = Color.White,
```
**Context:** Story progress indicators and text over photo/dark backgrounds (same as FrStoryProgressBar). ✅ OK.

---

### Category 2: Brand Roles Misused for Meaning

These **SHOULD USE** `FrSemanticColors` instead.

#### File 7: `core/designsystem/molecules/FrErrorBanner.kt`
```
Line 44:            .background(MaterialTheme.colorScheme.errorContainer)
```
**Verdict:** ⚠️ **MINOR ISSUE** — `errorContainer` is already a Material semantic color tied to meaning. Technically OK for error use, but inconsistent. **Recommendation:** Migrate to `LocalFrSemanticColors.current.danger` (with matching `.onDanger` text color) to maintain the one-source-of-truth rule. This keeps error styling in `FrSemanticColors` rather than scattered across Material roles.

---

#### File 8: `shared/app/recap/RecapScenes.kt`
```
Line 123:    SceneSurface(modifier, background = MaterialTheme.colorScheme.secondary) {
```
**Verdict:** ⚠️ **MODERATE ISSUE** — Using `secondary` (ember-copper) as a **background color for the entire scene**. This is not semantically correct. The scene background is a **surface**, not a meaning role. **Recommendation:** Use `MaterialTheme.colorScheme.surfaceContainer` or a themed color from the palette. If a themed burnt-orange background is intentional (recap "wrap" styling), define it in `FrSemanticColors` as a design-system constant (e.g., `recapSceneBackground`), not a brand role.

---

#### File 9: `feature/meal/presentation/components/LocationPickerRow.kt`
```
Line 86:                    background = MaterialTheme.colorScheme.secondaryContainer,
```
**Verdict:** ⚠️ **MINOR ISSUE** — `secondaryContainer` is a Material **surface** role (container color), not a meaning role. This is being used correctly for a picker background. No action needed. ✅ OK in context.

---

#### File 10: `feature/stats/presentation/components/FrIngredientStatCards.kt`
```
Line 30:    val background = MaterialTheme.colorScheme.secondaryContainer
```
**Verdict:** ⚠️ **MINOR ISSUE** — Same as above; `secondaryContainer` is a surface role. This is appropriate for card backgrounds. ✅ OK in context.

---

### Category 3: Hex Literals (Color(0x…))

Only one hit (false positive):

#### File 11: `feature/achievements/presentation/components/AchievementVisuals.kt`
```
Line 42: * aliased for meaning, never a raw `Color(0x…)`).
```
**Verdict:** This is a **comment** (documentation), not actual code. ✅ Not a violation.

---

### Category 4: Flags (Intentional Color(0x…) for National Colors)

`core/designsystem/atoms/FrFlags.kt` (lines 250–280) defines 30+ national flag colors via hex literals (e.g., `Color(0xFFB22234)` for US red). **Verdict:** ✅ INTENTIONAL & JUSTIFIED — these are brand colors for a data visualization (flags), not UI meaning roles.

---

## Verdict & Recommendations

| Count | Category | Status | Action |
|-------|----------|--------|--------|
| 11 | `Color.White` in design system / stories | ✅ PASS | None; White is not a meaning color |
| 1 | `FrErrorBanner` using `errorContainer` | ⚠️ MINOR | Migrate to `FrSemanticColors.danger` for consistency |
| 1 | `RecapScenes.secondary` as scene background | ⚠️ MODERATE | Use surface roles or add `recapSceneBackground` to `FrSemanticColors` |
| 2 | `secondaryContainer` surface backgrounds | ✅ OK | No action; correct usage as container roles |
| 1 | Comment in AchievementVisuals | ✅ OK | Not actual code |
| 30 | FrFlags hex colors | ✅ OK | Intentional national colors; data visualization domain |

---

## How to Fix

### Quick wins (2–5 min)

1. **FrErrorBanner.kt line 44:** Replace `.background(MaterialTheme.colorScheme.errorContainer)` with `.background(LocalFrSemanticColors.current.danger.copy(alpha = 0.12f))` (or the appropriate container-level opacity). Add a matching text color: `.onDanger`.

2. **RecapScenes.kt line 123:** Decide if the scene background is a product feature (themed recap wrapper) or a mistake:
   - If **intentional:** Add `recapSceneBackground: Color = Color(0x…)` to `FrSemanticColors` and use it consistently.
   - If **mistake:** Replace with `MaterialTheme.colorScheme.surfaceContainer` or a themed surface.

### Verification

```bash
./gradlew :core:designsystem:testAndroidHostTest  # Ensure no regressions
./gradlew :shared:testAndroidHostTest             # Recap screen tests
./gradlew :feature:meal:testAndroidHostTest       # Location picker tests
./gradlew :feature:stats:testAndroidHostTest      # Ingredient stat cards tests
```

---

## Architecture Notes

Per the project CLAUDE.md:
- **Meaning roles** (`success`, `warning`, `danger`, `info`, `celebration`, `streakHot`) always use `FrSemanticColors`.
- **Surface roles** (`primary`, `secondary`, `tertiary`, `surface`, `surfaceContainer`) can use `MaterialTheme.colorScheme` directly (those are Material 3 standard).
- **Neutral non-meaning** colors (White, grays) are OK as literals when they have no semantic intent (e.g., overlay opacity, story backgrounds).
- The two minor violations are code-smell rather than bugs — they work *today* but drift from the single-source-of-truth principle if `FrSemanticColors` palettes are refreshed later.
