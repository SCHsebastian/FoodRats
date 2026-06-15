# 001 · w0-account-deletion-domain

**Status:** done

**Summary (≤6 lines):**
- Domain layer for account deletion complete; both verify tasks green.
- Files: `core/domain/.../account/AccountDeletionPort.kt`, `core/domain/.../analytics/AnalyticsEvent.kt` (+ `AnalyticsTaxonomyTest`), `feature/auth/.../domain/error/ProfileError.kt`, `feature/auth/.../presentation/profile/ProfileErrorToStringKey.kt` (+ commonTest).
- Decisions: dropped `Ownership.OwnerOfActiveCrew`; added `Deletion.OwnerReassignFailed`; port KDoc = synchronous cascade, no marker; added `AccountDeleted` analytics leaf. Updated `ProfileErrorToStringKey` mapper+test only because the exhaustive `when` blocked compilation.
- Left for later tasks: `StubAccountDeletionPort`, Firebase adapter, i18n copy, `ProfileViewModel` wiring (data + presentation).
- Blockers: none (owned-crew fate fully specified in spec §6).

**Verify (quoted):**
```
> Task :feature:auth:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s
```

Report: `docs/session/reports/w0-account-deletion-domain.md` · Handoff: `docs/session/handoffs/w0-account-deletion-domain.md`
