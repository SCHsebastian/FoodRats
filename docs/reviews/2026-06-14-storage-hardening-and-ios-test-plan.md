# Saved plans for later execution — Storage hardening (#15) + iOS test run (#7)

Created 2026-06-14. These are the two remaining AAA-remediation items the user asked to
**execute in a later chat**. Both are designed against branch **`aaa-remediation-complete`**
(`a2db9bd`) — base new work there (`git checkout -b <branch> aaa-remediation-complete`).

Context handoff for the whole remediation: `docs/reviews/2026-06-13-aaa-remediation-progress.md`.

---

## PLAN A — #15 Harden Storage reads (P2, the one un-closed security item)

### The defect (verified 2026-06-14)
- `PlateStorageDataSource.upload()` (`feature/meal/.../data/firebase/`) and
  `AvatarStorageDataSource.upload()` (`feature/auth/.../data/firebase/`) both end with
  `ref.getDownloadUrl()` — a **Firebase download TOKEN URL** (the `?token=…` form). That URL
  is world-readable by anyone who has it and **bypasses Storage rules entirely**.
- The token URL is stored in `MealDto.photoUrl: String?` and `AccountDto.avatarUrl: String?`
  (and propagates to `Meal.photoUrl`, `MealAuthor.avatarUrl`, `MealRating.raterAvatarUrl`,
  `FeedMealUi.photoUrl/authorAvatarUrl`, `CommentRowUi.avatarUrl`, the stats `*Url` models).
- Read path: ONE Coil 3 `ImageLoader` (`core/data/.../image/ImageLoaderSetup.kt`) with
  `KtorNetworkFetcherFactory()` — a plain HTTP GET of whatever string is in those fields.
- `storage.rules`: plate + avatar `allow read: if request.auth != null` (not even membership) —
  and moot anyway because the token bypasses rules.

### Why "authenticated path reads" (the review's literal suggestion) is NOT viable now
GitLive `dev.gitlive:firebase-storage` 2.1.0 exposes `putData` / `getDownloadUrl` / `delete` /
`list` / metadata — but **no authenticated byte-download** (`getData`/`getStream`). So a Coil
fetcher that reads object bytes through the SDK (which WOULD enforce rules) can't be built on the
current binding. (Re-verify GitLive's API in the later chat — if a newer version adds `getData`,
Approach A below becomes the simpler option.)

### RECOMMENDED design — membership-checked V4 signed URLs via a Cloud Function (Approach B)
Pre-launch ⇒ **no migration needed** (schema changes are free; see memory `project_foodrats_no_legacy_versions`).

1. **Stop minting tokens.** Change both `upload()` methods to RETURN THE OBJECT PATH
   (`crews/{crewId}/meals/{mealId}.jpg`, `avatars/{uid}.jpg`) instead of `getDownloadUrl()`.
   Remove the `getDownloadUrl()` calls. (Upload still uses `putData` + the contentType metadata.)
2. **Store path, not URL.** Rename for honesty (ubiquitous language): `MealDto.photoUrl`→`platePath`,
   `AccountDto.avatarUrl`→`avatarPath`, and the domain/UI mirrors. (If the rename ripple is too
   wide for one pass, keep the field NAMES but change their documented SEMANTICS to "storage path"
   and resolve to a URL at the presentation boundary — but the rename is the clean end state.)
