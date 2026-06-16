# 017 · w1-reactions-data

**Status:** done

**Summary (≤6 lines):**
- Reactions data/infra: `reactions/{uid}` subcollection under each meal + `MealReactionPort` impl (observe aggregate + toggle Added/Removed). `ReactionDto`/mapper + testable `ReactionFirestore` seam mirroring `MealFirestore`.
- Files: NEW `feature/meal/.../data/firebase/{ReactionDto,ReactionMapper,ReactionFirestore,ReactionFirestoreDataSource}.kt`, `.../data/repository/FirebaseReactionRepository.kt`, commonTest `ReactionMapperTest`+`FirebaseReactionRepositoryTest`. EDITED `feature/meal/.../di/MealModule.kt`, `feature/feed/.../di/FeedModuleVerifyTest.kt`, `firestore.rules`.
- Decisions: impl placed in `:feature:meal` (NOT `:feature:feed` per handoff) — feed module is Firebase-free; matches comment/rating precedent; feed consumes the `:core:domain` port. No new `ReactionError` leaf (6 existing covered all faults).
- Blockers: none. MANUAL: deploy `firestore:rules`.

**Verify (quoted):**
```
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 6s
(meal also green; new tests 5/5 + 6/6; rules dry-run OK)
```

**Presentation handoff:** port methods + aggregate shape (counts + viewer's reaction); `meal_reacted` analytics on `Ok(Added)`; rules deploy.

Report: `docs/session/reports/w1-reactions-data.md` · Handoff: `docs/session/handoffs/w1-reactions-data.md`
