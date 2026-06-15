# Report — `w1-reactions-domain`

Domain layer for lightweight meal reactions (roadmap §1.3). DOMAIN ONLY — no Firestore DTO,
no repository, no feed UI (those are `w1-reactions-data` / `w1-reactions-presentation`).

## Prior work check

None. No `*reaction*` Kotlin source, no prior report/handoff for this task. Started fresh.

## Spec (roadmap §1.3) — what it dictates

> Domain: `MealReaction` (mealId, crewId, reactorId, reactedAt) + `MealReactionPort`
> (`observe`, `toggle`) in `:core:domain`. **Decision:** single fixed daily glyph vs. small
> fixed set (😋🔥🤤). **Default: today's `DailyEmote` only — reinforces the daily ritual.**
> … **Decision:** push author on reaction? **Default no** (noise) — reactions are ambient.

The spec resolves both product decisions explicitly, so I followed them exactly rather than
choosing a 😋🔥🤤 picker.

## Decisions

1. **Reaction set = the daily glyph only, modeled as a sealed `ReactionKind` with one leaf.**
   The MVP reaction is the meal-day's deterministic `DailyEmote` (`DailyEmote.forDay(meal.day)`),
   identical for all crew members that day. The glyph is therefore **derived at render time and
   NOT persisted** — only the *fact* a member reacted is stored. I modeled `ReactionKind` as a
   `sealed interface` with a single `data object DailyGlyph` leaf (not a bare boolean, not an
   enum) so a future small fixed set can add `data object` leaves — each with its own persisted
   `key` discriminator — without breaking the `MealReactionPort` contract or the stored shape.
   This reconciles the spec ("daily glyph only") with the project's "`data object` keeps the door
   open" convention and the brief's "fixed `ReactionKind` set".
2. **One reaction per member per meal, toggleable.** Firestore doc id == reactor uid (data
   task). `MealReactions` (the read model) defends this on read via `reactionBy(reactorId)` /
   `hasReacted(reactorId)`, which surface a single reaction per member even if duplicates ever
   leak. `toggle` adds when absent, removes when present, and reports which via `ReactionToggle`.
3. **No push on react.** Documented in the `MealReactionPort` KDoc as an adapter invariant; no
   domain artifact needed.
4. **Reaction is SEPARATE from Score.** No coupling to `MealRating`/`Score`/`MealWithRatings`.
   It is the affirmation counterpart to the numeric vote, deliberately not a like-counter.
5. **Layering.** Read + write both live on one port `MealReactionPort` in `:core:domain`
   (mirrors `MealCommentPort`, which also bundles observe + write). `:feature:feed` will own
   the adapter (Firestore subcollection + repository) AND the toggle use case + feed affordance;
   it consumes only this domain port, so no feature-to-feature dependency. No use case was added
   in this task — `toggle` is a thin, single-step contract over the port (like `MealRatingPort
   .rate`); a `ToggleReactionUseCase` belongs in `:feature:feed` next to its ViewModel, not in
   `:core:domain`. The one-per-member rule is the only non-trivial invariant and it lives as a
   pure function on `MealReactions`.

## Files added

- `core/domain/src/commonMain/kotlin/.../meal/ReactionKind.kt`
  — sealed `ReactionKind` (`DailyGlyph` leaf, stable `key`, `all`/`fromKey`).
- `core/domain/src/commonMain/kotlin/.../meal/MealReaction.kt`
  — `MealReaction` (mealId, crewId, reactorId, kind, reactedAt) + `MealReactions` read model
  (`count`, `reactionBy`, `hasReacted`, `empty`).
- `core/domain/src/commonMain/kotlin/.../meal/MealReactionPort.kt`
  — `MealReactionPort` (`observe`, `toggle`), `ReactionToggle` (`Added`/`Removed`),
  `ReactionError` (`Read.*`, `Toggle.*`).
- `core/domain/src/commonTest/kotlin/.../meal/MealReactionTest.kt` — 5 tests (read-model
  invariants incl. one-per-member).
- `core/domain/src/commonTest/kotlin/.../meal/ReactionKindTest.kt` — 5 tests (single kind, key
  round-trip, unknown-key null, stable key, glyph = `DailyEmote.forDay`).

No existing files modified. No vendor/Android/Compose imports (Konsist green).

## Type / contract summary (for the data + presentation tasks)

```kotlin
sealed interface ReactionKind { val key: String
    data object DailyGlyph : ReactionKind { override val key = "daily_glyph" }
    companion object { val all: List<ReactionKind>; fun fromKey(key: String): ReactionKind? }
}

data class MealReaction(mealId: MealId, crewId: CrewId, reactorId: AccountId,
                        kind: ReactionKind, reactedAt: Instant)

data class MealReactions(mealId: MealId, reactions: List<MealReaction>) {
    val count: Int
    fun reactionBy(reactorId: AccountId): MealReaction?
    fun hasReacted(reactorId: AccountId): Boolean
    companion object { fun empty(mealId): MealReactions }
}

interface MealReactionPort {
    fun observe(crewId, mealId): Flow<Result<MealReactions, ReactionError.Read>>
    suspend fun toggle(crewId, mealId, reactorId, kind): Result<ReactionToggle, ReactionError.Toggle>
}
sealed interface ReactionToggle { data object Added; data object Removed }
sealed interface ReactionError {
    sealed interface Read   : ReactionError { Unauthorized; Unavailable }
    sealed interface Toggle : ReactionError { Unauthorized; MealNotFound; Offline; Unavailable }
}
```

## Verification

```
$ ./gradlew :core:domain:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s
20 actionable tasks: 6 executed, 14 up-to-date
```

`testAndroidHostTest` runs `commonTest` + the Konsist `KonsistRulesTest` (no-Firebase/no-Android/
no-Compose in `:core:domain`) — both green. New tests confirmed executed from the XML results:
`ReactionKindTest` tests=5 failures=0 errors=0, `MealReactionTest` tests=5 failures=0 errors=0.
(Pre-existing "No cast needed" warnings in unrelated `CommentTextTest`/`ResultTest`/etc. are not
from this change.)

## Blockers

None.

## Suggested next

`w1-reactions-data` — Firestore subcollection `crews/{crewId}/meals/{mealId}/reactions/{uid}` +
`MealReactionPort` impl in `:feature:feed`'s adapter layer, + `firestore.rules` (member-only,
one doc per uid, toggle own only). Then `w1-reactions-presentation` (react affordance + who-reacted
row on `FrFeedMealCard`, reflect in `FeedMealUi`) and the `meal_reacted` analytics leaf.
