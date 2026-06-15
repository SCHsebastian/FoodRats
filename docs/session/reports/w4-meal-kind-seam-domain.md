# w4-meal-kind-seam-domain — report

**Status:** DONE. Behaviorally-inert `MealKind` seam introduced in `:core:domain`, carrying only
`MealKind.Solo` today. `Together` (spec §5) is DEFERRED and was not built. Domain layer only — DTO/
mapper is `w4-meal-kind-seam-data`; meal-flow integration is `w4-meal-kind-seam-integration`.

## Prior work check

No prior/interrupted work for this task on disk (no `MealKind.kt`, no report/handoff/journal entry).
Started clean.

## Spec basis

`docs/specs/2026-06-14-meal-post-types-design.md`:
- §4.1 — `MealKind` is a `sealed interface` in `:core:domain/meal/MealKind.kt`, `data object Solo`
  leaf, `commonMain` stdlib-only → Konsist-clean. Future `Together` is a `data class
  Together(val coAuthorIds: Set<AccountId>)` (full participant set INCLUDING creator).
- §4.2 — `Meal` gains `val kind: MealKind = MealKind.Solo`. Default keeps every construction site
  compiling and means feed/stats read `Solo` for every meal. `MealReadPort` returns `Meal` → no
  port surface change. `MealAuthor` stays the single authoritative author for `Solo`.
- §4.3 — `MealDraft` is **untouched now** (implicitly `Solo`); `kind` is NOT added to the draft yet.
  `PublishMealUseCase` will stamp `MealKind.Solo` (that wiring belongs to the data/integration
  tasks, not here).
- §5 (skimmed) — shaped the sealed hierarchy so `Together` slots in as a NEW `data class` leaf
  additively; not implemented (gated by §13 open product decisions).

## Changes

1. **NEW** `core/domain/src/commonMain/kotlin/.../meal/MealKind.kt`
   - `sealed interface MealKind { data object Solo : MealKind }`.
   - The future `Together` leaf is documented in KDoc + a commented stub (`data class
     Together(val coAuthorIds: Set<AccountId>)`) so the additive path is "follow the spec."
   - Pure helper `fun MealKind.authorIds(authorId: AccountId): Set<AccountId>` encoding the §4.2
     Solo invariant: a Solo meal has exactly one author = the existing `author` → `setOf(authorId)`.
     `when (this)` is exhaustive over `{Solo}` today; the future `Together` arm returns its
     `coAuthorIds` (commented). Pure, no I/O.

2. **EDIT** `core/domain/src/commonMain/kotlin/.../meal/Meal.kt`
   - Added `val kind: MealKind = MealKind.Solo` (last constructor param, defaulted) with KDoc
     documenting the default and the no-migration / old-data-reads-as-Solo intent.

3. **NEW** `core/domain/src/commonTest/kotlin/.../meal/MealKindTest.kt` (3 tests):
   - `meal_constructed_without_kind_defaults_to_solo` — default-is-Solo.
   - `meal_kind_is_exhaustive_over_solo_today` — `when (kind)` exhaustive WITHOUT `else` (locks the
     one-leaf seam; adding `Together` will break compilation here until handled).
   - `solo_meal_has_exactly_one_author_the_existing_author` — `MealKind.Solo.authorIds(id)` == `{id}`.

## Decisions

- **No new error leaf.** §4 introduces no Solo-specific failure, so per the brief none was added.
- **`MealDraft` deliberately NOT touched** — §4.3 is explicit. `MealKind` does not need the draft
  to carry `kind` until the composer can produce a non-Solo kind.
- **Invariant as a pure extension function** (`authorIds`) rather than a VO wrapper — Solo carries no
  payload, so a `data object` + pure function is the lightest correct shape and gives the future
  `Together` leaf a natural home for `coAuthorIds`.
- **Exhaustiveness test with no `else`** is the seam's regression lock per §9.

## Verify

```
./gradlew :core:domain:testAndroidHostTest
```
Last 3 lines:
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 17s
20 actionable tasks: 7 executed, 13 up-to-date
```
`MealKindTest`: `tests="3" skipped="0" failures="0" errors="0"`.
`KonsistRulesTest` ran in the same task (no-Firebase/no-Android/no-Compose in `:core:domain`) and
passed — `MealKind.kt` is `commonMain` stdlib-only (`kotlin.jvm`/`AccountId` only). No new Konsist
rule needed (§10).

`feature/meal` domain was NOT touched (§4.3), so `:feature:meal:testAndroidHostTest` was not required
for this task. The pre-existing "No cast needed" warnings in other commonTest files are unrelated to
this change.

## Blockers

None.

## Follow-on (not this task)

- `w4-meal-kind-seam-data`: `MealDto.kind: String = "solo"` discriminator + `MealMapper` arm +
  `publish()` stamp + `MealMapperTest`. See the handoff for the exact shape.
- `w4-meal-kind-seam-integration`: thread `Meal.kind` through publish/feed/stats; today everything
  reads `Solo` for free (default), so this is mostly assertions + the publish stamp.
