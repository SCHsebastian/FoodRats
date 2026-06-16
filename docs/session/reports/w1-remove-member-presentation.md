# Report — `w1-remove-member-presentation`

Terminal task of remove-member and of Wave 1. Finished the crew member-removal UX polish on top
of the already-wired domain + data layers (affordance, `FrConfirmDialog` destructive variant,
`RemoveMemberUseCase`, `CrewError.RemoveMember.*` mapping all pre-existed).

## What this task added

### Per-row in-progress state (MVI single source of truth)
- `CrewSettingsState.removingMemberIds: Set<AccountId>` — members whose owner-initiated removal is
  in flight. The row stays in the list (the live crew flow drops it only once the write lands) but
  renders disabled + a small spinner while present in the set. No parallel `MutableStateFlow`.
- `CrewSettingsViewModel.doRemoveMember` now: captures the live display name before the write, adds
  the target to `removingMemberIds` (+ clears error), then on `Ok` tracks analytics, removes the
  target from `removingMemberIds`, and emits the success effect; on `Err` removes the target from
  `removingMemberIds` and surfaces `r.error` via the existing error banner.

### Success feedback (snackbar effect)
- New `CrewSettingsEffect.MemberRemoved(displayName: String?)` (`null` ⇒ deleted/unresolved account
  → screen substitutes the localized deleted-user fallback; i18n stays in the UI layer).
- `CrewSettingsScreen` passes a `SnackbarHostState` to `FrScreenScaffold` (which already supports
  `snackbarHostState`) and shows the templated success string. The snackbar message is resolved in
  composition (via a `memberRemovedName` state + keyed `LaunchedEffect`) — `resolve()` is
  `@Composable` and cannot be called inside the effect-collector coroutine.
- The members list trailing slot swaps the `FrIcons.Close` `FrIconButton` for a 2dp
  `FrProgressIndicator` (`Sizes.iconMd`) while the row is removing.

### Analytics (no PII)
- New `:core:domain` taxonomy leaf `AnalyticsEvent.CrewMemberRemoved(crewId)` →
  `crew_member_removed`, params `{crew_id}` only. Deliberately carries **no** account id / identity,
  so the event cannot single out the removed person.
- Tracked **only** in the ViewModel `Ok` branch (never in the use case). Registered in
  `AnalyticsTaxonomyTest.allEvents`.

### DI
- `crewModule`'s `CrewSettingsViewModel` `viewModel { (crewId) -> ... }` binding converted from 9
  positional `get()` calls to a fully named binding incl. `analytics = get()`, so the
  `analytics: AnalyticsPort = NoopAnalyticsTracker` default does not short-circuit graph resolution
  (per CLAUDE.md). `CrewModuleVerifyTest` already had `AnalyticsPort::class` in `extraTypes`.

### i18n
- New `crew_settings_member_removed` (`%1$s was removed from the crew.` /
  `Se quitó a %1$s del grupo.`) in both `values/` and `values-es/strings.xml`; `CrewStringKey`
  enum entry `SettingsMemberRemoved` + import.

### Tests (`CrewSettingsViewModelTest`, +4 → 17 total)
- `owner_remove_member_emits_success_effect_clears_progress_and_tracks_analytics` — port called,
  `MemberRemoved` effect emitted, `removingMemberIds` empty after, exactly one
  `CrewMemberRemoved` analytics event.
- `non_owner_remove_member_surfaces_not_owner_...` → `CrewError.RemoveMember.NotOwner`, port not
  called, nothing tracked.
- `owner_removing_self_..._cannot_remove_self_...` → `CrewError.RemoveMember.CannotRemoveSelf`.
- `owner_removing_non_member_..._member_not_found_...` → `CrewError.RemoveMember.MemberNotFound`.
- `buildVm` gained an injectable `RecordingAnalyticsTracker` (commonMain test double).
- Existing happy-path test (`owner_confirms_remove_member_...`) kept.

## Files changed

- `core/domain/.../analytics/AnalyticsEvent.kt` — `CrewMemberRemoved` leaf.
- `core/domain/.../analytics/AnalyticsTaxonomyTest.kt` — registered the leaf.
- `feature/crew/.../i18n/CrewStringKey.kt` — `SettingsMemberRemoved`.
- `feature/crew/.../composeResources/values/strings.xml`, `values-es/strings.xml` — new string.
- `feature/crew/.../presentation/settings/CrewSettingsContract.kt` — `removingMemberIds` state +
  `MemberRemoved` effect.
- `feature/crew/.../presentation/settings/CrewSettingsViewModel.kt` — analytics injection +
  full remove flow.
- `feature/crew/.../presentation/settings/CrewSettingsScreen.kt` — snackbar + per-row spinner.
- `feature/crew/.../di/CrewModule.kt` — named VM binding with `analytics = get()`.
- `feature/crew/.../presentation/settings/CrewSettingsViewModelTest.kt` — 4 new tests + `buildVm`.

## Decisions

- Effect carries `displayName: String?` (not a fully-resolved string) so i18n / the deleted-user
  fallback stays in the UI layer.
- Mid-call (`removingMemberIds` non-empty) is asserted only via the terminal-state contract; under
  `UnconfinedTestDispatcher` the fake repo completes synchronously so the transient set state isn't
  reliably observable. The transitions are correct by construction (set on entry, cleared on both
  branches).
- Analytics event carries crew id only — no removed-member identity — to honor the no-PII invariant.

## Verify

```
./gradlew :feature:crew:testAndroidHostTest
> Task :feature:crew:testAndroidHostTest
BUILD SUCCESSFUL in 1s
90 actionable tasks: 8 executed, 82 up-to-date
```
`CrewSettingsViewModelTest`: tests="17" skipped="0" failures="0" errors="0".

```
./gradlew :core:domain:testAndroidHostTest   (taxonomy leaf added)
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL
```
`AnalyticsTaxonomyTest`: tests="5" skipped="0" failures="0" errors="0".

## Blockers

None.

## MANUAL step the user must run before this ships (restated from the data handoff)

Deploy the owner-only remove-member Firestore rule branch — it is inert in prod until deployed and
the client write will fail on the old rules:

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```

## Not built (per roadmap §1.5 default = silent)

The member-removed push notification is intentionally NOT built (roadmap §1.5 default: silent).
If product reverses that later, see `docs/session/handoffs/w1-remove-member-data.md` for the
server + client follow-up.
