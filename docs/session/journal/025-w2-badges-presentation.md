# 025 · w2-badges-presentation

**Status:** done

**Summary (≤6 lines):**
- Achievements UI end-to-end: `FrBadge` atom (+catalog entry `atom.badge`), `ObserveAchievementsUseCase` (evaluate→reconcile→recordUnlocks), `AchievementsViewModel` with effect-driven unlock celebration + `achievement_unlocked` analytics (Ok-branch only), achievements Route + Profile "Badges" entry, i18n en/es.
- Files: `core/domain/.../analytics/AnalyticsEvent.kt` (+`AchievementUnlocked`) +taxonomy test; `core/designsystem/.../atoms/FrBadge.kt` (new) + `FrIcons.kt` (+6 glyphs) + `FrBadgeTest`; `catalogApp/.../stories/AtomStories.kt`; `feature/achievements/.../domain/{AchievementSignalsBuilder,usecase/ObserveAchievementsUseCase}.kt`, `.../presentation/*`, `.../i18n/*` + strings, `.../di/AchievementsModule.kt`, tests; `shared/.../navigation/{Route,NavGraph}.kt`; `feature/auth/.../profile/ProfileScreen.kt` + auth strings; `docs/analytics/TRACKING_PLAN.md`.
- Decisions: entry = Profile "Badges" row; celebration = `FrConfirmDialog` effect (swappable); ISO `yyyy-MM-dd` earned-on (no CLDR months in commonMain); persist+track in ViewModel `persistAndCelebrate` (use case stays pure).
- Blockers: none. MANUAL: deploy `firestore:rules` (else PERMISSION_DENIED on the screen).

**Verify (quoted):**
```
:feature:achievements:testAndroidHostTest → BUILD SUCCESSFUL (47 tests)
:core:designsystem:testAndroidHostTest → BUILD SUCCESSFUL (48, incl FrBadgeTest)
:core:domain:testAndroidHostTest → BUILD SUCCESSFUL (98, incl AnalyticsTaxonomyTest)
:shared:testAndroidHostTest → BUILD SUCCESSFUL (31)
:androidApp:assembleDebug → BUILD SUCCESSFUL in 58s; :catalogApp:assembleDebug → BUILD SUCCESSFUL
```

**Handoff:** cuisine-passport / bingo / weekly-digest can reuse `FrBadge` + `AchievementStatus`.

Report: `docs/session/reports/w2-badges-presentation.md` · Handoff: `docs/session/handoffs/w2-badges-presentation.md`
