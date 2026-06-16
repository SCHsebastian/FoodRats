# 020 · w1-remove-member-domain

**Status:** done

**Summary (≤6 lines):**
- Removed the `NotImplemented.RemoveMember` stub; `RemoveMemberUseCase` now enforces owner-only / not-self / member-exists and delegates to `CrewRepository.removeMember(crewId, requestedBy, target)`.
- New errors: `CrewError.RemoveMember.{NotOwner,CannotRemoveSelf,MemberNotFound}` (backend reuses `Backend.*`); exhaustive mapper + i18n updated.
- Files: `feature/crew/.../domain/error/CrewError.kt`, `.../domain/repository/CrewRepository.kt`, `.../domain/usecase/RemoveMemberUseCase.kt`, `.../presentation/CrewErrorToStringKey.kt`, `.../presentation/settings/CrewSettingsViewModel.kt`, `.../i18n/CrewStringKey.kt`, `.../data/repository/FirebaseCrewRepository.kt` (checks + TODO), `composeResources/values{,-es}/strings.xml`, tests + `FakeCrewRepository`.
- Decisions: write via `CrewRepository.removeMember` (not a new port — consistent w/ existing crew writes); use case resolves requester from session; `FirebaseCrewRepository.removeMember` has checks but returns `Backend.Unavailable` behind a `TODO(scope=w1-remove-member-data)`.
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:crew:testAndroidHostTest
BUILD SUCCESSFUL in 3s
(RemoveMemberUseCaseTest 4/4, CrewErrorToStringKeyTest 20/20, CrewSettingsViewModelTest 13/13)
```

**Data handoff:** swap the TODO for the transactional Firestore write + datasource method; owner-only/can't-remove-self `firestore.rules` + rule test; "keep removed member's meals" default; member-removed push decision (§6.3 vs §1.5).

Report: `docs/session/reports/w1-remove-member-domain.md` · Handoff: `docs/session/handoffs/w1-remove-member-domain.md`
