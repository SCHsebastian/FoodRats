# 026 · w2-cuisine-passport-seed

**Status:** done

**Summary (≤6 lines):**
- Cuisine-passport seed data mirroring the ingredient seed: `cuisines.json` (14 cuisines, en/es), `dish-cuisine-map.json` (all 101 Food-101 dishes → 1 primary cuisine, full referential integrity), extended `seed-catalog.ts` uploader, `cuisines`/`dishCuisineMap` public-read rules, vitest integrity tests.
- Files: `functions/seed/cuisines.json` (new), `functions/seed/dish-cuisine-map.json` (new), `functions/__tests__/seedCuisines.test.ts` (new), `functions/scripts/seed-catalog.ts`, `firestore.rules`.
- Decisions: one primary cuisine per dish (§2.2 mirrors 1-row dishIngredientMap); 14-cuisine closed set, all used (dropped unused `mediterranean` to avoid an uncollectable cell); extended existing `seed:catalog` (no new script).
- Blockers: none. MANUAL: deploy rules + run seed.

**Verify (quoted):**
```
pnpm --dir functions test → Test Files 9 passed (9) / Tests 88 passed (88)
pnpm --dir functions build → tsc / EXIT=0
```

**Domain handoff:** `cuisines/{slug}` + `dishCuisineMap/{dishSlug}` doc shapes; cuisine slug set; mirror the ingredient-catalog read pattern; stamp cuisine at publish.

Report: `docs/session/reports/w2-cuisine-passport-seed.md` · Handoff: `docs/session/handoffs/w2-cuisine-passport-seed.md`
