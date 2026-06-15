# Report — w4-meal-kind-seam-data

**Status:** DONE. The `MealKind` seam is persisted in the meal data layer. Data-layer only; flow
integration + cross-cutting tests remain in `w4-meal-kind-seam-integration`.

## Prior work
None on disk for this task (no `docs/session/reports/w4-meal-kind-seam-data.md` /
`handoffs/w4-meal-kind-seam-data.md` existed). The DOMAIN seam was already landed and verified:
`core/domain/.../meal/MealKind.kt` (sealed interface, `data object Solo`) and
`Meal.kt` carries `val kind: MealKind = MealKind.Solo` (last param, defaulted). This task built only
the data layer (DTO + mapper + publish stamp + tests) per the domain handoff and spec §4.3/§6.

## What changed

### `MealDto` — discriminator field (spec §6.1)
`feature/meal/.../data/firebase/MealDto.kt`: added
```kotlin
val kind: String = "solo",
```
as the last field. Default `"solo"` so pre-seam docs (and any doc missing `kind`) deserialize as Solo
(kotlinx-serialization fills the default). Comment reserves the future `coAuthorIds` field additively;
**no Together fields added now** (§4.3 mentions `coAuthorIds` only as a *Future/DEFERRED* comment, so
it is reserved-in-comment, not declared — adding a real field now would be premature per the task
brief: "DO NOT add Together fields now unless §4.3 explicitly reserves them"; it reserves them only in
the deferred §5 design, not as a build-now field).

### `MealMapper` — read + write (spec §6.2/§6.3)
`feature/meal/.../data/firebase/MealMapper.kt`:
- `toDomain()` now maps the discriminator with the tolerant `when`:
  ```kotlin
  val mealKind = when (kind) {
      "solo" -> MealKind.Solo
      else -> MealKind.Solo   // missing/unknown/"together" → Solo until the Together build adds the arm
  }
  ```
  and constructs `Meal(..., kind = mealKind)`. The `else -> Solo` arm + its comment are intentional
  (§6.2/§12): the future Together task replaces `else` with an explicit `"together"` arm + an
  exhaustiveness test.
- Added a `MealKind.toDiscriminator()` extension (`Solo -> "solo"`) — an exhaustive `when` over the
  sealed type, so when the `Together` leaf lands the compiler forces this site to be updated.
- `MealDto.Companion.from(meal)` now writes `kind = meal.kind.toDiscriminator()`.

### `FirebaseMealRepository.publish()` — stamp Solo (spec §6.3)
`feature/meal/.../data/repository/FirebaseMealRepository.kt`: the publish path builds its DTO **inline**
(not via `MealDto.from`), so I stamped `kind = MealKind.Solo.toDiscriminator()` on the inline `MealDto(...)`
(~L221). The draft carries no `kind` (left untouched per the domain handoff / §4.3), so Solo is stamped
directly. **No new `withContext` added** — the stamp is a plain field inside the existing single
`withContext(dispatchers.io)` of `publish()`. Added imports: `core.domain.meal.MealKind` and the
`data.firebase.toDiscriminator` extension (following the existing top-level `toDomain` import style).

### Tests
`feature/meal/.../data/firebase/MealMapperTest.kt`: added 4 tests over a `soloDtoTemplate`:
- `toDomain_reads_solo_kind_discriminator` — `kind = "solo"` → `MealKind.Solo`.
- `toDomain_defaults_missing_kind_to_solo` — old doc with no field (DTO default `"solo"`) → `Solo`.
- `toDomain_maps_unknown_kind_to_solo` — `kind = "together"` (forward-compat) → `Solo`.
- `from_writes_solo_discriminator` — `MealDto.from(meal)` writes `kind = "solo"`.

(The publish-stamp itself is covered indirectly here via the `from()` discriminator test; an
end-to-end publish→read round-trip assertion belongs to the integration task, which exercises the
`FirebaseMealRepository.publish()` write path against the fake firestore.)

## Verify
```
./gradlew :feature:meal:testAndroidHostTest
```
Last lines:
```
> Task :feature:meal:testAndroidHostTest
BUILD SUCCESSFUL in 5s
90 actionable tasks: 16 executed, 74 up-to-date
```
`MealMapperTest` results XML: `tests="8" skipped="0" failures="0" errors="0"` (4 pre-existing + 4 new).
The only compiler output is pre-existing "No cast needed" warnings in unrelated test files (not from
this change).

## Decisions
- **No `coAuthorIds` field added.** §4.3 / §6.1 reserve it only as a `// Future:` comment for the
  DEFERRED Together build (§5). The task brief said add reserved Together fields only if §4.3
  *explicitly* reserves them as build-now; it does not. Kept the comment so the future addition is
  obviously additive (a new defaulted field — Solo ignores it).
- **`toDiscriminator()` is an exhaustive `when`** (write side), while `toDomain` keeps the tolerant
  `else -> Solo` (read side). Read must tolerate unknown future values from not-yet-updated clients;
  write must be compiler-forced to handle every leaf. This matches §6.2 (tolerant read) + §12 (the
  future task adds the `"together"` arm + exhaustiveness test).
- **Stamped on the inline DTO in `publish()`**, not via `MealDto.from` — publish builds its DTO inline
  from the draft + auth fields; `from()` is the inverse-of-`toDomain` factory for already-published
  meals. Both now carry the discriminator.

## Blockers
None.

## Suggested next
`w4-meal-kind-seam-integration` — see the handoff below.
