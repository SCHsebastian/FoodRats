# Handoff — `w0-data-export-function` → `w0-data-export-presentation`

The server side of GDPR data export (Art. 20) is live and registered. The presentation task wires
the Settings → "Export my data" flow that calls it. Nothing in functions needs further change.

## What the presentation task calls

**Callable:** `exportMyData` — region **`europe-west3`**, auth-required (Firebase callable, same as
`deleteAccount` / `mintPlateUrls`). Adapt the GitLive Functions adapter shape from
`feature/auth/.../data/firebase/FirebaseAccountDeletionPort.kt`:
`Firebase.functions("europe-west3").httpsCallable("exportMyData").invoke(req).data<Resp>()`, one
`withContext(dispatchers.io)` boundary, `runCatching{…}.fold(…)` mapping `HttpsError` codes.

### Request
Takes **no caller-supplied fields** — the function derives the uid from `request.auth.uid`. Pass an
empty object (the GitLive callable can `invoke()` with no payload, or an empty `@Serializable`
request DTO). Do NOT send an `accountId`.

### Response
```
{ downloadUrl: String, expiresAtMs: Long }
```
- `downloadUrl` — a **15-minute V4 signed READ URL** for the export archive (an
  `application/json` blob at `exports/{uid}/{ts}.json`). The UI opens/downloads this URL directly
  (browser/share-sheet). It is **not** a zip — it is JSON (see "Archive format" below).
- `expiresAtMs` — epoch-ms after which `downloadUrl` (and every plate image URL inside the archive)
  is expired. Surface a "link valid for 15 min" hint or re-request on expiry.

### Archive format (what `downloadUrl` returns)
A JSON document (`schemaVersion: 1`) with the caller's own data:
`{ schemaVersion, exportedAt, accountId, account, consent[], devices[], crews[], meals[],
comments[], votes[], plates[] }`, where `plates[]` is `{ path, url }` with each `url` a 15-min
signed image URL. Only the caller's PII — no other members' profiles/memberships. (If a true single
`.zip` is later wanted, that's a server change; presentation just opens whatever `downloadUrl`
points at.)

### Error cases → map like `deleteAccount`'s ports
| Server `HttpsError` | UI meaning |
|---|---|
| `unauthenticated` | not signed in (shouldn't happen behind the auth gate) — retryable |
| `internal` (any gather/sign/upload failure) | export failed — **retryable**, nothing destroyed |

This is **read-only** — there is no `failed-precondition`/`aborted` (no confirmation phrase, no
destructive cascade). A two-state result (URL ready / generic retryable error) is enough. Model the
domain error as a small sealed tree (`DataExportError.Backend.Unavailable` etc.) + a `*StringKey`
mapper + exhaustiveness test, per the project error convention.

### Analytics (per CHARTER #9)
If a `data_exported` event is added, fire it in the ViewModel AFTER the use case returns `Ok` —
never in the use case. No PII in params.

## Deploy steps the USER must run (carry to release)

1. `pnpm dlx firebase-tools deploy --only functions,storage --project foodrats-de4ec` (functions
   first, then storage rules — the new `exports/{uid}/{filename}` read rule).
2. Functions runtime SA needs the **Service Account Token Creator** role (V4 signed-URL minting —
   same prerequisite as `mintPlateUrls`; if image loading works, this does too).
3. If first invocation hits `FAILED_PRECONDITION: index required`, add `authorId` collection-group
   single-field indexes for `meals`+`comments` and deploy `firestore:indexes` (same as
   `deleteAccount`).
4. Optional: a Storage lifecycle rule to GC `exports/**` after N days.
