# 006 · w0-consent-settings-toggle

**Status:** done

**Summary (≤6 lines):**
- Analytics-consent revoke/re-grant `FrSwitch` row added to Profile, wired through `ProfileViewModel` over observed `ConsentPort.decision`. On → `grant()` + `ConsentGranted` event; off → `revoke()` (no event).
- Files: `feature/auth/.../presentation/profile/ProfileViewModel.kt`, `.../profile/ProfileScreen.kt`, `.../i18n/AuthStringKey.kt`, `.../di/AuthModule.kt`, `androidHostTest/.../di/AuthModuleVerifyTest.kt`, `commonTest/.../profile/ProfileViewModelTest.kt`, `composeResources/values{,-es}/strings.xml`.
- Decisions: off uses `revoke()` (settings opt-out verb); switch purely observed from `decision` (no optimistic write); reused `FrIcons.Stats`; `Account.dataConsentVersion` mirror out of scope (§0.2 bullet 2).
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:auth:testAndroidHostTest
BUILD SUCCESSFUL in 4s
(ProfileViewModelTest 4/4, AuthModuleVerifyTest 1/1 with ConsentPort in extraTypes)
```

Report: `docs/session/reports/w0-consent-settings-toggle.md`
