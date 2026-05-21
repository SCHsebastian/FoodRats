# Meal: replace `tags` with a single `description` field

**Date:** 2026-05-21
**Status:** Design — pending implementation
**Supersedes (partial):** lines 368 and 903 of `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` (the `tags: List<FoodTag>` field on `Meal` and the `FoodTag` model file in the module layout).

## 1. Decision

Drop the `tags: List<FoodTag>` field on `Meal` and `MealDraft`. Replace it with a single optional free-form `description: Description` value object, capped at 280 characters. The change is purely a domain shape change — no AI integration is built in or accommodated by the design; the field is plain user-typed text. `:feature:stats`' "tag variety" leaderboard is deleted with the tag concept it depended on.

## 2. Motivation

Tags as currently modeled (`FoodTag.Curated` enum of 8 meal-time labels + arbitrary `Custom(String)`) carry almost no signal: the curated values restate `Slot` (`breakfast/lunch/dinner/snack`) which is already a required field on `Meal`, and `Custom` tags are unconstrained free text in chip clothing. A description string is honest about being prose and removes the false structure.

Trade-offs accepted:

- Lose the "tag variety" stats metric. It was a weak signal anyway — counting `Custom` tags can't distinguish "lasagna" from "Lasagna ".
- Lose chip-based UX for tag selection. Replaced by a single multi-line text field — fewer taps to publish a tag-less meal (current default), one input affordance to teach.
- No structured filtering on tags. The MVP feed has no tag filter today, so there is nothing to delete on the consumption side beyond rendering.

## 3. Scope

In scope: domain model, Firestore DTO + mapper, local-draft persistence, the meal compose / publish / preview flow, the feed + detail screens that render meals, the stats `TagVariety` leaderboard, i18n strings for both en/es, and the affected tests.

Out of scope: Firestore migration of historical `tags` arrays (existing meals are simply read with empty description — see §7), changes to the score/rating/comment subsystem, any AI/vision integration (the design treats description as plain user text — no source flag, no provider port, no generation timing).

## 4. Architecture

### 4.1 New `Description` value object — `:core:domain/meal/Description.kt`

Follows the existing `DishName` / `CommentText` value-object pattern — copy that shape exactly (same factory naming, same `Result<T, E>` Ok/Err constructors as `DishName.of` uses, same companion-object layout).

```kotlin
@JvmInline
value class Description private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 280
        val EMPTY = Description("")

        // Trim, then enforce length. Returns Err(DescriptionTooLong) on overflow.
        // Use the same Result Ok/Err constructors as DishName.of in this codebase.
        fun of(raw: String): Result<Description, MealValueObjectError> { /* see DishName.of */ }
    }
}
```

Why `EMPTY` and not a nullable field on `Meal`: keeps the domain shape uniform with the existing pattern (no nullable required-by-shape fields), and lets a feed card unconditionally read `meal.description.value` and decide whether to render based on `isNotBlank()`.

### 4.2 `MealValueObjectError` (`:core:domain`) — sealed interface, no enum

Add `DescriptionTooLong`. Remove `FoodTagBlank`.

```kotlin
sealed interface MealValueObjectError {
    data object ScoreOutOfRange    : MealValueObjectError
    data object DishNameBlank      : MealValueObjectError
    data object DishNameTooLong    : MealValueObjectError
    data object MealIdBlank        : MealValueObjectError
    data object DescriptionTooLong : MealValueObjectError   // new
    // FoodTagBlank removed
}
```

No `DescriptionBlank` — the field is optional; blank trims to `Description.EMPTY` and is valid.

### 4.3 `Meal` (`:core:domain`) and `MealDraft` (`:feature:meal`)

Swap `val tags: List<FoodTag>` → `val description: Description` on both.

`MealReadPort` returns `Meal` instances, so consumers (feed, stats) get the new field for free via the same port. No port surface change.

### 4.4 Deletions

- `core/domain/.../meal/FoodTag.kt` — entire file.
- `MealValueObjectError.FoodTagBlank` data object.
- `feature/meal/.../domain/usecase/UpdateMealDraftCommand.kt`: `SetTags(List<FoodTag>)` case removed; `SetDescription(Description)` added.
- `core/designsystem/.../molecules/FrTagChipRow.kt` — entire file. No other consumers. Catalog story `TagChipRowStory()` in `catalogApp/.../stories/MoleculeStories.kt:228-255` deleted with it.
- `feature/meal/.../i18n/MealStringKey.kt`: `TagSeparator` enum entry removed.
- `meal_tag_separator` removed from both `feature/meal/.../composeResources/values/strings.xml` and `values-es/strings.xml`.
- `feature/stats/.../domain/compute/TagVariety.kt` — entire file.
- `feature/stats/.../commonTest/.../domain/compute/TagVarietyTest.kt` — entire file.
- `StatsStringKey.TagVarietyLabel` and `TagVarietyValue` enum entries; the matching `stats_tag_variety_label` / `stats_tag_variety_value` strings in both en and es.
- `StatsSnapshot.tagVarietyCount: Int` field.
- `ObserveStatsUseCase.kt:73` line that computes it; the import on line 15.
- `StatsScreen.kt:72` row that renders it (and the surrounding leaderboard row, if it has no other content).

