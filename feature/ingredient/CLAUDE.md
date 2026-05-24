# :feature:ingredient

Ingredient catalog and selection picker. The catalog is a read-only set of `Ingredient` domain objects keyed by `IngredientSlug`, seeded into Firestore by an admin script and consumed via a snapshot listener. The picker UI lets the user search and select ingredients to attach to a `MealDraft` before publishing.

## Authoritative references

- Spec — `docs/specs/2026-05-24-meal-ai-ingredient-classification-design.md` §5 (catalog domain model, `IngredientReadPort`), §7.2 (picker screen, `SelectIngredientsViewModel`).
- Root `CLAUDE.md` — "Module graph", "Architectural rules" (ports for cross-context reads, sealed-interface errors, i18n for all user-visible text, one `withContext` per repository method).

## Local rules

- No client writes to the catalog. Firestore security rules allow reads only; seed data is managed via the `scripts/seed-ingredients.ts` admin script.
- Catalog is cached in DataStore as a serialised snapshot; the Firestore listener updates the cache on change. Avoid re-fetching on every cold start.
- JVM target **17** (Firebase BOM applied in `androidMain.dependencies`; required by BOM 33.x inline functions).

## Test

`./gradlew :feature:ingredient:testAndroidHostTest`
