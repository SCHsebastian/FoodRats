# Handoff — w1-reactions-data → w1-reactions-presentation

Data/infra layer is DONE and green (`:feature:feed:testAndroidHostTest` + `:feature:meal:test
AndroidHostTest` BUILD SUCCESSFUL; new tests: `ReactionMapperTest` 5/5, `FirebaseReaction
RepositoryTest` 6/6; `FeedModuleVerifyTest` now includes `MealReactionPort::class`). `firestore.
rules` compiles (dry-run verified). Build the UI against the contract below.

## 1. The port to inject (NO `:feature:meal` Gradle dep)

`MealReactionPort` is in `:core:domain.meal` and is **bound** (in `mealModule`, wired via the
`shared` aggregator). Inject it into `FeedViewModel` the same way feed already injects
`MealRatingPort` / `MealReadPort` — a `:core:domain` port, no feature dependency. It's already in
`FeedModuleVerifyTest.extraTypes`, so the graph check stays green.

```kotlin
fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>>
suspend fun toggle(
    crewId: CrewId, mealId: MealId, reactorId: AccountId, kind: ReactionKind,
): Result<ReactionToggle, ReactionError.Toggle>
```

`Result` = `es.schsebastian.foodrats.core.domain.result.Result` (NOT stdlib). `crewId` comes from
`ActiveCrewProvider` (already used across feed); `reactorId` is the viewer's `AccountId`; `kind` is
always `ReactionKind.DailyGlyph` (the only leaf).

## 2. Aggregate shape `observe` returns

`MealReactions(mealId, reactions: List<MealReaction>)` — use its read-model API; **do NOT count
per-member yourself**:
- `count: Int` → the badge number.
- `hasReacted(viewerId): Boolean` → toggled/highlighted state of the button.
- `reactionBy(viewerId): MealReaction?` → the viewer's own reaction (for the highlighted glyph).
- `reactions` → the full list, for the "who reacted" row (resolve each `reaction.reactorId` to a
  name/avatar via `AccountReadPort`, already used in feed/detail).
- `MealReactions.empty(mealId)` → a meal with none (you can also just default to count 0).

**The glyph is not stored** — render it as `DailyEmote.forDay(meal.day)` (in `:core:domain.meal`),
deterministic per day, identical for every crew member. Each `MealReaction.kind` is already a
domain `ReactionKind`; unknown persisted keys were dropped in the data layer, so every element you
get back is a known kind.

## 3. Toggle outcome + analytics

`toggle(...)` returns `Result<ReactionToggle, ReactionError.Toggle>`:
- `ReactionToggle.Added` — reaction created. **Fire the `meal_reacted` analytics leaf HERE**
  (after `Ok(Added)`, in the ViewModel, NOT in a use case, NOT on `Removed`) — CHARTER rule 9.
- `ReactionToggle.Removed` — reaction removed (no analytics, no animation-of-add).

`toggle` is a single-step port call, so the ViewModel may call the port directly (no use case
required); a thin `ToggleReactionUseCase` next to the feed ViewModel is fine if you prefer the
shape consistency. Reactions are **ambient — no push is sent on react** (server-side, none exists).

## 4. Error → StringKey (presentation scope)

Add `*ErrorToStringKey` arms ONLY for whichever `ReactionError` leaves the feed actually surfaces,
with the matching exhaustiveness test (CHARTER rule 3/6). The full tree:
```
ReactionError.Read.Unauthorized    ReactionError.Toggle.Unauthorized
ReactionError.Read.Unavailable     ReactionError.Toggle.MealNotFound
                                   ReactionError.Toggle.Offline
                                   ReactionError.Toggle.Unavailable
```
No new error leaf was needed in data; these six cover everything. (A toggle that races a meal
delete returns `Toggle.MealNotFound`.)

## 5. Catalog + tests (presentation scope)

- Add a "reacted / not-reacted" scenario to the `FrFeedMealCard` story (the component is
  feature-owned in `feature/feed/.../presentation/components/`, NOT `:core:designsystem`).
- Add a `FeedMealUi` mapping test for the reaction count + viewer-reacted flag.

## firestore.rules deploy (user must run before reactions work against prod)

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```
Until deployed, the `reactions/{uid}` write is denied. Reads are unaffected (no data exists yet).
The rule: a crew member may read all reactions; create/update/delete ONLY their own
`reactions/{uid}` doc (doc id == uid); `kind` is a 1–40 char string; `reactedAtEpochMs` within ±60s.