3. **Cloud Function (callable, region `europe-west3`)** — add `functions/src/storage/mintPlateUrls.ts`
   (callable `onCall`): input `{ crewId }`. Verify `context.auth.uid` is in `crews/{crewId}.members`
   (single doc read; reject `permission-denied` otherwise). Return signed READ URLs (15-min TTL) for
   that crew's referenced plate objects + the crew members' avatars:
   `bucket.file(path).getSignedUrl({ version: 'v4', action: 'read', expires: Date.now()+15*60_000 })`.
   Return `{ [path]: { url, expiresAtMs } }`. (Batch per crew/feed-day so it's ~1 call per screen.)
   Mirror the existing functions' structure/region; add `functions/__tests__/mintPlateUrls.test.ts`
   (vitest): member→urls, non-member→permission-denied, TTL set.
4. **Client resolution port** — `ImageUrlPort` in `core:domain`
   (`suspend fun resolve(crewId: CrewId, paths: List<String>): Result<Map<String,String>, …>`),
   implemented in `core:data` over the GitLive **functions** binding
   (`dev.gitlive:firebase-functions` — ADD the dep; check BOM) calling the callable, with an
   in-memory TTL cache (~12 min < the 15-min URL TTL). Resolve paths→signed URLs in the existing
   feed/stats enrichment + profile/crew VMs (feed already enriches authors via `AccountReadPort`),
   filling the existing `*Url` UI fields with the signed URL. Keep one `withContext(io)` per repo
   method; ViewModels stay I/O-free (resolution happens in the repo/enrichment layer).
   - Alternative wiring: a Coil 3 custom `Mapper`/`Fetcher` keyed on a `StoragePath` type that
     resolves transparently — cleaner call sites but puts a Firebase call inside Coil internals and
     is harder to unit-test. Prefer the port-in-enrichment approach.
5. **Storage rules** (`storage.rules`): plate + avatar `allow read: if false;` (no direct client
   read; signed URLs bypass rules via service-account signing). Keep the write/delete rules.
   Add `firestore-tests`/storage emulator assertions that a direct authed read is DENIED.
6. **IAM caveat (runbook)** — V4 signing needs the function's service account to have the
   **Service Account Token Creator** role (`iam.serviceAccounts.signBlob`) or a key. Document in
   `docs/cicd-runbook.md`. Without it, `getSignedUrl` throws at runtime.

### Verification gate for Plan A
`pnpm --dir functions exec tsc --noEmit` + `pnpm --dir functions test` (new mint tests) ·
`cd firestore-tests && pnpm test` (read-denied) · `:feature:meal:testAndroidHostTest`
`:feature:auth:testAndroidHostTest` `:feature:feed:testAndroidHostTest` `:feature:stats:testAndroidHostTest`
(FakeImageUrlPort + mapper/VM tests) · `:androidApp:assembleDebug` · `:shared:linkDebugFrameworkIosSimulatorArm64`.
Device smoke: images still load in feed/detail/stats/profile; a logged-out/non-member fetch of a raw
object path is denied.

### Effort / risk
Medium-large, multi-layer (functions + rules + core:domain port + core:data adapter + 2 upload
datasources + DTO/domain/UI field semantics + 4 VMs). Main unknowns: GitLive functions-binding
ergonomics and the IAM signing setup. All of it is host/emulator-verifiable except final image
display (device).

---

## PLAN B — #7 Wire the iOS test run (P2; user said "later wire ios test run")

### Goal
The review says iOS is "tested nowhere." Actually run `iosSimulatorArm64Test` for the
**non-Firebase** modules (they link on Apple Silicon today) and wire it onto the self-hosted Mac
runner. Firebase-touching modules still can't link without Xcode-resolved FirebaseCore SPM (open).

### Steps
1. **Locally confirm which modules link+pass** (needs a booted sim — `xcrun simctl list` /
   `xcrun simctl boot <id>`). Candidates (no Firebase): `:core:domain`, `:core:i18n`,
   `:core:designsystem`, `:core:presentation`, `:feature:feed`, `:feature:stats`. Run e.g.
   `./gradlew :core:domain:iosSimulatorArm64Test` for each; record pass/link-fail.
   - Expect link-FAIL for `:core:data`, `:feature:{auth,crew,meal,notifications}`, `:shared`
     (Firebase/FirebaseCore) — document, don't fight it here.
   - `:core:data`/`:core:presentation` may lack a host-test/test source wiring — check tasks exist.
2. **CI**: add a job to `.github/workflows/ci.yml` running on the `macos` self-hosted runner (the
   one release uses) that runs the linkable `iosSimulatorArm64Test` set. Keep it SEPARATE from the
   Linux `host-tests` job; it's secret-free (non-Firebase). Gate it so it only runs where the runner
   exists (don't break fork PRs).
3. **Document** the Firebase-module iOS-test blocker (needs FirebaseCore.framework via SPM inside
   the Gradle/Xcode boundary) as the remaining open part of #7.

### Verification gate for Plan B
The new `iosSimulatorArm64Test` invocations pass locally (quote); `ci.yml` is valid YAML; the new
job is correctly scoped to the Mac runner.

### Effort / risk
Small-medium. Risk = simulator availability + whether feed/stats transitively pull Firebase
(verify they don't). Pure-infra; no production code change.

---

## How to resume in a later chat
"Execute Plan A (#15 storage hardening) from `docs/reviews/2026-06-14-storage-hardening-and-ios-test-plan.md`,
base on `aaa-remediation-complete`." (Likewise Plan B for the iOS test run.) Memory pointer:
`project_foodrats_storage_and_ios_test_plans`.
