# Image upload failing — diagnosis & fix (2026-06-15)

## Symptom
Feed shows banner **"1 no se pudieron publicar"** (1 could not be published) with Retry/Discard.
User reports "image uploading is not working correctly."

## Reproduction (device: Pixel 10 Pro, pid 18553)
Tapped **Reintentar**, captured logcat:
```
Firestore: Write failed at crews/Hzcm2RtihUg9xudMzm8b/meals/Hzcm2RtihUg9xudMzm8b_O73wyK9YqCTF7zvEYcxi67aYttQ2_2026-06-15_lunch:
           Status{code=PERMISSION_DENIED, description=Missing or insufficient permissions.}
FR/MealRepo ⚠ fan-out write failed for crew Hzcm2RtihUg9xudMzm8b: PERMISSION_DENIED
FR/MealRepo ⚠ orphan plate cleanup failed: User does not have permission to access this object.  (403)
```

## Root cause (PRIMARY — blocks publish)
The image **upload succeeds**; the **Firestore meal-doc create is denied**.

`firestore.rules` meal-create rule whitelists exactly 19 fields via `keys().hasOnly([...])`
(added in commit 7574964, which also added the strict whitelist for the first time).
`MealDto` has **21** fields — the 19 whitelisted ones + the two server-owned fields
`thumbHash` / `thumbnailPath` (intentionally excluded, written later by the image pipeline
via Admin SDK).

`MealFirestoreDataSource.write()` calls `.set(dto)` with no encode settings. **GitLive
firebase 2.1.0 defaults `encodeDefaults = true`** (verified:
`firebase-common-internal/.../internal/EncodeDecodeSettings.kt:17` →
`override var encodeDefaults: Boolean = true`). So `.set(dto)` serializes ALL 21 fields,
including `thumbHash: null` and `thumbnailPath: null`.

→ `keys().hasOnly([19 fields])` sees 2 extra keys → rule fails → PERMISSION_DENIED.

The rule comment assumed "kotlinx-serialization may omit defaults on the create write" —
false for GitLive, which encodes defaults. Pre-7574964 (no whitelist) publishing worked,
which is why older meals (e.g. Sebastián's breakfast w/ thumbnail) exist.

## Root cause (SECONDARY — orphaned images)
`storage.rules` meals `match` block has `allow write` (covers create/update/delete) but its
clauses check `request.resource.size`/`.contentType`, which are **null on delete** → delete
always denied. So the best-effort orphan cleanup after a failed publish 403s, leaving the
uploaded plate orphaned in Storage. (Avatars block already has a separate `allow delete`.)

## Fix
1. `firestore.rules` meal-create: add `thumbHash` + `thumbnailPath` to the `hasOnly` whitelist,
   pinned to `null` on create (anti-forgery preserved). Update the stale comment.
2. `storage.rules` meals: add an author-scoped `allow delete` (mirrors avatars).
3. Deploy `firestore:rules` + `storage`. Re-test publish on device.

## Verification (DONE — device Pixel 10 Pro)
- Deployed: `pnpm dlx firebase-tools deploy --only firestore:rules,storage --project foodrats-de4ec`
  → `✔ firestore: released rules`, `✔ storage: released rules`, `✔ Deploy complete!`
- Re-tapped **Reintentar**. Logcat after the fix:
  ```
  16:14:01 FR/DraftQueue published queued draft 964a9241-…; removing
  ```
  No `PERMISSION_DENIED`, no `fan-out write failed`, no `orphan plate cleanup` this time.
- UI: the "1 no se pudieron publicar" banner is **gone**; Passport shows `Capturado 2026-06-15`
  (the lunch meal's ingredients landed) → publish succeeded end-to-end.

## Files changed
- `firestore.rules` — meal-create whitelist + null-pinned `thumbHash`/`thumbnailPath`.
- `storage.rules` — author-scoped `allow delete` on `crews/{crewId}/meals/{filename}`
  (also carried the pre-existing uncommitted `_thumb` `endsWith→matches` hardening).

## Note for next time
GitLive `firebase` 2.x `.set(dto)` encodes defaults (`encodeDefaults = true`). Any
`keys().hasOnly([...])` create rule MUST list every non-transient DTO field (incl. ones that
are usually null), or pin the server-owned ones to null as done here. Don't assume null
defaults are omitted on the wire.
