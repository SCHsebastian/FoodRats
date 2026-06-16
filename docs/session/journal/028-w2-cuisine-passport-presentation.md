# 028 · w2-cuisine-passport-presentation

**Status:** done

**Summary (≤6 lines):**
- Cuisine passport end-to-end: Firebase `CuisineReadPort` adapter in `:feature:ingredient` (`cuisineModule`), publish stamping (detected dish slug → `loadDishCuisine` → `Meal.cuisine` via `MealDto`, advisory/non-blocking), stats passport grid (`FrCuisinePassport`, collected vs locked + N/total), achievements `CuisineVariety` arm made live.
- Files: NEW `feature/ingredient/.../data/firebase/{CuisineDto,CuisineFirestoreDataSource,CuisineMapper,CuisineRepository}.kt` + verify/test; `shared/.../app/di/AppModule.kt`; `feature/meal/.../{UpdateMealDraftCommand,UpdateMealDraftUseCase,ComposePlateViewModel,MealDraftLocalStore,MealDto,MealMapper,FirebaseMealRepository,MealModule}.kt` + 4 tests; NEW `feature/stats/.../presentation/components/FrCuisinePassport.kt` + stats wiring + strings + tests; `feature/achievements/.../domain/AchievementEvaluator.kt`.
- Decisions: adapter in `:feature:ingredient` (mirrors catalog reader); passport folded into existing StatsViewModel (single MVI state); stamp advisory; reused `FrBadge` (no catalog entry); no analytics leaf (§2.2 specifies none).
- Blockers: none. MANUAL: deploy rules + run seed (passport empty until both).

**Verify (quoted):**
```
:feature:{ingredient,meal,stats,achievements}:testAndroidHostTest :core:domain:testAndroidHostTest → BUILD SUCCESSFUL in 8s (199 tasks)
:shared:testAndroidHostTest :androidApp:assembleDebug → BUILD SUCCESSFUL in 8s (357 tasks)
:shared:linkDebugFrameworkIosSimulatorArm64 → BUILD SUCCESSFUL in 47s
```

Report: `docs/session/reports/w2-cuisine-passport-presentation.md`
