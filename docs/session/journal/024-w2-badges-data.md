# 024 · w2-badges-data

**Status:** done

**Summary (≤6 lines):**
- Achievements data layer: `AchievementProgressPort`+error in `:core:domain`; `:feature:achievements` bumped to JVM 17 + Firebase BOM; `AchievementUnlockDto` + datasource + `FirebaseAchievementRepository` (thin, one `withContext`/method); pure `AchievementReconciler` overlays persisted unlock timestamps; `AchievementsModule` registered in shared appModules.
- Files: `core/domain/.../achievement/{AchievementProgressPort,AchievementProgressError}.kt`; `feature/achievements/build.gradle.kts`, `.../data/firebase/*`, `.../data/repository/FirebaseAchievementRepository.kt`, `.../domain/AchievementReconciler.kt`, `.../di/AchievementsModule.kt`; `shared/.../app/di/AppModule.kt`, `shared/build.gradle.kts`; `firestore.rules` + `firestore-tests/tests/achievements.test.ts`; tests.
- Decisions: reconcile/overlay is pure `AchievementReconciler` (ViewModel wires evaluate→reconcile→recordUnlocks); `AchievementUnlockStore` seam for host-testability; owner-only read+write rule (client-derived earning).
- Blockers: none. MANUAL: deploy `firestore:rules` (else persistence PERMISSION_DENIED).

**Verify (quoted):**
```
:core:domain:testAndroidHostTest → BUILD SUCCESSFUL in 17s (Konsist passes)
:feature:achievements:testAndroidHostTest → BUILD SUCCESSFUL (DTO 4/4, Repo 7/7, Reconciler 4/4, ModuleVerify 1/1)
firestore-tests pnpm test → Test Files 5 passed (5) / Tests 38 passed (38)
```

**Presentation handoff:** use case + reactive ViewModel calling evaluate→reconcile→recordUnlocks; FrBadge; analytics leaf; "newly unlocked" detection for celebration.

Report: `docs/session/reports/w2-badges-data.md` · Handoff: `docs/session/handoffs/w2-badges-data.md`
