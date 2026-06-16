# 029 · w2-bingo-presentation

**Status:** done

**Summary (≤6 lines):**
- Ingredient bingo (Pokédex) end-to-end: pure `deriveIngredientBingo` in `:core:domain` (mirrors `CuisinePassport`), stats section `FrIngredientBingo` (collected vs locked + N/total), `IngredientVariety` badge arm + `ingredients_25/50/100` rows live.
- Files: `core/domain/.../meal/IngredientBingo.kt` (new) +test; `feature/stats` `StatsSnapshot.kt`, `ObserveStatsUseCase.kt`, `StatsStringKey.kt`, `presentation/components/FrIngredientBingo.kt` (new), `StatsScreen.kt`, `values{,-es}/strings.xml`, `StatsViewModelTest`, `ObserveStatsUseCaseTest`.
- Decisions: counts CONFIRMED `Meal.ingredients` only (AI `detectedIngredients` excluded, test-locked, sidesteps the known AI-persist bug); set-based O(meals)+O(catalog); reuses `FrBadge` (no new atom); personal-only (crew tab + category sections deferred); no analytics leaf (§2.3 lists none).
- Blockers: none. MANUAL: deploy rules + run seed (else all cells locked).

**Verify (quoted):**
```
:core:domain:testAndroidHostTest :feature:stats:testAndroidHostTest → BUILD SUCCESSFUL in 21s
:feature:achievements:testAndroidHostTest :androidApp:assembleDebug → BUILD SUCCESSFUL in 5s
```

Report: `docs/session/reports/w2-bingo-presentation.md`
