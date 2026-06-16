# 030 · w2-weekly-digest-story-presentation

**Status:** done — **WAVE 2 COMPLETE**

**Summary (≤6 lines):**
- Swipeable Instagram-style weekly recap story: `FrStoryProgressBar`/`FrStoryScaffold` DS atoms (+catalog), `WeeklyRecap` assembled PURELY from existing stats+achievements read paths (no recompute, behind a `WeeklyRecapStream` seam), `WeeklyStoryViewModel` + scenes in `shared` (only place both use cases co-exist), deep link `…/digest/{weekStart}`, in-app "See your week" Stats button, story analytics leaves.
- Files: `core/designsystem/.../atoms/{FrStoryProgressBar,FrStoryScaffold}.kt` (+test, +catalog); `core/domain/.../analytics/{AnalyticsEvent,AnalyticsDimensions}.kt` (+test); `shared/.../app/recap/*` + `navigation/{Route,DeepLink,NavGraph}.kt` + `di/AppModule.kt` + `i18n/SharedStringKey.kt` + strings + `build.gradle.kts`; `feature/stats/.../StatsScreen.kt` + strings; `functions/src/{fcm/push.ts,triggers/weeklyDigest.ts}` + test; `docs/analytics/TRACKING_PLAN.md`.
- Decisions: player/scenes/VM/Route in `shared` (features can't cross-depend); empty scenes skipped (cover + your-week always shown); `weekStart` informational.
- Blockers: none. MANUAL: deploy `functions:weeklyDigest` so the digest push carries the recap link.

**Verify (quoted):**
```
:feature:stats + :shared + :core:designsystem + :core:domain testAndroidHostTest → BUILD SUCCESSFUL (WeeklyStoryVM 7/7, Assembler 4/4, DeepLinkParser 12/12, FrStoryProgressBar 3/3)
pnpm --dir functions test → Test Files 9 passed (9) / Tests 90 passed (90)
:androidApp + :catalogApp assembleDebug + :shared iOS compile → all BUILD SUCCESSFUL
```

**Wave 3 handoff:** scene composables + recap model reusable as share-card content.

Report: `docs/session/reports/w2-weekly-digest-story-presentation.md` · Handoff: `docs/session/handoffs/w2-weekly-digest-story-presentation.md`
