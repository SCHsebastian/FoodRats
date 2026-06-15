# Meal post types: `Solo` now, multi-author `Together` deferred

**Date:** 2026-06-14
**Status:** Design — the `MealKind` seam is pending implementation; the multi-author `Together` type is **DEFERRED** (designed here, not built now).
**Relates to:** the `Meal` aggregate in `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §5. Additive — supersedes nothing.

## 1. Decision

Introduce a `MealKind` discriminator on the `Meal` aggregate with a single live leaf, `MealKind.Solo` (one author per meal — the current and only behaviour). **Do not build multi-author meals now.** Fully specify a future `MealKind.Together` — a dish co-owned by several crew members, where everyone (the co-authors included) can score it — so that it lands later as a *pure extension*: no invariant rewrite, no Firestore migration. The seam shipped now is **behaviourally inert** — every meal is `Solo`, every existing code path is unchanged in effect.

## 2. Motivation

The roadmap's original "Feast" item (`docs/roadmap/2026-06-14-feature-roadmap.md` §4.1) proposed building multi-author meals immediately, which breaks two load-bearing invariants at once:

1. **Deterministic single-author id.** `MealId = "{crewId}_{authorId}_{day}_{slot}"` (`core/domain/.../meal/MealId.kt`, `forDaySlot`) bakes a single author into the primary key.
2. **Authors can't rate their own meal.** `MealWithRatings` / the feed expose `canRate = !isAuthor && viewerHasNotRated && windowOpen`; `RateMealUseCase` + `firestore.rules` enforce `CannotRateOwnMeal`.

Per the request we keep single-author fully intact and pay only the near-zero cost of a forward marker. Pre-launch ("schema changes are free" — there are no legacy documents to migrate), adding a `kind` field costs nothing and the read path tolerates it from day one. Keeping `Together` *designed-but-deferred* turns the future work into "follow the spec" instead of "rediscover the invariants under pressure."

## 3. Scope

**In scope (build now)** — the inert seam:
- `MealKind` sealed interface in `:core:domain`.
- Thread `kind` through `Meal` + `MealDto` + `MealMapper` (read-tolerant; write stamps `Solo`).
- Tests asserting the default and the DTO round-trip.

**Deferred (designed in §5, NOT built now)** — everything multi-author: `MealKind.Together`, the multi-author id scheme, the relaxed scoring guard, stats attribution, delete rights, the "Together" compose UI, the push fan-out change, and the security-rule branch. Each is specified so it can be lifted into a self-contained future plan.

**Out of scope:** any change to scoring, stats, comments, or feed *behaviour* now. The seam must not alter a single rendered pixel or computed stat.

## 4. Architecture — the seam (build now)

### 4.1 `MealKind` — `:core:domain/meal/MealKind.kt`

Sealed interface, house convention (`data object` leaf keeps the door open to payload; the future `Together` is a `data class` carrying its participants):

```kotlin
sealed interface MealKind {
    /** One author — the only live kind today. */
    data object Solo : MealKind
    // Future (see §5): data class Together(val coAuthorIds: Set<AccountId>) : MealKind
    //   coAuthorIds is the full participant set INCLUDING the creator.
}
```

`commonMain`, stdlib-only → Konsist-clean (the `:core:domain` no-Firebase/no-Android/no-Compose rule still holds).

### 4.2 `Meal` gains `kind`

`core/domain/.../meal/Meal.kt`: add

```kotlin
val kind: MealKind = MealKind.Solo,
```

The default keeps every existing construction site compiling unchanged and means feed/stats read `Solo` for every meal. `MealReadPort` returns `Meal`, so feed and stats receive the field for free — **no port surface change**. `MealAuthor` stays the single authoritative author for `Solo`.

### 4.3 `MealDraft` — untouched now

`MealDraft` (`feature/meal/.../domain/model/MealDraft.kt`) is implicitly `Solo`; we do **not** add `kind` to it yet (no UI produces another kind). `PublishMealUseCase` stamps `MealKind.Solo` when it constructs the `Meal`. §5.4 covers extending the draft when `Together` is built.

## 5. The deferred `Together` type — full forward design (DO NOT build now)

This section is the turnkey design for the future multi-author plan. Nothing here is implemented by this spec.

### 5.1 Identity

`Together` cannot use the deterministic `{crewId}_{authorId}_{day}_{slot}` id — there is no single author and multiple Together meals per day are allowed. Use a **client-generated stable id** (a draft UUID), so offline retry stays idempotent (a re-published draft can't duplicate). Stored at the same path, `crews/{crewId}/meals/{mealId}`. Together meals do **not** occupy a member's `Solo` (day, slot) uniqueness slot.

### 5.2 Authorship

- `Meal.author: MealAuthor` = the **creator** (who pressed publish).
- `kind.coAuthorIds: Set<AccountId>` = the **full participant set, including the creator**.
- Feed card renders stacked avatars + "Maria, Tom & Sam" (resolved live via `AccountReadPort.observeMany`).

### 5.3 Scoring — "all can punctuate"

For `kind is Together`, **every crew member may rate, co-authors included** (the request: "the dish belongs to all and all can punctuate"). The `MealRating` / `MealRatingPort` shapes are unchanged; the `!isAuthor` exclusion is dropped *only* for `Together`:

```kotlin
val canRate = when (meal.kind) {
    MealKind.Solo            -> !isAuthor && viewerHasNotRated && windowOpen
    is MealKind.Together     -> viewerHasNotRated && windowOpen   // co-authors included
}
```

`firestore.rules` must mirror this: the `CannotRateOwnMeal` guard applies to `Solo` only. One vote per rater still holds.

### 5.4 Compose flow

A "Together" mode in the composer: select participants from the **active crew's** members (multi-select `Member`s, min 2, max crew size), one photo, one dish/description, publish. `MealDraft` gains `kind: MealKind = MealKind.Solo` and a participant selection; new `UpdateMealDraftCommand` cases (`SetKind`, `ToggleCoAuthor`) and `ComposePlateIntent`s. Publishing a `Together` draft skips the per-(author, slot) "already posted today" precondition.

### 5.5 Stats attribution — `feature/stats/.../domain/compute/ComputeWindow.kt`

- **`mostProlific`**: a Together meal counts **once toward each co-author's** post count.
- **`bestCook` / `mostCriticized`**: **excluded** — there is no single cook to average. (Avoids one member's average being moved by a shared dish.)
- **`bestMeal` / `mostVotedMeal`**: **eligible**, attributed to the group (award shows all co-authors).
- **Ingredient usage** (`mostUsedIngredient`, `topByMember`): the meal's confirmed ingredients count **once** crew-wide; per-member attribution credits each co-author once.
- **Streaks** (`personalStreak`, `crewStreak`): a Together meal satisfies "posted that day" for **every** co-author.

### 5.6 Delete rights

`MealDeletePort` extends to: **creator OR crew owner** (matches today's author-or-owner RBAC, with "author" = creator). A co-author who is neither creator nor owner cannot delete. (Decision §13.3.)

### 5.7 Notifications

`functions/src/triggers/onMealCreated.ts` must push to the crew **excluding all `coAuthorIds`**, not just the single author (read `kind`/`coAuthorIds` off the doc). Deep link unchanged.

### 5.8 Security rules

Add a `kind == "together"` create branch to `firestore.rules`: validate `coAuthorIds ⊆ crew members`, `2 ≤ |coAuthorIds| ≤ crewSize`, a non-deterministic id (not the `forDaySlot` pin), and creator ∈ `coAuthorIds`. The score rule drops `CannotRateOwnMeal` for `together`. The delete rule allows creator-or-owner.

### 5.9 Analytics

`MealPublished` already carries `audience_crew_count`; add `kind` (`solo|together`) and `coauthor_count`. New leaf optional: `together_meal_published`. No PII (ids only, consistent with the taxonomy).

## 6. Data layer (build now)

### 6.1 `MealDto` — `feature/meal/.../data/firebase/MealDto.kt`

```kotlin
@Serializable
data class MealDto(
    // ... existing fields ...
    val kind: String = "solo",   // new — default reads pre-seam docs as Solo
    // Future: val coAuthorIds: List<String> = emptyList()
)
```

### 6.2 `MealMapper.toDomain()`

Map the discriminator, tolerant of unknown values (forward-compat for when `Together` ships before all clients update):

```kotlin
val kind = when (dto.kind) {
    "solo" -> MealKind.Solo
    else   -> MealKind.Solo   // unknown/"together" → Solo until the Together build adds the arm
}
```

### 6.3 `FirebaseMealRepository.publish()`

Stamp `kind = "solo"` on write. (When `Together` ships, branch on `draft.kind`.)

### 6.4 `MealDraftLocalStore`

No change now (`MealDraft` has no `kind` yet).

## 7. Presentation

No change now. The `Together` feed card (stacked avatars + multi-name authorship) and the composer "Together" mode are sketched in §5.2 / §5.4 and built with the future plan.

## 8. i18n

None now. Future `MealStringKey` additions (deferred): `TogetherMode`, `TogetherParticipants`, an authorship join template `"%1$s, %2$s & %3$s"` (and an "+N more" variant). All user-visible, en/es, per the i18n rule.

## 9. Tests (build now)

`:core:domain/commonTest/.../meal/`:
- `Meal` constructed without `kind` defaults to `MealKind.Solo`.
- `MealKind` is a sealed interface with exactly one leaf today (a `when (kind)` over it is exhaustive with the single `Solo` arm — locks the seam).

`feature/meal` `MealMapperTest`:
- DTO with `kind = "solo"` → `MealKind.Solo`; DTO missing `kind` (old doc) → `Solo`; DTO with an unknown `kind` → `Solo`.
- Publish writes `kind = "solo"`.

## 10. Konsist / arch tests

`MealKind.kt` is `commonMain` stdlib-only → the `:core:domain` Konsist import rule still passes. No new rule.

## 11. Order of work (seam only)

1. `:core:domain` — add `MealKind.kt`; add `kind` to `Meal` (defaulted); domain tests.
2. `:feature:meal` data — `MealDto.kind`, `MealMapper` arm, `publish()` stamp, `MealMapperTest`.
3. Run `:core:domain:testAndroidHostTest` + `:feature:meal:testAndroidHostTest` + `:androidApp:assembleDebug` + iOS link; quote green output.
4. **No** CLAUDE.md "Recent decisions" entry until the seam is implemented (this is a spec, not a landed change).

## 12. Risks

- **Speculative-generality smell.** A one-leaf `sealed interface` looks like over-engineering. Mitigation: it is one file + one defaulted field + one mapper arm, fully inert, and explicitly justified by the foreseen `Together` type. If `Together` is ever cancelled, deleting the seam is a 3-line revert.
- **Mapper non-exhaustiveness when `Together` lands.** The `else -> Solo` arm will silently swallow real `Together` docs if a future dev forgets to add the arm. Mitigation: §6.2 comments the arm, and the future plan's first task is replacing `else` with explicit `"together"` handling + an exhaustiveness test.

## 13. Open decisions (gate the future `Together` build, not the seam)

1. **Final ubiquitous name** — `Together` (matches the request's "together type"), `Feast`, or `Gathering`. Default: **`Together`**.
2. **Do co-authors rate their own Together meal?** Default **yes** — "all can punctuate."
3. **Delete rights** — creator/owner only, or any co-author? Default **creator or crew owner**.
4. **Does a Together meal consume a co-author's Solo (day, slot)?** Default **no** — independent; multiple Together meals per day allowed.
5. **`bestCook` attribution** — excluded (default) vs. credited to each co-author's average.
6. **Participant bounds** — default **2 ≤ n ≤ crew size**.
