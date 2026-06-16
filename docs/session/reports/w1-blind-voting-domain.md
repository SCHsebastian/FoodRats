# Report — w1-blind-voting-domain

**Task:** Domain layer for "blind voting" — a per-crew, owner-settable boolean that hides a
meal's **author identity** from a crewmate until that crewmate has cast their own score.
Spec: `docs/roadmap/2026-06-14-feature-roadmap.md` §1.2.

## Scope reminder

DOMAIN ONLY. The Firestore DTO/schema is `w1-blind-voting-data`; the feed card masking +
`FeedMealUi` mapping is `w1-blind-voting-presentation`. This task delivers the flag, the
cross-context read port, and the pure masking rule + tests — no data, no UI, no toggle.

## Prior work

None. No `w1-blind-voting-*` report/handoff existed; `grep blindVoting` over `feature/crew`
returned nothing before this task. Started fresh.

## Spec reading

§1.2 explicitly states the decision (per-crew toggle `Crew.blindVoting: Boolean`, owner-set,
**default off**) and the masking condition: mask when `blindVoting && !isAuthor &&
viewerRating == null`. Per the parked product question the default is OFF so existing crews
keep current behavior. The spec masks **author identity** (`authorName`/`authorAvatarUrl`),
NOT the score itself — author always sees their own meal un-masked; voters' identities in the
votes are unaffected.

## What changed

### 1. Flag on the Crew domain model
`feature/crew/.../domain/model/Crew.kt`
- Added `val blindVoting: Boolean = false` as the last param of `data class Crew` (defaulted →
  every existing call site, fixture, and the Firestore reconstitution path compiles unchanged;
  pre-launch, no migration needed).
- Added the matching defaulted `blindVoting: Boolean = false` to the `Crew.of(...)` factory.
- KDoc on the field points at the policy + port. Used the roadmap's own ubiquitous term
  `blindVoting` (no generic name); it's a flat boolean on the aggregate root — no `CrewSettings`
  value object exists today and a single boolean doesn't warrant introducing one (the `Crew`
  model already carries its other settings, e.g. `name`, as flat fields).

### 2. Cross-context read port (so feed never depends on :feature:crew)
`core/domain/.../crew/CrewBlindVotingPort.kt` (NEW)
```kotlin
interface CrewBlindVotingPort {
    fun observeBlindVoting(crewId: CrewId): Flow<Boolean>
}
```
Mirrors the existing `CrewOwnerPort` exactly (same package, same `Flow`-of-projection shape,
same "emit the safe default when the crew is unknown/unreadable" contract — here `false`, i.e.
un-blind, so a read failure can never accidentally hide identities). Feed already imports
`CrewOwnerPort`/`ActiveCrewProvider` from `:core:domain`, so this is the established pattern.
To be **bound in `crewModule`** over the crew read model by the data task.

### 3. Pure masking rule
`core/domain/.../crew/BlindVotingPolicy.kt` (NEW)
```kotlin
object BlindVotingPolicy {
    fun shouldMaskAuthor(
        blindVoting: Boolean,
        isAuthor: Boolean,
        viewerHasVoted: Boolean,
    ): Boolean = blindVoting && !isAuthor && !viewerHasVoted
}
```
Pure, vendor-/platform-/presentation-free, reusable by the feed mapping and the catalog story.
`viewerHasVoted` is the spec's `viewerRating != null`. Reveal-after-window-close is left to the
caller (it depends on the rating-window state feed already tracks): when the window has closed
the presentation task passes `viewerHasVoted = true` (or simply doesn't mask), so the rule
itself stays the three-input decision the roadmap lists.

### 4. Tests (commonTest)
- `core/domain/.../crew/BlindVotingPolicyTest.kt` (NEW) — 5 cases: masks when on+not-author+
  not-voted; never masks when off; author always sees own; voting reveals; off+author+voted.
- `feature/crew/.../domain/model/CrewTest.kt` — +2 cases: `blindVoting_defaults_off`,
  `addMember_preserves_blindVoting`.

## Decisions

- **Name:** `blindVoting` (the roadmap's own term; ubiquitous, not generic). Not
  `scoresHiddenUntilVoted` — that name implies masking the score, but the spec masks author
  identity, so the chosen name matches the actual behavior the crew toggles.
- **No `CrewSettings` VO.** A single boolean on the aggregate doesn't justify a wrapper; the
  other crew settings are already flat fields. If a second setting lands, revisit then.
- **New port, not a field on an existing one.** `ActiveCrewProvider` is a per-device preference
  (`CrewId?`), not crew state; `CrewMembershipPort`/`CrewOwnerPort` are per-concern projections.
  A dedicated `CrewBlindVotingPort` keeps each port single-purpose, exactly like `CrewOwnerPort`.
- **Rule lives in `:core:domain`** (not `:feature:feed`) so it's reusable and the presentation
  task can call it directly without re-deriving the condition.
- **Default `false` everywhere** (model field, factory, port-on-failure) — existing crews and
  unreadable crews behave exactly as today.

## Verify

`./gradlew :core:domain:testAndroidHostTest :feature:crew:testAndroidHostTest`
```
> Task :feature:crew:testAndroidHostTest
> Task :core:domain:testAndroidHostTest

BUILD SUCCESSFUL in 18s
```
Both green, including the `:core:domain` Konsist architecture test (proves the new port +
policy import no Firebase/Android/Compose). `:feature:feed:testAndroidHostTest` was NOT run —
no feed-facing consumed type changed in this task (the port isn't wired into feed yet; that's
the presentation task). The new port compiles in `:core:domain`, which feed depends on.

## Blockers

None.

## Next tasks consume

`w1-blind-voting-data` (bind `CrewBlindVotingPort` over the crew read model in `crewModule`;
add `blindVoting` to `CrewDto` + mapper) and `w1-blind-voting-presentation` (read the port for
the active crew, call `BlindVotingPolicy.shouldMaskAuthor` in `FeedMealUi` mapping, mask
`authorName`/`authorAvatarUrl`, catalog "blind" scenario). See the handoff for exact signatures.
