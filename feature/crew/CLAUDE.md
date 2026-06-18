# :feature:crew

Closed-group membership bounded context (3–8 members): create / join-by-code / leave, invite codes, member list, owner-only rename / delete, active-crew selector, sign-out entry point.

## Authoritative references

- Spec — `docs/specs/2026-05-16-foodrats-ddd-kmp-design.md` §2.1 (Crew bounded context), §3 (module layout), §6.3 (remove-member design — fully implemented).
- Module README — `feature/crew/README.md` (screens + use-case inventory).
- Root `CLAUDE.md` — "Module graph", "Architectural rules" (cross-context reads via ports — others consume `ActiveCrewProvider` from `:core:domain`).

## Local rules

- The active-crew DataStore key is set via `SwitchActiveCrewUseCase`; downstream features observe `ActiveCrewProvider`.
- `CrewSettingsScreen` is the canonical sign-out surface for the whole app (reached via the gear icon on the Main top app bar).
- JVM target **17** (Firebase BOM).

## Test

`./gradlew :feature:crew:testAndroidHostTest`.
