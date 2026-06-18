# Full-app review, optimize, repair & clean — 2026-06-17

Goal (user, effort=max, autonomous): use multi-agent workflows to review the WHOLE app,
fan out haiku/sonnet/opus agents to find what's wrong, then repair/optimize/clean.
Save context → everything durable lives in files under this dir.

## Constraints discovered
- Branch `develop`, in sync with origin (0/0).
- **In-flight dirty tree**: "minotaur mode" feature — 26 modified + 3 untracked (see `DIRTY_FILES.txt`).
  Repairs MUST NOT clobber those files. Review them read-only; only fix genuine independent bugs.
- Scope: 18 modules, 843 Kotlin + 28 TS files. `:feature:achievements` (44) + `:baselineprofile` (2) are new since CLAUDE.md.
- Do NOT run parallel `./gradlew` (daemon/config-cache contention). Verification builds run serially in the main thread.

## Plan (workflows in sequence; main thread stays in the loop between them)
1. **REVIEW (wf_2eedf3cb-b6b, running)** — Scan(haiku greppable ×5) ∥ Review(sonnet ×16 modules) → Verify(opus HIGH / sonnet MED, per finding) → Synthesize(opus).
   Outputs: `scan/*.md`, `findings/<module>.md`, `FINDINGS.md`, `REPAIR_PLAN.md`, `repair-queue.json`.
2. **REPAIR** — one agent per module (disjoint files), applies only confirmed safeToAutoFix fixes from `repair-queue.json`; HIGH correctness/security I review by hand. Skips DIRTY_FILES.
3. **VERIFY** — serial: host-test suite + assembleDebug + iOS framework link + functions tsc/vitest. Quote outputs. Fix breakage.

## Review result (wf_2eedf3cb-b6b, 70 agents, ~4M tok)
26 confirmed (0 HIGH survived verify, 13 MED, 13 LOW), 3 refuted (ds-04, crew-02, feed-01).
Outputs: FINDINGS.md, REPAIR_PLAN.md, repair-queue.json, findings/*.md, scan/*.md.

## Pre-repair verification (decisive)
- `FirebaseUnavailable` rename → only SessionProvider.kt + AuthSignOutPort.kt. Safe split.
- `onScrim`/`onCelebration` exist in FrSemanticColors → ds-01/ds-03/stats-03 valid.
- **cpi-03 DROPPED**: StringKey implemented across 11+ modules; `sealed interface` needs same-module impls → would BREAK build. Reviewer/verifier miss.
- Dead-leaf removal (core-domain-02+auth-04) spans AccountDeletionPort.kt + 6 auth files+tests → all to auth agent.

## Repair routing (16 module agents, disjoint files; opus on E/G/H/I)
APPLY ~30 fixes. DEFER/surface (risky/cross-cutting/design): cpi-02 EventsEffect move, core-data-03 consent no-op,
crew-01/03/04/05, meal-02/06, mealai-03, notif-01/04, ingredient-04, app-shell-01/03, functions-05, core-data-04, ds-02, core-domain-04.
SKIP (dirty/minotaur): app-shell-02 MainViewController, catalog-01 AtomStories, all dirty crew data files.

## Status
- [x] Scout + session scaffold
- [x] Workflow 1 (review)
- [x] Inspect synthesis + repair-queue + pre-repair verification
- [ ] Workflow 2 (repair) — LAUNCHING
- [ ] Verification builds/tests (serial, main thread)
- [ ] Report to user (incl. deferred list)
