# 021 · w1-remove-member-data

**Status:** done

**Summary (≤6 lines):**
- Real transactional Firestore write behind `CrewRepository.removeMember` (atomic `runTransaction` mirroring `leave`, with TOCTOU re-check); owner-only `firestore.rules` + rules test.
- Files: `feature/crew/.../data/firebase/{CrewDataSource,CrewFirestoreDataSource}.kt`, `.../data/repository/FirebaseCrewRepository.kt`, commonTest `FakeCrewDataSource`+`FirebaseCrewRepositoryTest`, `firestore.rules`, `firestore-tests/tests/crews.test.ts`.
- Decisions: removed member's meals KEPT (§1.5 default, no cascade/migration); member-removed notification SILENT — NOT built (§1.5 explicit default, no real §6.3 design).
- Blockers: none. MANUAL: deploy `firestore:rules`.
- NOTE: discovered a `firestore-tests/` rules-test harness (vitest) — usable for future rules work.

**Verify (quoted):**
```
./gradlew :feature:crew:testAndroidHostTest → BUILD SUCCESSFUL in 4s
cd firestore-tests && pnpm test → ✓ tests/crews.test.ts (10 tests) / Tests 33 passed (33)
```

**Presentation handoff:** success snackbar + mid-call row state + failure-path VM tests.

Report: `docs/session/reports/w1-remove-member-data.md` · Handoff: `docs/session/handoffs/w1-remove-member-data.md`
