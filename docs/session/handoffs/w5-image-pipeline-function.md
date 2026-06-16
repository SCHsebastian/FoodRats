# w5-image-pipeline-function → handoff (for w5-image-pipeline-presentation)

The server now writes a blur-placeholder hash + a thumbnail ref onto every meal doc. The client
task renders the placeholder (Coil) + loads the thumbnail in feed / full image in detail.

## Meal doc fields written (Admin SDK, on `crews/{crewId}/meals/{mealId}`)

| Field           | Type   | Meaning |
|-----------------|--------|---------|
| `thumbHash`     | String | **base64-encoded ThumbHash bytes** (~21–25 bytes raw). Present only after the upload trigger runs (a few seconds post-publish). Absent ⇒ not yet processed; render the existing flat placeholder until it appears. |
| `thumbnailPath` | String | Storage object PATH of the downscaled JPEG, `crews/{crewId}/meals/{mealId}_thumb.jpg`. NOT a URL — resolve to a signed URL exactly like `platePath`. |

Add these to `MealDto` (`feature/meal/.../data/firebase/MealDto.kt`) as nullable fields
(`val thumbHash: String? = null`, `val thumbnailPath: String? = null`) — kotlinx-serialization
tolerates their absence on older docs. Map onto the domain `Meal` / `FeedMealUi` as the placeholder +
preferred-load source.

## Hash format — how the client decodes it

- It's a **ThumbHash** (https://evanw.github.io/thumbhash/), base64 of the raw bytes.
- Decode: `Base64.decode(thumbHash)` → bytes → `ThumbHash.thumbHashToRGBA(bytes)` (or
  `thumbHashToApproximateAspectRatio`). There is no maintained KMP ThumbHash lib; the reference
  algorithm is ~150 lines of pure Kotlin-portable math (port `thumbhash`'s `thumbHashToRGBA`), OR use
  a JVM lib on Android + a Swift port on iOS. Render the decoded RGBA as a Compose `ImageBitmap` and
  pass it to Coil as the `placeholder`. Aspect ratio is recoverable from the hash, so you can size the
  placeholder before the real image loads.

## Thumbnail URL/path scheme

- Path: `crews/{crewId}/meals/{mealId}_thumb.jpg`. Same crew prefix + `.jpg` as the original, so
  `mintPlateUrls` already authorizes it for crew members — **no callable change needed.** Feed-card
  image loading should request the signed URL for `thumbnailPath` (small/fast); the detail screen
  requests `platePath` (full). Both go through the existing `ImageUrlPort` / `FirebaseImageUrlResolver`
  batch-minting path — just include `thumbnailPath` in the set of paths you mint.

## Deploy steps the user must run (already appended to `docs/session/human.md` §A)

1. **Deploy functions:** `pnpm --dir functions deploy` — adds the new `onPlateImageFinalized`
   trigger. It bundles `sharp` (native; the Gen-2 build image compiles it). Watched bucket is pinned
   in code to `foodrats-de4ec.firebasestorage.app`.
2. **IAM:** the trigger needs Storage READ+WRITE + Firestore WRITE. The default Functions runtime SA
   already has these; if it was tightened, grant `roles/storage.objectAdmin` + `roles/datastore.user`.
3. No rules deploy needed for this field (clients already read the meal doc; only the server writes
   the field — `allow update` already forbids clients from touching it).

## Heads-up for whoever touches `onMealDeleted`

`onMealDeleted` does NOT yet delete the `_thumb.jpg` sibling — only the original `platePath`. If you
want thumbnails reclaimed on meal delete, add a
`bucket.file('crews/${crewId}/meals/${mealId}_thumb.jpg').delete({ ignoreNotFound: true })` next to
the existing plate-delete there. Out of scope for this task; flagged so it isn't forgotten.