## 5. Data layer

### 5.1 `MealDto` (Firestore)

```kotlin
@Serializable
data class MealDto(
    // ... existing fields ...
    val description: String = "",   // new — default for read-side compat with old docs
    // tags: List<String> = emptyList()   // REMOVED
)
```

The default value of `description = ""` lets `@Serializable` deserialize pre-refactor documents that lack the field. Documents written after the refactor always include it (empty string when no description).

### 5.2 `MealMapper.toDomain()`

- Drop the entire tag-parsing block (current lines 29-32, which loops over `dto.tags` and validates via `FoodTag.Curated.entries` / `FoodTag.custom()`).
- Add: `val description = Description.of(dto.description).getOrElse { return Result.Err(MealError.Read.NotFound) }`.

Treating a too-long stored description as `NotFound` matches the existing "malformed-on-read = absent" stance for tags. In practice this can only fire if Firestore data is hand-edited around the client (we validate at write).

### 5.3 `FirebaseMealRepository.publish()` (line 140)

```kotlin
// before:
tags = draft.tags.map { it.label },
// after:
description = draft.description.value,
```

### 5.4 `MealDraftLocalStore` (DataStore JSON)

The private `MealDraftJson` schema swaps `tags: List<String>` for `description: String = ""` (default for forward-compat with existing on-disk drafts mid-rollout — they will be read with empty description, which is valid).

- Read path (current lines 58-61): drop the per-tag mapping; add `description = Description.of(json.description).getOrElse { Description.EMPTY }`. Note: unlike the Firestore mapper, the local-draft path silently falls back to `EMPTY` rather than failing the whole draft — a corrupt local draft should not strand the user.
- Write path (current line 83): replace `tags = d.tags.map { it.label }` with `description = d.description.value`.

## 6. Use case / command flow

### 6.1 `UpdateMealDraftCommand`

```kotlin
sealed interface UpdateMealDraftCommand {
    data class SetPlate(val plate: Plate) : UpdateMealDraftCommand
    data class SetDish(val name: DishName) : UpdateMealDraftCommand
    data class SetDescription(val description: Description) : UpdateMealDraftCommand   // new
    // SetTags removed
    // ... other existing commands unchanged ...
}
```

### 6.2 `UpdateMealDraftUseCase`

Replace the `SetTags` branch (current line 17) with `is UpdateMealDraftCommand.SetDescription -> current.copy(description = command.description)`.

### 6.3 `StartMealDraftUseCase`

Line 24: change `tags = emptyList()` to `description = Description.EMPTY`.

### 6.4 `PublishMealUseCase`

No precondition change. Description is optional; an empty description is a publishable meal.

## 7. Migration of historical meals

Drop-on-read. The refactored `MealMapper` ignores any `tags` array present on existing Firestore documents — the field becomes dead data. Pre-refactor meals will render with an empty description area on cards. We do not write a migration script and do not synthesize a description from old tags. Rationale: the app is in closed-group beta, the volume of pre-refactor meals is small, and synthesizing text the user did not write would be misleading.

The `tags` field stays in Firestore data forever, untouched. Security rules already do not validate it (existing rules are silent on `tags`). No `firestore.rules` change is required.

## 8. Presentation

### 8.1 `ComposePlateContract`

```kotlin
data class ComposePlateState(
    // ... existing fields ...
    val descriptionInput: String = "",   // raw text, not the value object
    val descriptionTooLong: Boolean = false,   // derived from descriptionInput.trim().length > Description.MAX_LEN
    // selectedTags: Set<String> = emptySet()   // REMOVED
)

sealed interface ComposePlateIntent {
    data class DishChanged(val name: String) : ComposePlateIntent
    data class DescriptionChanged(val text: String) : ComposePlateIntent   // new
    // TagToggled REMOVED
    // ... other existing intents unchanged ...
}
```

`descriptionInput` is held as a raw `String` in state (the live text-field value) and only converted to `Description` at persist time in `persistAndAdvance()`. This mirrors how `dishInput` is treated.

### 8.2 `ComposePlateViewModel`

