# :feature:meal

The rich-domain exemplar: `ImagePickerKMP` (launcher-style native camera + gallery) → compose draft → publish a `Meal`. Owns the `MealReadPort` / `MealRatingPort` implementations consumed by `:feature:feed` and `:feature:stats`.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §10 (Exemplar B — Meal), §3.1 (cross-context read pattern: this module owns the read ports).
- Root `CLAUDE.md` — "Module graph" (rich-domain exemplar tag), "Architectural rules" (sealed-interface errors with `data object` leaves — `MealError.Publish.AlreadyPostedToday` is the canonical example), "Active tech debt" (meal-feed enrichment still reads the legacy `crews/{id}.members` cache).
- Recent change — "Description replaces tags on `Meal`" (2026-05-21) — `Meal.tags`/`MealDraft.tags` gone, replaced by `Description` (≤ 280 chars). Spec `docs/specs/2026-05-21-meal-description-replaces-tags-design.md` supersedes lines 368 and 903 of the base design spec.

## Local rules

- New Meal-touching tests use `description = Description.EMPTY` (positional or named). Don't reintroduce `FoodTag` — it's deleted, along with the stats `TagVariety` leaderboard.
- **AI classification is advisory and goes through `:core:domain` ports, never `:feature:meal-ai`.** `ComposePlateViewModel` classifies a captured plate via the meal-owned `ClassifyDraftPlateUseCase` (over `MealClassifierPort` + `IngredientReadPort`), stamps `UpdateMealDraftCommand.SetDetected`, and exposes `classifying`/`detectedIngredients`/`draftIngredients`/`classifierError` in state. A classifier failure surfaces a banner but must NOT touch `canContinue`. `onPhotoCaptured(bytes)` dedupes by `contentHashCode()` (re-entry no-op, re-capture overwrites). The picker is reached via `Route.SelectIngredients` (in `:feature:ingredient`); the composer edits the draft's ingredient set only through `MealDraftIngredientsPort`.
- iOS: ImagePickerKMP `1.0.41` declares `material-icons-extended` in `commonMain`. If iOS linking breaks, add the `exclude(group = "org.jetbrains.compose.material", module = "material-icons-extended")` block in `build.gradle.kts`. ImagePickerKMP also references `CLLocation` unconditionally — link `CoreLocation.framework` in Xcode if the linker complains.
- JVM target **17** (Firebase BOM).

## Test

`./gradlew :feature:meal:testAndroidHostTest`. Single test: `--tests "*PublishMealUseCaseTest.publishes_when_draft_day_is_today"`.
