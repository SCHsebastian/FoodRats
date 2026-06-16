# 041 · w5-image-pipeline-presentation

**Status:** done

**Summary (≤6 lines):**
- Client image pipeline: hand-ported pure-Kotlin ThumbHash decoder → `ThumbHashPainter` placeholder behind `AsyncImage`; feed loads the thumbnail, detail loads the full image; on-device compression (1600px/q80) before upload; Coil cache tuning.
- Files: `core/domain/.../meal/Meal.kt` (+thumbHash/thumbnailPath); `core/designsystem/.../image/{ThumbHash,ThumbHashPainter}.kt` + android/ios actuals + test; `core/data/.../image/ImageLoaderSetup.kt`; `feature/meal/.../data/firebase/{MealDto,MealMapper,PlateStorageDataSource,PlateCompressor}.kt` + actuals + DI + repo; `feature/feed/.../components/{FeedMealUi,FrFeedMealRow}.kt` + `detail/MealDetailScreen.kt`; tests; human.md.
- Decisions: hand-ported ThumbHash decoder (no KMP lib; verified vs reference, fixed 2 port bugs); compression inside `publish`'s IO boundary; client never mints `thumbnailPath` (server-owned); `PlateStorageDataSource` made `internal` + explicit `single{}`.
- Blockers: none. MANUAL: on-device placeholder + compression check (human.md §E).

**Verify (quoted):**
```
:feature:feed:testAndroidHostTest :feature:meal:testAndroidHostTest → BUILD SUCCESSFUL (feed 81, meal 122, 0 fail)
:core:designsystem:testAndroidHostTest → BUILD SUCCESSFUL (73, incl 6 ThumbHash)
:core:domain + :androidApp:assembleDebug + :shared iOS compile → all BUILD SUCCESSFUL
```

Report: `docs/session/reports/w5-image-pipeline-presentation.md`
