# :feature:crew

Bounded context: closed-group membership (3-15 members), invite codes, member
listing, owner-only rename/delete, the active-crew selector that gates the
rest of the app, and the sign-out entry point.

## Screens

- **`CrewPickerScreen`** (`presentation/picker/`) — lists the viewer's crews
  with a "create" and "join by code" CTA. Picking a crew sets the active crew
  in DataStore (via `SwitchActiveCrewUseCase`) and routes the user to Main.

- **`CrewSettingsScreen`** (`presentation/settings/`) — single screen for
  everything that lives "around" an active crew:
  - Rename the crew (owner only)
  - Copy the invite code to the clipboard
  - Edit your own member `displayName`
  - List members
  - Switch to a different crew (navigates back to `CrewPicker`)
  - **Leave** the crew (always available)
  - **Sign out** the entire account — this is the canonical sign-out surface
  - Delete the crew (danger zone, owner only, with confirm dialog)

  Reached from the gear icon on the Main top app bar in
  `:shared/.../NavGraph.kt`.

## Domain layer

- **`Crew` / `Member` / `CrewCode`** (`domain/model/`) — value objects.
  `CrewCode.of` validates 6-char alphanumeric codes.
- **`CrewRepository`** (`domain/repository/`) + use cases (`domain/usecase/`):
  `CreateCrewUseCase`, `JoinCrewByCodeUseCase`, `LeaveCrewUseCase`,
  `ObserveMyCrewsUseCase`, `ObserveCrewUseCase`, `SwitchActiveCrewUseCase`,
  `RenameCrewUseCase`, `RenameMemberUseCase`, `DeleteCrewUseCase`.
- **`CrewError`** (`domain/error/`) — sealed interface with `Validation`,
  `Authorization`, and read/write leaves. Exhaustively mapped in
  `presentation/CrewErrorToStringKey` (locked by `CrewErrorToStringKeyTest`).

## Data layer

- **`FirebaseCrewRepository`** (`data/repository/`) writes crews + members to
  Firestore and pairs with `firestore.rules` at the repo root for membership
  + rename + delete permission checks.
- **`ActiveCrewLocalStore`** (`data/local/`) — implements the
  `ActiveCrewProvider` port from `:core:domain` over DataStore.
- **`CrewCodeGenerator`** generates unique 6-char codes; collisions are
  surfaced as `CrewError.Collision` so the caller can retry.

## Sign-out flow

`CrewSettings` depends on `SignOutPort` from `:core:domain/session/` — a
narrow write-side interface deliberately separate from `:feature:auth` so
this module doesn't pull in the whole auth surface to end a session. The
adapter `AuthSignOutPort` in `:feature:auth` wraps `AuthRepository.signOut()`,
maps `AuthError → SessionError`, and clears both `Keys.SessionToken` AND
`Keys.ActiveCrewId` so the next sign-in lands cleanly on `CrewPicker`.

On the UI side: tapping "Cerrar sesión" dispatches
`CrewSettingsIntent.SignOut` → VM calls the port → emits
`CrewSettingsEffect.SignedOut`. The screen's `onSignedOut` callback only does
a defensive `popBackStack()` — the **real** auth-boundary navigation is
driven by `RootNavViewModel` observing `SessionProvider.current` going null,
which emits `NavigateTo(Route.SignIn)` → `navigateTopLevel` pops the entire
graph and lands on a single-entry `[SignIn]` stack. Back-pressing from
SignIn no longer leaks back to the post-auth stack.

## Active crew gate

The `ActiveCrewProvider` port (implemented here) is consumed app-wide:
`RootNavViewModel` combines it with `SessionProvider` to decide between
`NeedsSignIn`, `NeedsCrew`, and `Ready` stages. Every feature that needs to
know "which crew am I writing under?" reads this port — never feature-to-
feature dependencies. See `Meal` publish, Feed observe, Stats observe.

## i18n

`CrewStringKey` covers picker, create, join, the unified settings labels
(`SettingsTitle`, `SettingsCrewSection`, `SettingsMembersSection`,
`SettingsActionsSection`, `SettingsDangerSection`, ...), the sign-out CTA
and its failure banner, and the full `CrewError` tree. en + es are both
populated in `composeResources/values{,-es}/strings.xml`.
