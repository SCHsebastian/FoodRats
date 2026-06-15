# 022 · w1-remove-member-presentation

**Status:** done — **WAVE 1 COMPLETE**

**Summary (≤6 lines):**
- Remove-member UX polish: per-row in-progress spinner (`removingMemberIds`), `MemberRemoved` snackbar effect, mapped `CrewError.RemoveMember.*` failure banners, `crew_member_removed` analytics on Ok (crew_id only, no PII).
- Files: `core/domain/.../analytics/{AnalyticsEvent,AnalyticsTaxonomyTest}.kt`; `feature/crew/.../i18n/CrewStringKey.kt`; `composeResources/values{,-es}/strings.xml`; `.../presentation/settings/{CrewSettingsContract,CrewSettingsViewModel,CrewSettingsScreen,CrewSettingsViewModelTest}.kt`; `.../di/CrewModule.kt`.
- Decisions: per-row state single-source-of-truth; named VM binding with `analytics = get()`; member-removed push deliberately not built (§1.5 silent default).
- Blockers: none. MANUAL: deploy `firestore:rules`.

**Verify (quoted):**
```
./gradlew :feature:crew:testAndroidHostTest → BUILD SUCCESSFUL (CrewSettingsViewModelTest 17/0/0)
./gradlew :core:domain:testAndroidHostTest → BUILD SUCCESSFUL (AnalyticsTaxonomyTest 5/0/0)
```

Report: `docs/session/reports/w1-remove-member-presentation.md`
