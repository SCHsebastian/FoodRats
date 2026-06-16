# Handoff — w1-blind-voting-domain → data + presentation

Domain is DONE and green. Exact contracts the next two tasks implement against:

## New field (model)
`feature/crew/.../domain/model/Crew.kt`
```kotlin
data class Crew(
    /* …existing fields… */
    val blindVoting: Boolean = false,   // last param, defaulted
)
// Crew.of(...) also gained: blindVoting: Boolean = false
```
- Name: **`blindVoting`**. Default: **`false`**. Owner-settable boolean. Pre-launch → no migration.

## New cross-context port (data task binds it)
`core/domain/.../crew/CrewBlindVotingPort.kt`
```kotlin
interface CrewBlindVotingPort {
    fun observeBlindVoting(crewId: CrewId): Flow<Boolean>
}
```
- **Emit `false` when the crew is unknown/unreadable** (safe default = un-blind; never hide on a
  read failure). Mirror `CrewOwnerPort`'s impl/binding exactly.
- **w1-blind-voting-data:** bind this in `crewModule` (commonMain) over the crew read model
  (`CrewRepository.observeCrew(...).map { it.blindVoting }`, defaulting failures to `false`).
  Add `blindVoting: Boolean = false` to `CrewDto` + the crew mapper (read tolerant of missing
  field — kotlinx-serialization default).

## Masking rule (presentation task calls it)
`core/domain/.../crew/BlindVotingPolicy.kt`
```kotlin
object BlindVotingPolicy {
    fun shouldMaskAuthor(
        blindVoting: Boolean,
        isAuthor: Boolean,
        viewerHasVoted: Boolean,
    ): Boolean = blindVoting && !isAuthor && !viewerHasVoted
}
```
- **w1-blind-voting-presentation:** observe `CrewBlindVotingPort.observeBlindVoting(activeCrewId)`,
  then in the `FeedMealUi` mapping call `BlindVotingPolicy.shouldMaskAuthor(blindVoting, isAuthor,
  viewerHasVoted = viewerRating != null)`. When `true` → replace `authorName`/`authorAvatarUrl`
  with a placeholder. `viewerHasVoted` = `viewerRating != null`. For reveal-after-window-close,
  pass `viewerHasVoted = true` (or skip masking) once the rating window has closed — the rule
  itself takes only those three inputs. Author + voters' vote identities are unaffected.
  Add the "blind" scenario to the `FrFeedMealCard` catalog story + a masked-vs-revealed mapping test.

## Verify done in this task
`./gradlew :core:domain:testAndroidHostTest :feature:crew:testAndroidHostTest` → BUILD SUCCESSFUL
(incl. Konsist; `BlindVotingPolicyTest` 5 cases + `CrewTest` +2 cases).
