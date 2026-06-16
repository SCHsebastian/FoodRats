# 015 · w1-blind-voting-presentation

**Status:** done

**Summary (≤6 lines):**
- Feed masks meal author (name+avatar) when crew `blindVoting` is ON until viewer rates; reveals on vote, on own meal, or on window-close. Reuses `BlindVotingPolicy`; `CrewBlindVotingPort` injected into `FeedViewModel` (combined into feed flow, no parallel `MutableStateFlow`, no `withContext`).
- Files: `feature/feed/.../components/FeedMealUi.kt`, `.../components/FrFeedMealRow.kt`, `.../feed/FeedViewModel.kt`, `.../feed/FeedContract.kt`, `.../di/FeedModule.kt`, `.../i18n/FeedStringKey.kt`, `composeResources/values{,-es}/strings.xml`, `androidHostTest/.../di/FeedModuleVerifyTest.kt`, `commonTest` `FeedMealUiTest`+`FeedViewModelTest`, `androidHostTest` `FrFeedMealRowTest`.
- Decisions: window-close reveal at call site (`viewerHasVoted = viewer != null || !windowOpen`); masking in `toFeedUi`/`FrFeedMealRow` (feature layer); `FrAvatar` placeholder + i18n `feed_blind_author`; MealDetail intentionally un-masked (out of FEED scope).
- Blockers: none. MANUAL: deploy `firestore:rules` for the owner toggle write.

**Verify (quoted):**
```
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 8s
(module total tests=61, failures=0)
```

Report: `docs/session/reports/w1-blind-voting-presentation.md`
