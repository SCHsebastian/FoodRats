# Report — `w0-data-export-function`

**Date:** 2026-06-14
**Scope:** SERVER/FUNCTIONS layer only — the GDPR Art. 20 data-export callable Cloud Function, its
vitest coverage, `index.ts` registration, and the `storage.rules` export read path. The Android/iOS
Settings UI, the download trigger, and i18n are OUT of scope (`w0-data-export-presentation`).

Spec: `docs/roadmap/2026-06-14-feature-roadmap.md` §0.4.

## Prior work check

No partial export work existed on disk. `functions/src/callables/` held only `mintPlateUrls.ts` +
`deleteAccount.ts`; no `exportMyData.ts`, no export test. Built fresh from the `deleteAccount` /
`mintPlateUrls` baseline (mirrors their auth/region/error/DI-core/test conventions).

## What changed

### NEW `functions/src/callables/exportMyData.ts`

A thin `onCall({ region: "europe-west3" })` wrapper over a dependency-injected, Admin-SDK-free
testable core (`exportMyDataCore(deps, uid, req)`), exactly like `deleteAccountCore` /
`buildSignedUrls`. A pure projection `buildExportArchive(...)` assembles the JSON document so the
"right shape + excludes other members' PII" contract is asserted without fakes.

**Flow:** unauth guard (`HttpsError("unauthenticated")`) → gather the caller's data in parallel →
sign a 15-min V4 read URL for each authored plate image → assemble the archive JSON → upload it to
`exports/{uid}/{ts}.json` (`contentType: application/json`) → sign a 15-min V4 read URL for the
archive → return `{ downloadUrl, expiresAtMs }`.

**Exported (the caller's data ONLY):**
- `account` — `accounts/{uid}` profile doc (null if gone).
- `consent` — owner-only `accounts/{uid}/private/*` docs (PII / consent records, if any).
- `devices` — caller's FCM tokens: `accounts/{uid}/devices/*` + legacy top-level `devices/{uid}`.
- `crews` — per crew the caller is in: `{ crewId, name, ownerId, myMembership }`. **Only the
  caller's own membership** + shared crew identity — NO other members' profiles or membership
  entries (the `members` map is never emitted).
- `meals` — every meal the caller authored across every crew
  (`collectionGroup("meals").where("authorId","==",uid)`), projected to `{ path, data }`.
- `comments` — every comment the caller authored, incl. on others' meals
  (`collectionGroup("comments").where("authorId","==",uid)`).
- `votes` — every score the caller cast: `ratings[uid]` entries on meals NOT authored by them,
  iterated over the caller's crews (map-key existence isn't queryable; crews are ≤ 8 members so the
  bounded per-crew scan is fine — mirrors `deleteAccount.votedMeals`).
- `plates` — manifest of `{ path, url }`: signed read URLs for the caller's plate images.
- `schemaVersion` (=1), `exportedAt` (ISO), `accountId`.

Real Admin-SDK deps wired in the `onCall`: `getFirestore()` reads + `getStorage().bucket().file(p)`
`.getSignedUrl({ version: "v4", action: "read", expires })` (copied from `mintPlateUrls`) and
`.save(json, { contentType, resumable: false })` for the upload. `platePathOf` falls back to the
deterministic `crews/{crewId}/meals/{mealId}.jpg` upload path (mirrors `deleteAccount` /
`onMealDeleted`).

### `functions/src/index.ts`
`export { exportMyData } from "./callables/exportMyData";`.

### `storage.rules`
Added an `exports/{uid}/{filename}` block: `read: if request.auth != null && request.auth.uid ==
uid` (owner-only, defense-in-depth), `write: if false` (function-only via Admin SDK, which bypasses
rules). The actual download path is the signed URL — which also bypasses rules — so the explicit
read rule is belt-and-suspenders.

