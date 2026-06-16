# Handoff — w4-meal-kind-seam-domain → data + integration

The domain seam for meal post types is landed. Solo is the only live leaf; everything reads Solo by
default. `Together` is DEFERRED (spec §5, gated by §13). Build the data/integration tasks as "follow
the spec," not "rediscover invariants."

## What landed (domain)

### The type — `core/domain/.../meal/MealKind.kt`
```kotlin
sealed interface MealKind {
    data object Solo : MealKind
    // Future (DEFERRED, spec §5): data class Together(val coAuthorIds: Set<AccountId>) : MealKind
    //   coAuthorIds = full participant set INCLUDING the creator (Meal.author).
}

/** Solo invariant: a Solo meal has exactly one author = the existing author. */
fun MealKind.authorIds(authorId: AccountId): Set<AccountId> = when (this) {
    MealKind.Solo -> setOf(authorId)
    // Future: is MealKind.Together -> coAuthorIds
}
```
- `commonMain`, stdlib-only (imports `kotlin.jvm` via nothing + `AccountId`) → Konsist-clean.

### Attached to the read model — `core/domain/.../meal/Meal.kt`
- Added as the **last** constructor param, **defaulted**:
  ```kotlin
  val kind: MealKind = MealKind.Solo,
  ```
- **Default = `MealKind.Solo`.** Every existing `Meal(...)` construction site compiles unchanged;
  feed/stats read `Solo` for every meal. Pre-launch: NO migration; old Firestore docs with no `kind`
  field read as `Solo`.

### NOT touched (deliberate, per spec §4.3)
- `MealDraft` (`feature/meal/.../domain/model/MealDraft.kt`) has **no `kind` field**. It is
  implicitly Solo. Do NOT add `kind` to it in the data task — the composer can't yet produce another
  kind. The draft gains `kind` only when `Together` is built (§5.4).
- `MealReadPort` surface is unchanged (it returns `Meal`, so the new field rides along for free).
- No new error leaf (§4 has no Solo-specific failure).

## For `w4-meal-kind-seam-data` (DTO + mapper)

Serialization shape — a **string discriminator field** named `kind`, per spec §4.3:

### `MealDto` (`feature/meal/.../data/firebase/MealDto.kt`)
```kotlin
val kind: String = "solo",   // new — default reads pre-seam docs as Solo
// Future: val coAuthorIds: List<String> = emptyList()
```
- Default `"solo"` so old docs missing the field deserialize as Solo.

### `MealMapper.toDomain()` — tolerant mapping (forward-compat)
```kotlin
val kind = when (dto.kind) {
    "solo" -> MealKind.Solo
    else   -> MealKind.Solo   // unknown / "together" → Solo until the Together build adds the arm
}
// then construct Meal(..., kind = kind)
```
- Keep the `else -> Solo` and its comment — §6.2/§12 require it (the future Together task replaces
  `else` with an explicit `"together"` arm + an exhaustiveness test).

### `FirebaseMealRepository.publish()`
- Stamp `kind = "solo"` on the written DTO. (Branch on `draft.kind` only when Together ships.)

### `MealDraftLocalStore`
- No change (`MealDraft` has no `kind`).

### `MealMapperTest` (commonTest) — assert:
- `kind = "solo"` → `MealKind.Solo`; **missing** `kind` (old doc) → `Solo`; **unknown** `kind` →
  `Solo`. Publish writes `kind = "solo"`.

## For `w4-meal-kind-seam-integration`

- Nothing branches on `Meal.kind` yet; with the default, feed/stats already see `Solo` for free.
- Thread the value end-to-end: confirm `PublishMealUseCase` → repo write stamps `solo`, the round
  trip (publish → read) yields `MealKind.Solo`, and the existing meal flow (`assembleDebug`)
  is unaffected. The domain exhaustiveness test (`MealKindTest`) already locks the one-leaf seam.

## Verify (this task)
`./gradlew :core:domain:testAndroidHostTest` → BUILD SUCCESSFUL; `MealKindTest` 3/3, Konsist green.