- Drop the `TagToggled` handler (current lines 69-73) — no replacement needed beyond `DescriptionChanged`, which does `update { it.copy(descriptionInput = intent.text, descriptionTooLong = intent.text.trim().length > Description.MAX_LEN) }`.
- In `persistAndAdvance()` (current lines 86-92), after the existing `DishName` resolution add:

```kotlin
val description = Description.of(currentState.descriptionInput).getOrElse { error ->
    /* map MealValueObjectError → user-facing state — likely the same error pipeline DishName uses */
    return
}
dispatch(UpdateMealDraftCommand.SetDescription(description))
```

The exact error surfacing mirrors how `DishNameTooLong` is currently surfaced (consistency over invention).

### 8.3 `ComposePlateScreen`

Replace the `FrTagChipRow` block (current lines around 86) with a multi-line `FrTextField`:

- Label/placeholder: `resolve(MealStringKey.DescriptionPlaceholder)` — "Describe your meal…" / "Describe tu comida…".
- Counter below the field: `resolve(MealStringKey.DescriptionCounter, state.descriptionInput.trim().length, Description.MAX_LEN)` → renders `"45 / 280"`.
- Error state when `descriptionTooLong` is true: counter color shifts to `LocalFrSemanticColors.current.danger`; supporting text shows `resolve(MealStringKey.DescriptionTooLongError)`.
- Multi-line (`singleLine = false`), reasonable visual cap of 4-5 lines before scrolling.

Also delete the file-private `CURATED_TAGS` list at the top of the screen (current line 42).

### 8.4 `PublishMealScreen`

Line 36: `tags = draft.tags.map { it.label }` → `description = draft.description.value` (passing through to `FrMealCard.MealUi`).

### 8.5 `FrMealCard` (`:feature:meal`)

```kotlin
data class MealUi(
    // ...
    val description: String,   // replaces `val tags: List<String>`
)
```

Render path (current line 46) becomes:

```kotlin
if (ui.description.isNotBlank()) {
    FrText(text = ui.description, style = MaterialTheme.typography.bodyMedium)
}
```

No `MealStringKey.TagSeparator` lookup. No `joinToString`.

### 8.6 `FeedMealUi` and `FrFeedMealCard`

- `FeedMealUi.tags: List<String>` (line 21) → `FeedMealUi.description: String`.
- The `toFeedUi` mapper (line 43): `description = meal.description.value`.
- `FrFeedMealCard` (current lines 63-69): delete the `if (ui.tags.isNotEmpty()) { FrTagChipRow(…) }` block; replace with `if (ui.description.isNotBlank()) FrText(ui.description, …)`.

### 8.7 `MealDetailScreen` (`:feature:feed`)

Current lines 172-178: same swap — drop the `FrTagChipRow` read-only block, render the description string conditionally.

## 9. Design system & catalog

- Delete `FrTagChipRow.kt`. Audit confirms zero non-meal consumers.
- Keep `FrChip` (used elsewhere, e.g. assistive actions; the file comment explicitly distinguishes it from selection-state usage).
- Keep `FrFilterChip` — used for score selection in the rating flow, independent of tags.
- Delete `TagChipRowStory()` in `catalogApp/.../stories/MoleculeStories.kt:228-255`.
- No new catalog story is required by this refactor: the description field is rendered with the existing `FrTextField` atom which already has a catalog entry.

## 10. i18n

`MealStringKey` additions (and one removal):

| Key | en | es | Notes |
|---|---|---|---|
| `DescriptionPlaceholder` (new) | `Describe your meal…` | `Describe tu comida…` | Compose screen text-field placeholder |
| `DescriptionCounter` (new) | `%1$d / %2$d` | `%1$d / %2$d` | Live char counter; first arg current length, second `Description.MAX_LEN` |
| `DescriptionTooLongError` (new) | `Description is too long (max %1$d characters).` | `La descripción es demasiado larga (máx. %1$d caracteres).` | Surfaced when `descriptionTooLong` is true |
| `TagSeparator` (removed) | — | — | Was `meal_tag_separator` |

`StatsStringKey` removals: `TagVarietyLabel`, `TagVarietyValue`, and the matching `stats_tag_variety_label` / `stats_tag_variety_value` strings in both locales.

`FeedStringKey`: no changes (tags had no Feed string keys; description is a raw string passed through).

## 11. `MealErrorToStringKey` mapping + exhaustiveness test

The `MealErrorToStringKey` mapper (`feature/meal/.../presentation/MealErrorToStringKey.kt`) must add a case for `MealValueObjectError.DescriptionTooLong → MealStringKey.DescriptionTooLongError` and remove the `FoodTagBlank` case if one exists.

