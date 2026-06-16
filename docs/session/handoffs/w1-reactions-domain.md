# Handoff — `w1-reactions-domain` → data + presentation

Domain layer is DONE and green. Build the adapter + UI against these exact contracts. All types
live in package `es.schsebastian.foodrats.core.domain.meal` in `:core:domain`.

## Exact type names (do not rename)

- `ReactionKind` — sealed interface. ONE leaf: `ReactionKind.DailyGlyph`.
  - `ReactionKind.key: String` — the **persisted discriminator**. `DailyGlyph.key == "daily_glyph"`.
  - `ReactionKind.all: List<ReactionKind>` and `ReactionKind.fromKey(key): ReactionKind?`.
- `MealReaction` — `data class(mealId: MealId, crewId: CrewId, reactorId: AccountId,
  kind: ReactionKind, reactedAt: kotlin.time.Instant)`.
- `MealReactions` — read model `data class(mealId: MealId, reactions: List<MealReaction>)` with:
  - `count: Int`
  - `reactionBy(reactorId: AccountId): MealReaction?`
  - `hasReacted(reactorId: AccountId): Boolean`
  - `MealReactions.empty(mealId): MealReactions`
- `MealReactionPort` — the cross-context port to implement (data) / inject (presentation).
- `ReactionToggle` — sealed: `ReactionToggle.Added`, `ReactionToggle.Removed`.
- `ReactionError` — sealed tree (see leaves below).

## Fixed reaction set

ONE kind only: `ReactionKind.DailyGlyph`. **The glyph is NOT stored.** Render it as
`DailyEmote.forDay(meal.day)` (already in `:core:domain.meal.DailyEmote`) — deterministic per day,
identical for every crew member. Persist only `kind.key` (`"daily_glyph"`) on the doc.

## Port signatures (`MealReactionPort`)

```kotlin
fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>>
suspend fun toggle(
    crewId: CrewId, mealId: MealId, reactorId: AccountId, kind: ReactionKind,
): Result<ReactionToggle, ReactionError.Toggle>
```

`Result` is `es.schsebastian.foodrats.core.domain.result.Result` (NOT stdlib).

## Error leaves to map

```
ReactionError.Read.Unauthorized      ReactionError.Toggle.Unauthorized
ReactionError.Read.Unavailable       ReactionError.Toggle.MealNotFound
                                     ReactionError.Toggle.Offline
                                     ReactionError.Toggle.Unavailable
```

Presentation: add a `*ErrorToStringKey` mapper for whichever of these the feed surfaces, with a
matching exhaustiveness test (CHARTER rule 3 / 6).

## The one-per-member rule (data MUST enforce; presentation reads)

- **Data:** Firestore doc id == reactor uid → `crews/{crewId}/meals/{mealId}/reactions/{uid}`.
  `toggle` is intent-idempotent: if a doc for `reactorId` with this `kind` exists → DELETE it and
  return `ReactionToggle.Removed`; else CREATE it and return `ReactionToggle.Added`. Member-only;
  a member may write only their OWN doc. Rules: member-of-crew read, write own doc only.
  **No push on react** (roadmap §1.3 — reactions are ambient).
- **Presentation:** never count per-member yourself — use `MealReactions.count` for the badge and
  `MealReactions.hasReacted(viewerId)` / `reactionBy(viewerId)` for the toggled/highlighted state.
  Persisted-key→kind via `ReactionKind.fromKey`; unknown keys return `null` (skip them — forward
  compat). Reflect into `FeedMealUi` and add the react affordance + who-reacted row on
  `FrFeedMealCard` (domain-aware → lives in `:feature:feed/presentation/components`, NOT designsystem).

## Layering notes

- `MealReactionPort` is in `:core:domain`, so `:feature:feed` (adapter + UI) consumes it WITHOUT
  depending on `:feature:meal`. Mirror `MealCommentPort` (bundled observe + write) and
  `MealRatingPort` (carries the actor id explicitly).
- **No use case in domain.** Put a `ToggleReactionUseCase` next to the feed ViewModel in
  `:feature:feed` if you want one; `toggle` is a single-step port call, so a ViewModel may call the
  port directly. The only pure invariant (one-per-member view) already lives on `MealReactions`.
- Analytics: `meal_reacted` `AnalyticsEvent` leaf, fired in the ViewModel AFTER `toggle` returns
  `Ok(Added)` (NOT on `Removed`, NOT in a use case) — CHARTER rule 9.

## Verify (this task)

`./gradlew :core:domain:testAndroidHostTest` → BUILD SUCCESSFUL (Konsist + commonTest green;
`MealReactionTest` 5/5, `ReactionKindTest` 5/5).
