# 044 · w5-fix-stats-meal-fixtures

**Status:** done

**Summary (≤6 lines):**
- Fixed RED `:feature:stats` test compile. Root cause: `image-pipeline-presentation` inserted defaulted `thumbnailUrl`/`thumbHash` MID-constructor in `Meal.kt` (before `dish`), breaking POSITIONAL fixture calls in stats tests.
- Fix: converted 3 stats fixtures to NAMED args (test-only; no production/`Meal.kt` change — avoids touching other callers).
- Files: `feature/stats/commonTest/.../domain/compute/PersonalStreakTest.kt`, `feature/stats/commonTest/.../presentation/stats/StatsViewModelTest.kt`.
- Blockers: none.
- NOTE for review pack: other positional `Meal(...)` fixtures may exist elsewhere; mid-constructor field insertion is the latent hazard — worth a sweep.

**Verify (quoted):**
```
:feature:stats:testAndroidHostTest → BUILD SUCCESSFUL in 2s
:core:domain + :feature:feed + :feature:meal + :feature:stats testAndroidHostTest → BUILD SUCCESSFUL in 2s (167 tasks, all green)
```

Report: `docs/session/reports/w5-fix-stats-meal-fixtures.md`
