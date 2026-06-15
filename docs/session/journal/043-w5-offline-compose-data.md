# 043 · w5-offline-compose-data

**Status:** done (own scope green) — surfaced a separate red build to fix next

**Summary (≤6 lines):**
- Durable DataStore-backed `DraftQueuePort`, connectivity-gated retry runner (idempotent re-publish via deterministic `MealId`), queued/failed counts on `MealUploadProgressPort` (via sibling `MealUploadQueueSnapshot` + defaulted port member). Per-platform `ConnectivityMonitor` + WorkManager `NetworkType.CONNECTED`. Koin + worker wired.
- Files: `core/domain/.../meal/{MealUploadQueueSnapshot(new),MealUploadProgressPort}.kt`, `core/data/.../datastore/Keys.kt`, `feature/meal/.../data/queue/*` (new + android/ios actuals), `.../data/upload/{BackgroundMealUploadCoordinator,MealUploadWorker}.kt`, `.../di/{MealModule,MealAndroidModule,MealIosModule}.kt` + verify, commonTest queue tests.
- Decisions: DataStore JSON (no SQLDelight in repo); durable image = base64 blob; converged with existing coordinator (deterministic-id idempotency makes both safe).
- Blockers: **`:feature:stats:compileAndroidHostTest` RED** — 15 `Meal()` fixture mismatches from an earlier task (independent of this change; agent verified by reverting). iOS background best-effort. MANUAL on-device offline check in human.md.

**Verify (quoted):**
```
:feature:meal:testAndroidHostTest → 150 tests completed · BUILD SUCCESSFUL
:core:domain + :feature:feed testAndroidHostTest → BUILD SUCCESSFUL in 5s
:androidApp:assembleDebug → BUILD SUCCESSFUL; :shared iOS compile → BUILD SUCCESSFUL
```

**Next:** dispatch a fix for the `:feature:stats` `Meal()` fixture breakage (tracked `w5-fix-stats-meal-fixtures`).

Report: `docs/session/reports/w5-offline-compose-data.md` · Handoff: `docs/session/handoffs/w5-offline-compose-data.md`
