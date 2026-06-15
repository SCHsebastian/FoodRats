# 040 · w5-thumb-cleanup-onmealdeleted

**Status:** done

**Summary (≤6 lines):**
- `onMealDeleted` now reclaims the `_thumb.jpg` thumbnail alongside the full plate, best-effort (missing thumb does not fail deletion). Refactored storage-delete into pure `reclaimMealObjects`/`mealStoragePaths` helpers.
- Files: `functions/src/triggers/onMealDeleted.ts`, `functions/__tests__/onMealDeleted.test.ts` (new, 9 — none existed before).
- Decisions: prefer `thumbnailPath` from doc, else derive `crews/{crewId}/meals/{mealId}_thumb.jpg`; same `ignoreNotFound` + per-object try/catch as the existing plate reclaim.
- Blockers: none. No new human.md step (ships with the existing "Deploy Cloud Functions").

**Verify (quoted):**
```
pnpm --dir functions test → Test Files 11 passed (11) / Tests 113 passed (113)
pnpm --dir functions build → tsc clean exit, zero TS errors
```

Report: `docs/session/reports/w5-thumb-cleanup-onmealdeleted.md`