`MealErrorToStringKeyTest` (in `commonTest`) is the exhaustiveness lock for this mapper. It must:

- Drop any assertion on `FoodTagBlank`.
- Add an assertion that `DescriptionTooLong` resolves to `DescriptionTooLongError`.
- Continue to enforce that every `MealError` and `MealValueObjectError` leaf is covered by the mapper (the test pattern walks the sealed hierarchy reflectively or via an exhaustive `when` — the existing pattern decides which).

## 12. Tests

Update in place:

- `MealMapperTest`: replace `tags = listOf("dinner")` with `description = "Stir-fry with peppers"` in fixtures; assert the mapped domain `Meal.description.value` equals the trimmed input.
- `MealWithRatingsMapperTest`: same fixture swap on lines 22 and 43.
- `PublishMealUseCaseTest`: drafts initialize `description = Description.EMPTY` instead of `tags = emptyList()` (lines 34, 44, 59).
- `FeedMealUiTest`: replace `tags = emptyList()` with `description = ""` on line 40; add one case asserting that a non-blank `meal.description.value` flows into `FeedMealUi.description`.
- `MealErrorToStringKeyTest`: per §11.

New tests:

- `DescriptionTest` in `:core:domain/commonTest/.../meal/`:
  - `of(empty) → Ok(EMPTY)`
  - `of("  hello  ") → Ok("hello")` (trims)
  - `of("a".repeat(280)) → Ok` (boundary)
  - `of("a".repeat(281)) → Err(DescriptionTooLong)`
  - `EMPTY.value == ""`
- `ComposePlateViewModelTest` (extend if exists, create if not) — at minimum: `DescriptionChanged` updates state; `persistAndAdvance` with a 281-char input surfaces the too-long error without dispatching `SetDescription`.

Delete:

- `TagVarietyTest.kt` (entire file).

## 13. Konsist / arch tests

The `KonsistRulesTest` in `:core:domain` enforces the no-Firebase/no-Android/no-Compose rule on `:core:domain`. The new `Description.kt` lives in `commonMain` and uses only `kotlin.stdlib` + the in-module `Result` type, so the rule still passes. No new arch rule is required.

## 14. Firestore security rules

No change. The current rules at `firestore.rules:63-88` do not validate `tags` and will not validate `description`. The create rule restricts immutable fields to slot/dayKey/authorId/membership/deterministic id, which is correct as-is. The update rule restricts changes to denormalized rating fields, which is correct as-is (description is immutable post-publish, like dishName).

## 15. Order of work (for the implementation plan)

The implementation plan (to be generated by `writing-plans` after this spec is approved) should sequence the change roughly as:

1. `:core:domain` — `Description` value object + test, `MealValueObjectError` swap, `Meal` field swap, delete `FoodTag.kt`.
2. `:feature:meal` domain — `MealDraft` field swap, command/use-case updates.
3. `:feature:meal` data — `MealDto`, `MealMapper`, `MealDraftLocalStore`, `FirebaseMealRepository`.
4. `:feature:meal` presentation — contract, view model, screen, `FrMealCard`, `PublishMealScreen`, i18n keys + strings, error-mapper test.
5. `:feature:feed` — `FeedMealUi`, `FrFeedMealCard`, `MealDetailScreen`, feed tests.
6. `:feature:stats` — delete `TagVariety` + test + `tagVarietyCount` + render + i18n keys + strings.
7. `:core:designsystem` + catalog — delete `FrTagChipRow.kt` and `TagChipRowStory()`.
8. Run the full host-test set (per CLAUDE.md "Build, run, test") and the Konsist test, quote the green output.
9. Add a "Recent decisions (2026-05-21) — Description replaces tags" entry to `CLAUDE.md` (what/why/how), following the existing pattern of dated entries. This is how the change becomes carried-forward project context.

## 16. Risks

- **`MealErrorMapper` exhaustiveness:** if the test does not currently lock all `MealValueObjectError` leaves, the `FoodTagBlank` removal can silently compile while leaving the mapper not exhaustive on the new shape. Mitigation: extend the test to assert by walking the sealed-interface leaves, not by named cases.
- **Catalog story orphans:** missing the delete of `TagChipRowStory()` leaves an unresolved `FrTagChipRow` reference and breaks the catalog build. Caught by `catalogApp:assembleDebug`.
- **DataStore drafts mid-refactor:** if a user has an unpublished draft on disk pre-refactor, the new JSON schema reads `description = ""` for them via the `@Serializable` default and ignores their `tags`. Acceptable per the migration stance in §7. Worst case the user retypes a description; the photo/dish/slot are preserved.
