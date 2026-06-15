# 016 · w1-reactions-domain

**Status:** done

**Summary (≤6 lines):**
- Domain layer for lightweight meal reactions (§1.3). Reaction = the day's `DailyEmote` only (glyph derived at render, not persisted), modeled as sealed `ReactionKind` w/ single `DailyGlyph` leaf (door open for a future fixed set). One-per-member (doc=uid), toggle returns `Added`/`Removed`, no push, separate from Score.
- Files: `core/domain/.../meal/{ReactionKind,MealReaction,MealReactionPort}.kt` (new), `core/domain/commonTest/.../meal/{MealReactionTest,ReactionKindTest}.kt`.
- Decisions: one bundled `MealReactionPort` (observe + toggle); `MealReactions` aggregate defends one-per-member on read; no domain use case.
- Blockers: none.

**Verify (quoted):**
```
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s
(Konsist green; ReactionKindTest 5/5, MealReactionTest 5/5)
```

**Data handoff:** Firestore `reactions/{uid}` subcollection + port impl in `:feature:feed` + firestore.rules. Presentation later adds `meal_reacted` analytics leaf.

Report: `docs/session/reports/w1-reactions-domain.md` · Handoff: `docs/session/handoffs/w1-reactions-domain.md`
