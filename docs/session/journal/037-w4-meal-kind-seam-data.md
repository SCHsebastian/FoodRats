# 037 · w4-meal-kind-seam-data

**Status:** done

**Summary (≤6 lines):**
- `MealKind` seam persisted: `MealDto.kind: String = "solo"` discriminator, tolerant `MealMapper` (unknown/missing → Solo), publish() stamps `"solo"` inside the existing single `withContext(io)`.
- Files: `feature/meal/.../data/firebase/{MealDto,MealMapper}.kt`, `.../data/repository/FirebaseMealRepository.kt`, commonTest `MealMapperTest`.
- Decisions: no `coAuthorIds` field (§4.3 reserves it only as a Future comment, not build-now); read-side `else -> Solo` tolerant, write-side `toDiscriminator()` exhaustive `when` (compiler forces the future Together arm).
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:meal:testAndroidHostTest
BUILD SUCCESSFUL in 5s
(MealMapperTest 8/8, 0 failures)
```

**Integration handoff:** add a publish→read round-trip asserting `Meal.kind == Solo`; no read-path code changes needed (Solo rides through `MealReadPort` for free).

Report: `docs/session/reports/w4-meal-kind-seam-data.md` · Handoff: `docs/session/handoffs/w4-meal-kind-seam-data.md`