### NEW `functions/__tests__/exportMyData.test.ts`
10 vitest cases over `exportMyDataCore` (recording fakes — uploaded JSON + signed paths tracked) +
`buildExportArchive` directly: unauth (nothing gathered), 15-min download URL + exactly one archive
uploaded, every plate image + the archive signed, full archive assembly (account/consent/devices/
crews/meals/comments/votes/plates), missing-account graceful path, **excludes other members' PII**
(only `myMembership`, no `members` map, no foreign uid leaks), self-authored meals excluded from
votes, plate de-dup by path, schema version + ISO timestamp stamping, and meal projection drops the
internal `platePath`.

## Decisions

- **Delivery = JSON archive uploaded to Storage + 15-min signed download URL** (§0.4 "write … to
  Storage; return a 15-min signed download URL"). I export **JSON, not a zip** — no zip library is
  installed in `functions/` and adding a binary-streaming dep (`archiver`/`jszip`) for a small,
  bounded export isn't warranted. The plate images are delivered via the signed-URL manifest inside
  the JSON (§0.4 asks for "a manifest of plate image URLs" — done), so the JSON is a complete copy.
  If a true single-file zip is later required, swap `uploadArchive` to stream a zip; the core +
  tests don't change. **Documented in the handoff so presentation knows it gets JSON, not a zip.**
- **Synchronous, inline (no enqueue/push job).** §0.4 lists an async job pattern as an option for
  "export may be slow". Exports are bounded (crew ≤ 8 members; a member authors a handful of
  meals/day), so the gather runs inside the callable and returns the URL directly — simpler, and
  the presentation UI just awaits the result. Noted as a future split if data volume grows.
- **15-min TTL** for both the archive URL and the plate image URLs — matches `mintPlateUrls`'
  `URL_TTL_MS` so the client caches consistently. `EXPORT_URL_TTL_MS` is exported.
- **Analytics consent is NOT in the export.** The analytics opt-in lives in client-side DataStore
  (`AnalyticsConsentState`), not Firestore/Auth — there's no server-side record to export. The
  reserved `Account.dataConsentVersion`/`dataConsentGrantedAtEpochMs` fields (on the `accounts/{uid}`
  doc) and any `accounts/{uid}/private/*` docs ARE exported via `account` + `consent`.
- **Reactions are NOT exported** — `MealReaction` is a Wave 1.3 feature that doesn't exist yet (no
  Firestore collection, no functions reference). Add a `reactions` gather when 1.3 lands.

## Verification

### `pnpm --dir functions test` (last lines)
```
 Test Files  6 passed (6)
      Tests  49 passed (49)
   Duration  719ms (...)
```
(10 of the 49 are the new `exportMyData.test.ts`. The lone stderr is the pre-existing
`deleteAccount` reassign-failure `logger.error`, which still passes.)

### `pnpm --dir functions build` (tsc) — last lines
```
$ tsc
BUILD_EXIT=0
```
Clean — the production code with the real Admin-SDK deps type-checks.

## MANUAL — deploy/store steps (not codeable here; carry to release)

1. Deploy: `pnpm dlx firebase-tools deploy --only functions,storage --project foodrats-de4ec`
   (functions first, then storage rules) — order per `docs/cicd-runbook.md` §6.
2. If the first invocation reports `FAILED_PRECONDITION: index required`, add the `authorId`
   single-field collection-group indexes for `meals` + `comments` to `firestore.indexes.json` and
   re-deploy `firestore:indexes` (SAME indexes `deleteAccount` needs — likely already present once
   deleteAccount is deployed).
3. Confirm the Functions runtime SA can mint V4 signed URLs (needs the Service Account Token Creator
   role / `iam.serviceAccounts.signBlob`) — same prerequisite as `mintPlateUrls`. If `mintPlateUrls`
   works, this does too. (This is the recurring IAM Token-Creator item noted in the storage-hardening
   handoff.)
4. App Store Connect / Google Play Data-Safety: declare the data-export capability if required.
5. Optional lifecycle: set a Storage lifecycle rule to auto-delete `exports/**` after N days (the
   signed URL already expires in 15 min; this just reclaims the blobs).

## Blockers

None.
