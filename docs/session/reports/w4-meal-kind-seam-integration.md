# Report — w4-meal-kind-seam-integration (TERMINAL Wave 4 task)

**Date:** 2026-06-15
**Status:** DONE — green.
**Goal:** Prove the `MealKind.Solo` seam is threaded end-to-end and behaviorally inert across the
whole meal flow, and lock it with cross-cutting tests. No `Together`, no kind picker, no UI.

## Prior-work check

No prior `w4-meal-kind-seam-integration` report existed. Domain (`w4-meal-kind-seam-domain`) and
data (`w4-meal-kind-seam-data`) were already landed and green per their handoffs. This task added
**only tests** (plus this report) — no production code changed, because §4 was already fully covered.

## §4 coverage checklist — every required touch-point

The spec `docs/specs/2026-06-14-meal-post-types-design.md` §4 (+ §6 data layer, §9 tests) names these
touch-points for the inert Solo seam. Each is implemented and locked:

| §  | Required touch-point | Implemented in | Locked by test |
|----|----------------------|----------------|----------------|
| §4.1 | `MealKind` sealed interface, `data object Solo` leaf, `commonMain` stdlib-only (Konsist-clean) | `core/domain/.../meal/MealKind.kt` | `MealKindTest.meal_kind_is_exhaustive_over_solo_today` (one-leaf `when`, no `else`) + `:core:domain` Konsist import rule |
| §4.2 | `Meal.kind: MealKind = MealKind.Solo` (defaulted last param; every construction site unchanged; rides `MealReadPort` for free) | `core/domain/.../meal/Meal.kt` L38 | `MealKindTest.meal_constructed_without_kind_defaults_to_solo` |
| §4.2 | Solo invariant: exactly one author = the existing `Meal.author` | `MealKind.authorIds(authorId)` in `MealKind.kt` | `MealKindTest.solo_meal_has_exactly_one_author_the_existing_author` |
| §4.3 | `MealDraft` NOT touched (implicitly Solo; composer can't produce another kind) | `feature/meal/.../domain/model/MealDraft.kt` — no `kind` field | (negative; nothing to assert — confirmed by inspection) |
| §6.1 | `MealDto.kind: String = "solo"` string discriminator; default reads pre-seam docs as Solo | `feature/meal/.../data/firebase/MealDto.kt` L45 | `MealMapperTest.toDomain_defaults_missing_kind_to_solo`; **new** `FirebaseMealRepositoryTest.legacy_doc_without_kind_field_reads_back_as_solo` |
| §6.2 | `MealMapper.toDomain()` tolerant read: `"solo" → Solo`, unknown/missing/`"together"` → Solo (forward-compat `else` arm) | `feature/meal/.../data/firebase/MealMapper.kt` L40–43 | `MealMapperTest.toDomain_reads_solo_kind_discriminator` / `toDomain_maps_unknown_kind_to_solo` / `toDomain_defaults_missing_kind_to_solo` |
| §6.2/§4 | Write-side discriminator mapper (exhaustive `when` over the one leaf → compile-guard for future `Together`) | `MealKind.toDiscriminator()` in `MealMapper.kt` L73–75; `MealDto.from(meal)` L100 | `MealMapperTest.from_writes_solo_discriminator` |
| §6.3 | `FirebaseMealRepository.publish()` stamps `kind = MealKind.Solo.toDiscriminator()` (= `"solo"`) on every written DTO, including per-crew fan-out copies | `FirebaseMealRepository.kt` L226 | **new** `FirebaseMealRepositoryTest.publish_stamps_solo_discriminator_on_written_dto` + `publish_fan_out_stamps_solo_on_every_per_crew_copy` |
| §6.4 | `MealDraftLocalStore` unchanged (`MealDraft` has no `kind`) | — | (negative; confirmed by inspection) |

**Conclusion: §4 was already fully covered before this task. No new surface was invented.** This task
added the cross-cutting integration proof only.

## Read paths that surface meals — confirmed Solo passes through untouched

`MealReadPort` returns `MealWithRatings` (whose `.meal` is the full `Meal`), so `kind` rides through
for free with NO port surface change (§4.2). Verified by inspection that **no read path branches on
`Meal.kind`** (grep over `feature/feed` + `feature/stats` + `core/domain/.../meal` found zero `MealKind`
/ `Meal.kind` reads other than the field declaration itself):

- `:feature:feed` — `FeedMealUi` / `FrFeedMealRow` consume `MealWithRatings.meal` fields (dish, author,
  photo, score) and never read `kind`. Feed read model is built by `FirebaseMealRepository.crewStream`
  → `MealDto.toMealWithRatings(lookup)` → `toDomain()` (carries `kind`) → `.copy(photoUrl = …)` (keeps `kind`).
- `:feature:stats` — `ObserveStatsUseCase` reads via `MealReadPort.observeRange`; the compute layer
  (`ComputeWindow`) aggregates over meals and never reads `kind` today (§5.5 stats attribution is
  deferred with `Together`).
- `:feature:meal` detail — same `MealReadPort`-backed read model; no `kind` branch.

The one place the read model could drop the field — `toMealWithRatings`'s live-identity
`baseMeal.copy(author = …)` branch — is now explicitly asserted to preserve `kind`.

## Cross-cutting tests added (this task)

All in `commonTest` (run on every test target via `testAndroidHostTest` / `iosSimulatorArm64Test`):

1. **Publish→read round trip (repository-level).**
   `FirebaseMealRepositoryTest`:
   - `publish_returns_meal_with_solo_kind` — the `Meal` returned by `publish` is `MealKind.Solo`
     (proves the publish→domain leg via `dto.toDomain()` over the DTO actually written).
   - `publish_stamps_solo_discriminator_on_written_dto` — the written DTO carries `kind == "solo"`.
   - `published_dto_reads_back_as_solo_kind` — the exact DTO the repo wrote, read back through the
     same `toDomain` feed/stats/detail use, deserializes to `MealKind.Solo` (closes the loop that
     `MealMapperTest` only checks at the isolated DTO↔domain boundary — the handoff's suggested test).
   - `publish_fan_out_stamps_solo_on_every_per_crew_copy` — multi-crew fan-out keeps every per-crew
     copy `"solo"`.
2. **Legacy/old doc WITHOUT the `kind` field deserializes to Solo (tolerant read).**
   `FirebaseMealRepositoryTest.legacy_doc_without_kind_field_reads_back_as_solo` — builds a pre-seam DTO
   omitting `kind` (deserializer fills the `"solo"` default) and asserts `toDomain().kind == Solo`.
   (Also locked at the mapper boundary by `MealMapperTest.toDomain_defaults_missing_kind_to_solo`.)
3. **Feed/stats/detail read model carries `kind` through without behavior change.**
   `MealWithRatingsMapperTest.read_model_carries_solo_kind_through_to_meal_with_ratings` — asserts the
   read model `MealWithRatings.meal.kind == Solo` through BOTH the no-lookup branch and the
   live-identity `baseMeal.copy(author = …)` branch. This is exactly the projection feed/stats/detail
   consume via `MealReadPort`.

All pre-existing feed/stats/meal/domain tests remain green (no behavior change).

## On the "guard that the system only writes Solo today"

§4 does **not** call for a runtime guard, and none was added — the seam is guarded by the **type
system**, which is stronger:

- `MealDraft` has no `kind` field (§4.3), so the composer/use-case layer literally cannot produce a
  non-Solo draft.
- `FirebaseMealRepository.publish()` hardcodes `MealKind.Solo.toDiscriminator()` — the only kind the
  write path can ever stamp today.
- `MealKind.toDiscriminator()` and `MealKind.authorIds(...)` are exhaustive `when`s over the single
  `Solo` leaf with no `else`. Adding the future `Together` leaf will **fail to compile** at both sites
  (and at `MealKindTest.meal_kind_is_exhaustive_over_solo_today`) until each is handled — a
  compile-time guard, surfacing every consumer that must change. A runtime "reject non-Solo" check
  would be dead code (unreachable) given the above, so it was deliberately omitted.

The read side is intentionally tolerant (`else -> Solo`, §6.2) rather than guarded: a future
`"together"` doc seen by a not-yet-updated client must degrade to Solo, not crash the read.

## The seam is behaviorally inert — and how `Together` slots in later

**Inert today:** every meal — published, fanned-out, read-back, legacy, or unknown-discriminator —
is `MealKind.Solo`. No rendered pixel, computed stat, scoring rule, delete right, or push fan-out
reads `kind`. The seam is one domain file + one defaulted `Meal` field + one DTO field + one read arm
+ one write stamp, all proven inert by the tests above.

**`Together` lands additively (spec §5; gated by §13 open decisions), without migrating `Solo`:**
1. Add `data class Together(val coAuthorIds: Set<AccountId>) : MealKind` — the exhaustive `when`s in
   `toDiscriminator()`, `authorIds()`, and `MealKindTest` immediately fail to compile, listing every
   site to update.
2. Replace `MealMapper.toDomain()`'s `else -> Solo` with an explicit `"together"` arm (+ a
   `coAuthorIds: List<String>` field on `MealDto`) and add an exhaustiveness test (§6.2/§12).
3. Add `MealDraft.kind` + participant selection + composer "Together" mode (§5.4); branch
   `publish()`'s stamp on `draft.kind`; relax the scoring guard for `Together` (§5.3) and mirror it in
   `firestore.rules`; extend stats attribution (§5.5), delete rights (§5.6), push fan-out (§5.7),
   security rules (§5.8), and analytics (§5.9).
Nothing in the Solo path is rewritten — exactly the "pure extension" the spec promised.

## Files changed

- `feature/meal/src/commonTest/kotlin/.../data/repository/FirebaseMealRepositoryTest.kt` — +2 imports
  (`MealKind`, `MealDto`, `toDomain`), +5 MealKind-seam tests (publish→read round trip, legacy-doc
  tolerant read, fan-out stamp).
- `feature/meal/src/commonTest/kotlin/.../data/firebase/MealWithRatingsMapperTest.kt` — +1 import
  (`MealKind`), +1 read-model carries-`kind`-through test.
- `docs/session/reports/w4-meal-kind-seam-integration.md` — this report.

No production code changed.

## Verify (all green)

```
$ ./gradlew :feature:meal:testAndroidHostTest :feature:feed:testAndroidHostTest \
    :feature:stats:testAndroidHostTest :core:domain:testAndroidHostTest
> Task :feature:stats:testAndroidHostTest
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 8s
167 actionable tasks: 16 executed, 151 up-to-date
```
Per-class (from JUnit XML): `MealKindTest` 3/3, `MealMapperTest` 8/8,
`FirebaseMealRepositoryTest` 33/33 (+5 new), `MealWithRatingsMapperTest` 6/6 (+1 new) — all
`failures="0" errors="0"`.

```
$ ./gradlew :androidApp:assembleDebug
> Task :androidApp:packageDebug
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 2s
329 actionable tasks: 28 executed, 301 up-to-date
```
The seam does not break the app graph.

## Decisions
- Added NO runtime "only-Solo" guard — the type system (no `MealDraft.kind`, hardcoded publish stamp,
  exhaustive non-`else` `when`s) is a stronger compile-time guard; a runtime check would be dead code.
- Added NO new production surface — §4 was already fully covered; this task is proof-only per the brief.
- Did NOT touch the read-side `else -> Solo` arm (deliberate forward-compat per §6.2; the future
  Together task owns replacing it).

## Blockers
None.

## Manual steps for human.md
None — proof-only test task, no deploy/IAM/store/index/on-device step.
