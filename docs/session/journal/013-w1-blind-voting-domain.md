# 013 · w1-blind-voting-domain

**Status:** done

**Summary (≤6 lines):**
- Domain layer for blind voting: `Crew.blindVoting: Boolean = false`, new `CrewBlindVotingPort.observeBlindVoting(crewId): Flow<Boolean>` (mirrors `CrewOwnerPort`, emits false on unknown), pure `BlindVotingPolicy.shouldMaskAuthor(blindVoting, isAuthor, viewerHasVoted)`.
- **KEY SPEC FINDING:** blind voting masks the **author identity** (name/avatar) until the viewer has voted — NOT the score. (rate the food, not the person)
- Files: `feature/crew/.../domain/model/Crew.kt` (+`CrewTest`), `core/domain/.../crew/CrewBlindVotingPort.kt` (new), `core/domain/.../crew/BlindVotingPolicy.kt` (new, +test).
- Decisions: single flag (no `CrewSettings` VO); author always masked-or-not per `isAuthor`.
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:crew:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s   (incl. Konsist; +7 tests)
```

Report: `docs/session/reports/w1-blind-voting-domain.md` · Handoff: `docs/session/handoffs/w1-blind-voting-domain.md`
