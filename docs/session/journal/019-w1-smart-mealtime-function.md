# 019 · w1-smart-mealtime-function

**Status:** done

**Summary (≤6 lines):**
- `streakNudge` now gated by a smart-mealtime window: new pure `mealtimeProfile` module learns the crew's active posting window from the UTC posting-hour distribution (peak-grow to 75% coverage, midnight-wrapping; fallback 11–14 UTC below 5 samples). Hourly schedule kept; gate fires before reads, never burns dedupe out-of-window.
- Files: `functions/src/triggers/mealtimeProfile.ts` (new), `functions/src/triggers/streakNudge.ts`, `functions/__tests__/mealtimeProfile.test.ts` (new), `functions/__tests__/streakNudge.test.ts`.
- Decisions: NO timezone stored anywhere (verified Account/Crew/Meal DTOs) → window from UTC distribution; sends tagged `windowSource` for A/B (§1.4).
- Blockers: none. True-local mealtime needs a stored timezone → flagged as optional client tz-capture follow-up (handoff written).

**Verify (quoted):**
```
pnpm --dir functions build → tsc (no errors)
pnpm --dir functions test → Test Files 8 passed (8) / Tests 80 passed (80) (mealtimeProfile 13, streakNudge 18, weeklyDigest 7)
```

**Deferred enhancement (optional, codeable later, NOT required for §1.4):** capture device timezone (`TimeZone.currentSystemDefault()`) on FCM token registration → true-local nudge timing. See handoff.

Report: `docs/session/reports/w1-smart-mealtime-function.md` · Handoff: `docs/session/handoffs/w1-smart-mealtime-function.md`
