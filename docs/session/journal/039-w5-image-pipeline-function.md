# 039 · w5-image-pipeline-function

**Status:** done

**Summary (≤6 lines):**
- Storage `onObjectFinalized` trigger `onPlateImageFinalized`: computes a ThumbHash + 512px thumbnail on plate upload, writes `thumbHash`+`thumbnailPath` to the meal doc via Admin SDK. Loop-safe (metadata marker + `*_thumb.jpg` name guard), idempotent, missing-meal no-op.
- Files: `functions/src/triggers/onPlateImageFinalized.ts` (new), `functions/__tests__/onPlateImageFinalized.test.ts` (new, 14), `functions/src/index.ts`, `functions/package.json`+lock (+`sharp@0.35.1`, `thumbhash@0.1.1`); human.md.
- Decisions: ThumbHash over BlurHash (smaller, param-free decode); thumbnail `crews/{crewId}/meals/{mealId}_thumb.jpg` @512/q75 (already authorized by `mintPlateUrls`); NO rules change (clients read the doc; `allow update` forbids clients writing these fields).
- Blockers: none. Follow-up flagged: extend `onMealDeleted` to reclaim the `_thumb.jpg` sibling → tracked `w5-thumb-cleanup-onmealdeleted`.

**Verify (quoted):**
```
pnpm --dir functions build → tsc (exit 0)
pnpm --dir functions test → Test Files 10 passed (10) / Tests 104 passed (104)
(real sharp+thumbhash on in-memory JPEG → 21-byte ThumbHash + valid thumbnail, not mocked)
```

**Presentation handoff:** decode placeholder from `thumbHash`, load thumb in feed / full in detail.

Report: `docs/session/reports/w5-image-pipeline-function.md` · Handoff: `docs/session/handoffs/w5-image-pipeline-function.md`
