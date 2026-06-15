# Report — w1-blind-voting-data

Data/infra layer for blind voting: persist the crew `blindVoting` flag in Firestore, bind
`CrewBlindVotingPort`, and add an owner-only toggle in CrewSettings. Default OFF. Feed-card
masking is explicitly out of scope (that's `w1-blind-voting-presentation`).

## Prior work check

No prior work for this task existed (no report/handoff on disk). The domain task
(`w1-blind-voting-domain`) had already landed `Crew.blindVoting: Boolean = false`,
`core/domain/.../crew/CrewBlindVotingPort.kt`, and `BlindVotingPolicy.kt` — all untouched here.

## What changed

### Persistence (DTO ↔ domain)
- `feature/crew/.../data/firebase/CrewDto.kt` — added `val blindVoting: Boolean = false` (last
  field). kotlinx-serialization default → old crew docs without the field deserialize to `false`.
  No migration (pre-launch).
- `feature/crew/.../data/firebase/CrewMapper.kt` — `toDomain()` now passes `blindVoting = blindVoting`
  into `Crew.of(...)`.

### Port binding
- `feature/crew/.../di/CrewModule.kt` — bound `single<CrewBlindVotingPort>` as an anonymous object
  over `CrewRepository.observeCrew(crewId).map { ... }`, returning `r.value.blindVoting` on `Ok`
  and **`false` on `Err`** (safe default = un-blind; never hide on a read failure). Mirrors the
  existing `CrewOwnerPort` binding exactly. No `extraTypes` change in `CrewModuleVerifyTest` needed —
  the port is bound *inside* the module, not an external dependency.

### Owner toggle (write path)
- `feature/crew/.../domain/repository/CrewRepository.kt` — new
  `suspend fun setBlindVoting(crewId, requestedBy, enabled): Result<Unit, CrewError>`.
- `feature/crew/.../data/firebase/CrewDataSource.kt` — new `setBlindVoting(crewId, enabled)`.
- `feature/crew/.../data/firebase/CrewFirestoreDataSource.kt` — impl: single
  `withContext(dispatchers.io)` doing `crewsCol.document(id).update("blindVoting" to enabled)`,
  errors mapped via `errorMapper.map(it)` → typed `CrewError`. Mirrors `renameCrew`.
- `feature/crew/.../data/repository/FirebaseCrewRepository.kt` — `setBlindVoting` does the
  owner check (`fetchOnce` → `ownerId != requestedBy` → `CrewError.Authorization.NotOwner`) then
  delegates to the datasource. Mirrors `renameCrew`/`deleteCrew` (the repo orchestrates the auth
  check; the datasource owns the IO boundary — the existing pattern in this module).
- `feature/crew/.../domain/usecase/SetBlindVotingUseCase.kt` — NEW. Resolves the acting account
  from `session.requireCurrent()` then calls `repository.setBlindVoting(crewId, accountId, enabled)`.
  Mirrors `RenameCrewUseCase`. No `withContext`.
- Registered `factoryOf(::SetBlindVotingUseCase)` and threaded the new use case into the
  `CrewSettingsViewModel` Koin binding (now 9 `get()` positional args).

### Presentation (MVI, single source of truth)
- `CrewSettingsContract.kt` — added `isSavingBlindVoting: Boolean = false` to state and a new
  `data class ToggleBlindVoting(val enabled: Boolean)` intent. The toggle's *value* is read from
  `state.crew.blindVoting` (the crew is already the single source of truth in state) — no parallel
  field for the flag itself; only the transient `isSavingBlindVoting` guard.
- `CrewSettingsViewModel.kt` — new `setBlindVoting: SetBlindVotingUseCase` ctor param (positioned
  between `deleteCrew` and `leaveCrew` to match the Koin `get()` order) + `doToggleBlindVoting`:
  sets `isSavingBlindVoting=true`, calls the use case, clears the flag on `Ok` (the Firestore crew
  listener re-emits the updated crew → state.crew.blindVoting flips), surfaces `r.error` on `Err`.
  No `withContext` in the VM.
- `CrewSettingsScreen.kt` — new private `BlindVotingCard` composable rendered **only inside
  `if (state.isOwner)`** (right after the rename card). Label + description text + an `FrSwitch`
  (not raw Material3); `enabled = !state.isSavingBlindVoting`; `checked = crew.blindVoting`.
  Non-owners never see the card.

### i18n (both locales)
- `CrewStringKey.kt` — `SettingsBlindVotingSection`, `SettingsBlindVotingLabel`,
  `SettingsBlindVotingDescription`.
- `composeResources/values/strings.xml` + `values-es/strings.xml` — populated all three keys
  (en + es).
- No new `CrewError` leaf was needed — existing `Authorization.NotOwner` + `Backend.*` cover the
  failure modes, so `CrewErrorToStringKey` and its exhaustiveness test are unchanged.

### Security rules
- `firestore.rules` — added a 5th branch to the `crews/{crewId}` `update` rule: owner-only
  (`resource.data.ownerId == request.auth.uid`), `affectedKeys().hasOnly(['blindVoting'])`,
  `request.resource.data.blindVoting is bool`. Non-owners and any multi-field write still fail.

### Tests
- `CrewMapperTest.kt` — `toDomain_defaults_blindVoting_to_false_when_absent`,
  `toDomain_carries_blindVoting_true_when_set`.
- `CrewSettingsViewModelTest.kt` — `owner_toggles_blind_voting_on_and_state_reflects_it`,
  `non_owner_toggle_blind_voting_surfaces_authorization_error`; updated `buildVm` for the new param.
- `FakeCrewRepository.kt` + `FakeCrewDataSource.kt` — implement the new method (capture +
  stubbable result), default behavior does the owner check & flips the flag.

## Verify

```
$ ./gradlew :feature:crew:testAndroidHostTest
> Task :feature:crew:testAndroidHostTest
BUILD SUCCESSFUL in 5s
90 actionable tasks: 20 executed, 70 up-to-date
```

Per-class result XML: `CrewSettingsViewModelTest` tests=13 skipped=0 failures=0 errors=0;
`CrewMapperTest` tests=6 skipped=0 failures=0 errors=0. The two new toggle cases and two new
mapper cases all ran and passed.

## Decisions

- IO boundary stays in the datasource for `setBlindVoting` (repo only does the owner check, no
  `withContext`) — this matches the **existing** `renameCrew`/`deleteCrew` pattern in this module.
  The charter's "one withContext per public data-layer method" holds: there is exactly one, in the
  datasource.
- No new error leaf — reused `Authorization.NotOwner` / `Backend.*`.
- The switch reflects `state.crew.blindVoting` directly (single source of truth) rather than a
  mirrored state field; only a transient `isSavingBlindVoting` guard was added.

## Blockers / pending (user)

- **Deploy `firestore.rules`** before the owner toggle works against prod (until then the owner's
  `update` write is denied):
  `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`

## Suggested next

`w1-blind-voting-presentation` — consume `CrewBlindVotingPort` in the feed and apply
`BlindVotingPolicy.shouldMaskAuthor(...)` in the `FeedMealUi` mapping. See the handoff.
