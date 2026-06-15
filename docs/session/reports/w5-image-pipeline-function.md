# w5-image-pipeline-function — report

**Task:** SERVER/FUNCTIONS side of the image pipeline (roadmap §5.1). On plate upload, compute a
tiny blur-placeholder hash + a downscaled thumbnail, and write both refs onto the owning meal doc
so the client can paint an instant placeholder while the full image loads.

**Status:** DONE. Build + tests green; real-image `sharp`/`thumbhash` path verified in-env.

## Prior work check

None. No `docs/session/reports|handoffs/w5-image-pipeline-function.md`; no `blurhash`/`thumbhash`/
`onFinalize`/`sharp` references anywhere in `functions/src`. Built from scratch.

## Decisions (spec §5.1 leaves these open; documented here)

1. **ThumbHash, not BlurHash.** §5.1 says "BlurHash/ThumbHash". Chose **ThumbHash**: it encodes the
   aspect ratio (and alpha) *inside the hash*, decodes with no parameters, and is more faithful at a
   *smaller* byte size (~21–25 bytes) than BlurHash. Stored as a single base64 string on the meal doc.
   The client decodes via the `thumbhash` lib (`thumbHashToDataURL` / `thumbHashToRGBA`); there is a
   Kotlin port if needed — see handoff.
2. **`sharp` for decode/resize** — the de-facto standard for Cloud Functions image work; native
   binary compiles on the Gen-2 Linux build image at deploy time.
3. **Trigger:** `onObjectFinalized` (Storage), region `europe-west3` (matches every other trigger),
   `memory: "512MiB"` (decoding a 5 MiB JPEG to a full RGBA raster is memory-hungry), **bucket pinned**
   to `foodrats-de4ec.firebasestorage.app` (from `.firebaserc`). Pinning is also why the module is
   importable in unit tests — `onObjectFinalized` validates the bucket at *definition* time, so an
   unset `FIREBASE_CONFIG` would otherwise throw "Missing bucket name" on import.
4. **Thumbnail path:** `crews/{crewId}/meals/{mealId}_thumb.jpg` — same crew prefix + `.jpg`
   extension, so `mintPlateUrls.authorizedPaths` ALREADY authorizes it for crew members (no callable
   change needed). Max edge **512px**, JPEG **quality 75**.
5. **Meal doc fields written (Admin SDK, merge):** `thumbHash` (base64 string) + `thumbnailPath`
   (storage path). Client stores PATHS and mints signed read URLs on demand (existing `ImageUrlPort`
   pattern) — consistent with `platePath`.
6. **No rules change needed.** Function uses Admin SDK (bypasses rules). Clients already read the
   meal doc (rule line 117), so the two new fields are readable for free. Clients physically CANNOT
   write them: the meal `allow update` rule (line 144) restricts client diffs to
   `['ratings','ratingSum','voterCount']` only — the server is the sole writer.

## Loop safety + idempotency + robustness

- **Loop guard ×2** (defense in depth): (a) the generated thumbnail carries custom metadata marker
  `thumbnailGenerated=true` → `isGeneratedThumbnail` rejects its finalize before any work; (b) the
  path classifier rejects `*_thumb.jpg` by name. Either alone suffices.
- **Idempotent:** `readStatus` reports `processed` when the meal already has a `thumbHash` → no-op on
  a double-fire or identical re-upload.
- **Missing meal → no-op:** if the meal doc is absent (deleted before the trigger fired, or never
  written), it logs and returns; nothing is downloaded/uploaded. An orphaned `_thumb.jpg` (if a meal
  is deleted *after* processing) is reclaimed by `onMealDeleted` the same way the original is (it's
  under the same `crews/{crewId}/meals/` prefix — though note `onMealDeleted` deletes the exact
  `platePath`, see "Follow-up" below).
- **Failure isolation:** download / processing / thumbnail-upload / meal-write each guarded; a
  thumbnail-upload failure still lets the hash write proceed (the placeholder is the higher-value bit).

## Architecture (mirrors `mintPlateUrls`)

Pure, dependency-injected core so every branch is unit-testable without the Admin SDK or the native
`sharp` binary:
- `classifyPlateObject(name, contentType)` → `PlateObject | null` (path filter + thumbnail-name guard).
- `isGeneratedThumbnail(metadata)` → loop guard by metadata marker.
- `processPlateImage(buffer, ImageOps)` → `{ thumbHash, thumbnail }` (`sharp` behind the `ImageOps`
  interface; `sharpImageOps` is the prod impl).
- `runPlatePipeline({ meals, blobs, imageOps }, object)` → `PipelineOutcome` — the orchestration
  decision (ignored-not-plate / ignored-thumbnail-marker / no-meal / already-processed / processed).
  The real `onObjectFinalized` handler is a thin adapter that builds `MealStore`/`PlateBlobStore` over
  Firestore/Storage and delegates to `runPlatePipeline` — one tested code path.

## Files changed

- `functions/src/triggers/onPlateImageFinalized.ts` — NEW. The trigger + pure core.
- `functions/__tests__/onPlateImageFinalized.test.ts` — NEW. 14 tests.
- `functions/src/index.ts` — `export { onPlateImageFinalized }`.
- `functions/package.json` + `pnpm-lock.yaml` — added `sharp@^0.35.1`, `thumbhash@^0.1.1`.
- `docs/session/human.md` — updated the "Deploy Cloud Functions" entry (added trigger + IAM note).

## Verification

- `pnpm --dir functions build` → `$ tsc`, exit 0 (no errors).
- `pnpm --dir functions test` → `Test Files 10 passed (10)`, `Tests 104 passed (104)` (14 new).
- **Real `sharp`+`thumbhash` end-to-end (not mocked):** an in-memory 400×300 JPEG produced a
  21-byte ThumbHash (`XfcFJZAGxGWCd4iAh8iHcwZyYND4` base64) and a valid 512-bounded JPEG thumbnail —
  so `sharpImageOps` works in this env, not just the mocked `ImageOps`. The on-deploy real path
  (actual Storage objects) is a deploy-time check.

## Follow-up (not in scope; noted)

- `onMealDeleted` currently reclaims the exact `platePath` (and falls back to `<mealId>.jpg`). It does
  NOT delete the `_thumb.jpg` sibling. A small follow-up should extend `onMealDeleted` to also delete
  `crews/{crewId}/meals/{mealId}_thumb.jpg` so thumbnails don't outlive their meal. Left out to keep
  this task server-pipeline-only and non-overlapping; flagged in the handoff.
- The CLIENT side (Coil placeholder render from `thumbHash`, feed loads thumb / detail loads full,
  on-device compression) is `w5-image-pipeline-presentation`.
