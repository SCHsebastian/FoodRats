# Handoff — w1-blind-voting-data → w1-blind-voting-presentation

Data layer is DONE and green (`:feature:crew:testAndroidHostTest` BUILD SUCCESSFUL, 13/13 VM +
6/6 mapper). What presentation needs:

## 1. How feed reads the flag (the port)
`core/domain/.../crew/CrewBlindVotingPort.kt` is now **bound** in `crewModule`:
```kotlin
interface CrewBlindVotingPort {
    fun observeBlindVoting(crewId: CrewId): Flow<Boolean>   // false when crew unknown/unreadable
}
```
- Inject it into the feed ViewModel (it's a `:core:domain` port — NO `:feature:crew` Gradle dep;
  same pattern as `CrewOwnerPort` already consumed by `MealDetailViewModel`).
- Observe `observeBlindVoting(activeCrewId)`. `activeCrewId` comes from `ActiveCrewProvider`
  (already used across feed). The binding defaults any failure/unknown crew to `false`, so you do
  NOT re-check or guard for nulls.
- **Koin wiring:** add `CrewBlindVotingPort::class` to the feed module's `*ModuleVerifyTest`
  `extraTypes` (it's provided by `crewModule`, external to the feed module's graph — exactly like
  `CrewOwnerPort::class` already is in `FeedModuleVerifyTest`).

## 2. The masking policy to apply
`core/domain/.../crew/BlindVotingPolicy.kt` (already exists from the domain task):
```kotlin
BlindVotingPolicy.shouldMaskAuthor(blindVoting, isAuthor, viewerHasVoted): Boolean
  = blindVoting && !isAuthor && !viewerHasVoted
```
When `true` → replace `authorName` / `authorAvatarUrl` with a placeholder in `FeedMealUi`.
Author + voters' vote identities are unaffected (only the meal author's identity is masked).

## 3. Where vote state lives (how "viewer has voted" is determined)
`feature/feed/.../presentation/components/FeedMealUi.kt`, fun `MealWithRatings.toFeedUi(viewerId, today, ...)`:
- `val viewer = ratingBy(viewerId)` — the viewer's own rating, or `null`.
- `val isAuthor = meal.author.accountId == viewerId`.
- `viewerRating = viewer?.score?.value` (already a field on `FeedMealUi`).
So **`viewerHasVoted = viewer != null`** (equivalently `viewerRating != null`). Both `isAuthor` and
`viewer` are already computed in `toFeedUi` — you only need to thread `blindVoting: Boolean` in as a
parameter and call the policy there.

Roadmap §1.2 also asks: "reveal after rating **or window close**." `toFeedUi` already computes
`windowOpen = today.daysSince(meal.day) in 0..1`. For the reveal-after-window-close behavior, pass
`viewerHasVoted = true` (or skip masking) once `!windowOpen` — the policy itself takes only the
three inputs, so do that gating at the call site.

## 4. Catalog + tests (presentation scope)
- Add a "blind" scenario to the `FrFeedMealCard` / `FrFeedMealRow` story (component lives in
  `feature/feed/.../presentation/components/` — feature-owned, NOT in `:core:designsystem`).
- Add a masked-vs-revealed mapping test in `FeedMealUiTest.kt`.

## firestore.rules deploy (carry forward — user must run)
The owner toggle write needs the new rule branch. The user must deploy before the toggle works
against prod:
```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```
(Read path is unaffected — `observeBlindVoting` reads the crew doc, already allowed for any
authenticated member.)
