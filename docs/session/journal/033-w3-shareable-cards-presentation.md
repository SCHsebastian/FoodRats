# 033 · w3-shareable-cards-presentation

**Status:** done

**Summary (≤6 lines):**
- Share entry points wired: feed/meal-detail (plate) + stats (award/streak), domain→`Fr*ShareCard` props mappers, `ShareCardStringKey` (en/es) in `:core:i18n`, `share` analytics leaves. Introduced `StoryShareController` adapter (renderer/launcher are un-fakeable `expect class`) so VMs are testable.
- Files: `core/domain/.../analytics/AnalyticsEvent.kt` (+test); `core/i18n/.../ShareCardStringKey.kt` + strings; `core/designsystem/.../atoms/FrIcons.kt`; `core/data/.../share/{StoryShareController,RecordingStoryShareController}.kt`; `androidApp/.../FoodRatsApplication.kt`; `core/data/iosMain/.../di/ShareIosModule.kt`; feed + stats presentation/mappers/DI/strings/tests.
- Decisions: card chrome resolved inside the off-screen `@Composable` lambda (no `withContext` in VMs); shared `ShareCardStringKey` in `:core:i18n`; followed spec §8 (no achievements/meal-extra entries).
- Blockers: none. Recap-story-share CTA DEFERRED (needs a DS overlay slot on `FrStoryScaffold`) → tracked as `w3-recap-share-cta`.
- MANUAL: on-device IG-Stories share smoke + Xcode `StoryShareBridge.swift` (in human.md).

**Verify (quoted):**
```
:feature:feed + :feature:stats + :core:domain + :core:designsystem testAndroidHostTest → BUILD SUCCESSFUL in 16s (MealDetailShareTest 4/4, StatsShareMappersTest 3/3, StatsViewModelTest 60/60, AnalyticsTaxonomyTest green)
:androidApp:assembleDebug → BUILD SUCCESSFUL in 7s
:core:data:compileIosMainKotlinMetadata → BUILD SUCCESSFUL in 6s
```

Report: `docs/session/reports/w3-shareable-cards-presentation.md`
