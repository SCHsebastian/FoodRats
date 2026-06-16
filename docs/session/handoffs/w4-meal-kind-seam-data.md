# Handoff — w4-meal-kind-seam-data → w4-meal-kind-seam-integration

The data layer now persists the `MealKind` seam. Domain + data are both done; the seam is fully inert
(every meal is `Solo`). Integration is about threading the round trip end-to-end and locking it with
cross-cutting tests — there are NO behaviour changes to make.

## What is now persisted
- `MealDto.kind: String = "solo"` (`feature/meal/.../data/firebase/MealDto.kt`). Default reads any
  pre-seam / missing-field doc as Solo (kotlinx-serialization fills the default).
- `FirebaseMealRepository.publish()` stamps `kind = MealKind.Solo.toDiscriminator()` (= `"solo"`) on
  every published meal DTO (inline DTO build, ~L221, inside the existing single `withContext(io)`).
- `MealDto.Companion.from(meal)` also writes `kind = meal.kind.toDiscriminator()`.

## What the read model now carries
- `MealMapper.toDomain()` maps `kind` → `MealKind` (tolerant `when`: `"solo"` → `Solo`;
  missing/unknown/`"together"` → `Solo`) and constructs `Meal(..., kind = mealKind)`.
- `Meal.kind` therefore flows through `MealReadPort` (returns `Meal`) for free — **no port surface
  change**. Feed / stats / detail already receive `Meal.kind` = `Solo` on every meal.

## What remains for integration
1. **End-to-end publish→read round trip.** Add a test (likely in
   `feature/meal/.../data/repository/FirebaseMealRepositoryTest.kt`, against the existing fake
   firestore) that publishes a draft and asserts the written/read-back `Meal.kind == MealKind.Solo`
   (and/or the stored DTO's `kind == "solo"`). This closes the loop that `MealMapperTest` only checks
   at the DTO↔domain boundary.
2. **Nothing branches on `Meal.kind` yet** — with the `Solo` default, feed/stats/detail need NO code
   change. Just confirm (a quick read) that no read path drops the field and that `assembleDebug`
   (and ideally the iOS framework link) stays green with the seam threaded.
3. **Cross-cutting / exhaustiveness:** the domain `MealKindTest` already locks the one-leaf seam
   (`when (kind)` exhaustive with the single `Solo` arm). The data-layer `toDiscriminator()` is also an
   exhaustive `when`, so adding the future `Together` leaf will fail-to-compile both sites — no action
   needed now, but note it for the future Together plan (replace the mapper's read-side `else -> Solo`
   with an explicit `"together"` arm + an exhaustiveness test, per spec §6.2/§12).

## Watch-outs
- Do NOT add a `kind` field to `MealDraft` (spec §4.3 — the composer can't produce another kind yet).
- Do NOT replace the read-side `else -> Solo` in `MealMapper.toDomain()` — it is the deliberate
  forward-compat arm (§6.2). Touching it is the future Together task's job.
- No new error leaf, no i18n, no UI change (spec §3 out-of-scope, §7/§8 deferred).

## Verify (data task, already green)
```
./gradlew :feature:meal:testAndroidHostTest   → BUILD SUCCESSFUL; MealMapperTest 8/8, 0 failures
```
