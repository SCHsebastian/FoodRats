# 009 · w0-data-export-function

**Status:** done

**Summary (≤6 lines):**
- `exportMyData` callable (europe-west3, auth-required) gathers caller's account/consent/devices/crews(own membership only)/authored-meals/authored-comments/cast-votes + signed plate manifest, uploads JSON to `exports/{uid}/{ts}.json`, returns `{ downloadUrl, expiresAtMs }` (15-min V4 signed URL).
- Files: `functions/src/callables/exportMyData.ts` (new), `functions/__tests__/exportMyData.test.ts` (new), `functions/src/index.ts`, `storage.rules`.
- Decisions: JSON archive (no zip dep) · synchronous/inline (bounded exports) · excludes other members' PII · analytics consent not exported (client DataStore only) · reactions not exported (Wave 1.3 doesn't exist yet) · 15-min TTL.
- Blockers: none. MANUAL at release: deploy `functions,storage`; Functions SA needs Service Account Token Creator for V4 signing; possible `authorId` collection-group indexes.

**Verify (quoted):**
```
pnpm --dir functions test → Test Files 6 passed (6) / Tests 49 passed (49) / Duration 719ms
pnpm --dir functions build → tsc / BUILD_EXIT=0
```

Report: `docs/session/reports/w0-data-export-function.md` · Handoff: `docs/session/handoffs/w0-data-export-function.md`
