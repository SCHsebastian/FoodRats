# 005 · w0-consent-ui-presentation

**Status:** done

**Summary (≤6 lines):**
- First-run consent screen + root-nav gate + `ConsentViewModel` + Koin + en/es i18n. Gate shows consent as last step before Ready when `needsDecision`; a settled current-version Deny does NOT re-prompt; login-flash & deep-link-resume not regressed.
- Files: `shared/.../app/consent/{ConsentContract,ConsentViewModel,ConsentScreen}.kt` (new); `shared/.../app/root/{RootNavContract,RootNavViewModel}.kt`; `shared/.../app/navigation/{Route,NavGraph}.kt`; `shared/.../app/di/AppModule.kt`; `shared/.../app/i18n/SharedStringKey.kt`; `composeResources/values{,-es}/strings.xml`; tests `RootNavViewModelTest`, `RouteAccessTest`, `ConsentViewModelTest`.
- Decisions: home = `:shared` (root-nav onboarding gate); consent = last gate before Ready; Deny records nothing; `Account.dataConsentVersion` stamp NOT wired (handoff forbade new port surface; ConsentPort persists durably).
- Blockers: none.

**Verify (quoted):**
```
> Task :shared:testAndroidHostTest
BUILD SUCCESSFUL in 5s
(31 tests, 0 fail; ConsentVM 3/3, RootNav 7/7; :androidApp:assembleDebug also green)
```

**Follow-up tracked:** added `w0-consent-settings-toggle` (revoke/re-grant in Profile, §0.2 bullet 3).
**MANUAL (user):** Play Data-Safety form; iOS `PrivacyInfo.xcprivacy` (`NSPrivacyTracking=false`); manifest/plist collection-disabled defaults.

Report: `docs/session/reports/w0-consent-ui-presentation.md`
