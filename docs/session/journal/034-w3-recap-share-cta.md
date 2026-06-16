# 034 · w3-recap-share-cta

**Status:** done

**Summary (≤6 lines):**
- "Share this recap" CTA on the weekly recap story. Added an `action` overlay slot to `FrStoryScaffold` (drawn above tap-zones so the button wins the click; default null → existing callers unchanged); recap scene → `Fr*ShareCard` props → `StoryShareController` render+launch; `RecapShared(sceneKind)` analytics.
- Files: `core/designsystem/.../atoms/FrStoryScaffold.kt` (+test, +catalog); `core/domain/.../analytics/AnalyticsEvent.kt` (+`RecapShared`, +taxonomy test); `shared/.../app/recap/{RecapShareCard(new),WeeklyStoryViewModel,WeeklyStoryContract,WeeklyStoryScreen,WeeklyStoryModule}.kt`, `app/i18n/SharedStringKey.kt`, tests, strings; `docs/analytics/TRACKING_PLAN.md`.
- Decisions: `RecapShared` (content_type=recap, item_id=scene-kind slug) not `PlateShared` — recap TopMeal has no MealId, keeps it PII-free; shareable scenes = top-meal/streak/your-week.
- Blockers: none. MANUAL: on-device share smoke now covers all 4 surfaces incl. recap (human.md).

**Verify (quoted):**
```
:core:domain + :core:designsystem testAndroidHostTest → BUILD SUCCESSFUL in 6s (FrStoryScaffoldTest 3/3, AnalyticsTaxonomyTest 6/6)
:shared:testAndroidHostTest → BUILD SUCCESSFUL in 16s (WeeklyStoryViewModelTest 13/13)
:catalogApp + :androidApp assembleDebug → BUILD SUCCESSFUL; :shared iOS compile → BUILD SUCCESSFUL
```

Report: `docs/session/reports/w3-recap-share-cta.md`
