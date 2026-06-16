# 002 · w0-account-deletion-data

**Status:** done

**Summary (≤6 lines):**
- Data/infra layer complete: `FirebaseAccountDeletionPort` (new) replaces deleted `StubAccountDeletionPort`; Koin binding swapped; cascade Cloud Function `deleteAccount` added + vitest.
- Files: `feature/auth/.../data/firebase/FirebaseAccountDeletionPort.kt`, `.../di/AuthModule.kt`, `feature/auth/build.gradle.kts`, `functions/src/callables/deleteAccount.ts`, `functions/src/index.ts`, `functions/__tests__/deleteAccount.test.ts`.
- Decisions: `votedMeals` scans caller's crews (map-key not queryable); `planCrewReassignment` extracted as exported pure helper; added `libs.firebase.functions` dep to `:feature:auth`; Admin SDK bypasses rules (no rules edit) — Firestore index flagged MANUAL.
- Blockers: none. User must DEPLOY functions before release.

**Verify (quoted):**
```
pnpm --dir functions test → Test Files 5 passed (5) / Tests 39 passed (39) / Duration 606ms
./gradlew :feature:auth:testAndroidHostTest → BUILD SUCCESSFUL in 5s
(pnpm --dir functions build → tsc EXIT=0)
```

Report: `docs/session/reports/w0-account-deletion-data.md` · Handoff: `docs/session/handoffs/w0-account-deletion-data.md`
