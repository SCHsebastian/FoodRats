# 045 · w5-offline-compose-presentation

**Status:** done

**Summary (≤6 lines):**
- Feed top-bar upload-queue indicator (pending/failed counts) + retry/dismiss affordances; idempotency de-dup so a published queued draft renders once.
- Files: NEW `core/domain/.../meal/QueuedUploadActionsPort.kt`, `feature/feed/.../presentation/components/FrUploadQueueBar.kt`; edited `BackgroundMealUploadCoordinator.kt`, meal `MealModule.kt`, feed `FeedContract/FeedViewModel/FeedScreen/FeedStringKey/FeedModule/FeedModuleVerifyTest/FeedViewModelTest` + `values{,-es}/strings.xml`.
- Decisions: new `:core:domain` write port (retry/dismiss only terminal `Failed(retryable=false)`); aggregate count only (no per-entry sheet → avoids feed→meal dep); de-dup by `MealId` via `distinctBy`; no analytics event (§5.2 doesn't call for one); `FrUploadQueueBar` feature-local (not catalog).
- Blockers: none. MANUAL: real offline→reconnect→publish on-device smoke (human.md).

**Verify (quoted):**
```
:feature:feed:testAndroidHostTest → BUILD SUCCESSFUL in 7s
:feature:meal + :core:domain testAndroidHostTest → BUILD SUCCESSFUL in 17s
:androidApp:assembleDebug → BUILD SUCCESSFUL in 3s
```

Report: `docs/session/reports/w5-offline-compose-presentation.md`
