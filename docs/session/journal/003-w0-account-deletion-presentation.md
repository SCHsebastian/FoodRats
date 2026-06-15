# 003 · w0-account-deletion-presentation

**Status:** done

**Summary (≤6 lines):**
- Presentation layer wired: `ProfileViewModel` Ok-branch teardown (track `AccountDeleted` → `setUserId(null)` → `resetData()` → sign-out), explicit Koin `viewModel{}` binding with `analytics = get()`, ownership i18n copy, new `ProfileViewModelTest`.
- Files: `feature/auth/.../presentation/profile/ProfileViewModel.kt`, `.../di/AuthModule.kt`, `.../composeResources/values{,-es}/strings.xml`, `.../commonTest/.../profile/ProfileViewModelTest.kt` (new).
- Decisions: UI/use-case/mapper/event/error-tree already existed — only teardown + analytics inject + i18n + VM test were missing. `signOut` Result intentionally not surfaced (local cleanup, UI torn down).
- Blockers: none. Terminal task of the trio — no handoff.

**Verify (quoted):**
```
> Task :feature:auth:testAndroidHostTest
BUILD SUCCESSFUL in 2s
(ProfileViewModelTest 2/2, AuthModuleVerifyTest 1/1, 0 failures)
```

**MANUAL (user, before release):** deploy functions; create the flagged Firestore index; store data-safety declarations. Optional follow-up: delete dead `NotImplemented` leaf + add CLAUDE.md "Account deletion" decision entry post-deploy.

Report: `docs/session/reports/w0-account-deletion-presentation.md`
