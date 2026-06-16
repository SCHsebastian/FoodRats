# 018 · w1-reactions-presentation

**Status:** done

**Summary (≤6 lines):**
- Reactions feed UI: per-meal daily-emote react button (selected/unselected + count) in `FrFeedMealRow`, `ReactMeal` intent toggling via `MealReactionPort`, `meal_reacted` analytics on `Ok(Added)` only.
- Files: `core/domain/.../analytics/{AnalyticsEvent,AnalyticsTaxonomyTest}.kt`; `feature/feed/.../presentation/feed/{FeedViewModel,FeedContract,FeedScreen}.kt`, `components/{FeedMealUi,FrFeedMealRow}.kt`, `FeedErrorToStringKey.kt`, `i18n/FeedStringKey.kt`, `composeResources/values{,-es}/strings.xml`, `di/FeedModule.kt`; tests `FeedViewModelTest`, `FeedMealUiTest`, `ReactionErrorToStringKeyTest`, `FrFeedMealRowTest`.
- Decisions: glyph = existing `dayEmote` (render-time); "who reacted" = count (not name list); feature-local `ReactionButton` (no designsystem atom/catalog); no optimistic UI (live `observe()` re-emits); per-meal multiplexed via deduped+combined flows like `MealDetailViewModel`.
- Blockers: none. MANUAL: deploy `firestore:rules`.

**Verify (quoted):**
```
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 5s
(FeedViewModelTest 14, FeedMealUiTest 15, FrFeedMealRowTest 7; :core:domain AnalyticsTaxonomyTest 5/5; :androidApp:assembleDebug green)
```

Report: `docs/session/reports/w1-reactions-presentation.md`
