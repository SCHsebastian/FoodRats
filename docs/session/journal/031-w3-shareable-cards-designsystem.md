# 031 · w3-shareable-cards-designsystem

**Status:** done

**Summary (≤6 lines):**
- `FrPlateShareCard` / `FrAwardShareCard` / `FrStreakShareCard` + `ShareCardFormat` per spec §4.1 — primitives-only, `ImageBitmap?` slot, Iron & Ember tokens + `FrSemanticColors`, deterministic ratio-locked layout, finished-string params (no i18n in DS), brand watermark.
- Files: `core/designsystem/.../templates/FrShareCard.kt` (new), `core/designsystem/androidHostTest/.../FrShareCardTest.kt` (new, 5 tests), `catalogApp/.../stories/TemplateStories.kt` (3 entries).
- Decisions: one file, 3 cards + enum + shared chrome (~80% shared); image is decoded `ImageBitmap?` slot (null → branded placeholder), never a URL; ratio-locked via `aspectRatio` (export pixel size lives with platform renderer).
- Blockers: none.

**Verify (quoted):**
```
:core:designsystem:testAndroidHostTest → BUILD SUCCESSFUL in 6s (56 tests, 0 failed)
:catalogApp:assembleDebug → BUILD SUCCESSFUL in 2s
```

**Handoff:** platform task pre-decodes plate to `ImageBitmap`, fixed 1080×1920 / 1080×1080; presentation task creates `ShareCardStringKey` + en/es, domain→primitive mappers, `share` analytics leaves.

Report: `docs/session/reports/w3-shareable-cards-designsystem.md` · Handoff: `docs/session/handoffs/w3-shareable-cards-designsystem.md`
