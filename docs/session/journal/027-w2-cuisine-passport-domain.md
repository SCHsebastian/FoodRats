# 027 · w2-cuisine-passport-domain

**Status:** done

**Summary (≤6 lines):**
- Cuisine passport domain: `Cuisine`/`CuisineSlug` VOs, `CuisineReadPort` (language-aware, mirrors `IngredientReadPort`), pure `CuisinePassport` derivation (collected vs total + branches). `Meal.cuisine` + `MealDraft.{detectedDishSlug,cuisine}` added (nullable, no migration).
- Files: `core/domain/.../cuisine/{Cuisine,CuisineReadPort,CuisinePassport}.kt` + tests; `core/domain/.../meal/Meal.kt` (+`cuisine`); `feature/meal/.../domain/model/MealDraft.kt` (+`detectedDishSlug`,`cuisine`).
- Decisions: STAMP-AT-PUBLISH per §2.2 — dish slug wasn't persisted (classifier yields then discards), so added `detectedDishSlug` to draft; derivation reads `Meal.cuisine` (always-confirmed, no AI re-derive). No separate data task needed.
- Blockers: none. MANUAL: deploy rules + run seed.

**Verify (quoted):**
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s
(CuisinePassport 7/7, Resolution 3/3, Slug 5/5, Konsist 1/1; :feature:meal also green)
```

**Presentation handoff:** thread `detectedDishSlug`→`loadDishCuisine`→`Meal.cuisine`+`MealDto`; build Firebase cuisine-catalog adapter (mirror ingredient catalog); build the stats grid.

Report: `docs/session/reports/w2-cuisine-passport-domain.md` · Handoff: `docs/session/handoffs/w2-cuisine-passport-domain.md`
