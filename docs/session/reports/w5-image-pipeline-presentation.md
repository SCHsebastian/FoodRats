# w5-image-pipeline-presentation — report

Terminal task of the image-pipeline workstream: the CLIENT side. Reads the server-written
`thumbHash`/`thumbnailPath`, decodes the ThumbHash to an instant blur placeholder behind Coil,
loads the thumbnail in the feed + the full image in detail, and downscales/re-encodes plates
on-device before upload. Server function (`onPlateImageFinalized`) already exists (see handoff
`docs/session/handoffs/w5-image-pipeline-function.md`).

## Status: DONE — all verifies green. Real placeholder + real compression are a MANUAL on-device check (added to human.md §E).

## Scope delivered (roadmap §5.1)

1. **Read the new fields.** `MealDto` gains nullable `thumbHash` + `thumbnailPath` (kotlinx-serialization
   tolerates absence on old docs). Domain `Meal` gains `thumbnailUrl: String = ""` (resolved signed URL,
   like `photoUrl`) + `thumbHash: String? = null` (base64, passed through to presentation — no graphics
   dependency in domain). `FeedMealUi` mirrors both, plus a `feedImageUrl` selector.
2. **ThumbHash decode.** Pure-Kotlin port of the reference `thumbHashToRGBA` /
   `thumbHashToApproximateAspectRatio` in `:core:designsystem` commonMain (no platform/Compose types in
   the math). A thin `expect/actual imageBitmapFromRgba` builds the Compose `ImageBitmap` (Android
   `Bitmap.setPixels`, iOS Skia `Bitmap.installPixels`). `decodeThumbHash(base64)` +
   `rememberThumbHashPainter(base64)` are the call-site API. Malformed/short/garbage hashes return null
   (feed falls back to the flat surfaceVariant box) — never throws.
3. **Feed loads thumbnail, detail loads full.** `crewStream` now mints signed URLs for BOTH
   `platePath` and `thumbnailPath` in the same `mintPlateUrls` batch (same crew prefix → already
   authorized, no callable change). `FrFeedMealRow` loads `ui.feedImageUrl` (= thumbnail when present,
   else full) with the ThumbHash painter as `placeholder`/`error`. `MealDetailScreen.PhotoHero` loads
   the full `meal.photoUrl` with the same placeholder.
4. **On-device compression.** `PlateCompressor` (`expect/actual` in `:feature:meal`) is called inside
   `PlateStorageDataSource.upload` — which runs under the repository's single `withContext(io)` boundary
   (`publish`). Decode → downscale (longest edge ≤ 1600px) → re-encode JPEG q80. Best-effort: returns
   the original bytes on any failure / already-small image (never blocks a publish). The pure scaling
   math (`PlateCompression.scaledSize`) is shared + unit-tested; the actual codec work is platform IO.
5. **Coil cache tuning.** `installImageLoader()`: memory 20% → 25%, disk 50 MB → 128 MB (the pipeline
   now serves a thumbnail per card + the full plate on detail).

## Decisions

- **ThumbHash decoder is a hand-port, not a lib** — no maintained KMP ThumbHash exists; the reference
  algorithm is ~150 lines of portable math. Put in `:core:designsystem` (it produces a Compose
  `ImageBitmap`, so it can't live in `:core:domain`). Verified against the reference algorithm fetched
  from `evanw/thumbhash`; fixed two port bugs found during testing: (a) the standalone
  `approximateAspectRatio` was reading the wrong header bytes; (b) the L channel must NOT get the 1.25×
  saturation boost (only P/Q do).
- **Compression target = 1600px / q80** — §5.1 says "~1440–2048px, quality ~80"; 1600 is the mid-band
  (ample for a full-screen hero, ~halves a 12-MP capture's linear size). Documented in
  `PlateCompression` KDoc since the spec gives a range, not an exact number.
- **Compression lives in the data source, inside `publish`'s IO boundary** — keeps the
  one-IO-boundary-per-repo-method rule (the codec call is synchronous CPU work on the already-off-main
  upload coroutine, not a new `withContext`).
- **Client never mints `thumbnailPath`** — server-owned (storage rule forbids client writes). The
  `MealDto.from(meal)` inverse carries `thumbHash` through for a faithful round-trip but leaves
  `thumbnailPath` null.
- **`PlateStorageDataSource` made `internal` + bound with explicit `single {}`** (not `singleOf`) — its
  new `PlateCompressor` ctor param uses a default, which `singleOf`'s constructor-reflection would
  otherwise try (and fail) to resolve from the Koin graph. Public class exposing an internal param also
  failed compilation; `internal` matches its `internal PlateStorage` interface. `MealModuleVerifyTest`
  stays green (it resolves `FirebaseStorage`, already in `extraTypes`).

## Files changed

- `core/domain/src/commonMain/kotlin/.../meal/Meal.kt` — `+thumbnailUrl`, `+thumbHash`
- `core/designsystem/src/commonMain/kotlin/.../image/ThumbHash.kt` (NEW) — pure decoder
- `core/designsystem/src/commonMain/kotlin/.../image/ThumbHashPainter.kt` (NEW) — base64 + Painter API + expect
- `core/designsystem/src/androidMain/kotlin/.../image/ThumbHashPainter.android.kt` (NEW)
- `core/designsystem/src/iosMain/kotlin/.../image/ThumbHashPainter.ios.kt` (NEW)
- `core/designsystem/src/androidHostTest/kotlin/.../image/ThumbHashTest.kt` (NEW) — decoder test
- `core/data/src/commonMain/kotlin/.../image/ImageLoaderSetup.kt` — cache tuning
- `feature/meal/src/commonMain/kotlin/.../data/firebase/MealDto.kt` — `+thumbHash`, `+thumbnailPath`
- `feature/meal/src/commonMain/kotlin/.../data/firebase/MealMapper.kt` — map both fields (toDomain + from)
- `feature/meal/src/commonMain/kotlin/.../data/firebase/PlateStorageDataSource.kt` — compress before upload; `internal`
- `feature/meal/src/commonMain/kotlin/.../data/firebase/PlateCompressor.kt` (NEW) — expect + pure size math
- `feature/meal/src/androidMain/kotlin/.../data/firebase/PlateCompressor.android.kt` (NEW)
- `feature/meal/src/iosMain/kotlin/.../data/firebase/PlateCompressor.ios.kt` (NEW)
- `feature/meal/src/commonMain/kotlin/.../di/MealModule.kt` — explicit `single` for the data source
- `feature/meal/src/commonMain/kotlin/.../data/repository/FirebaseMealRepository.kt` — mint + set thumbnail URL
- `feature/feed/src/commonMain/kotlin/.../presentation/components/FeedMealUi.kt` — `+thumbnailUrl/thumbHash/feedImageUrl`
- `feature/feed/src/commonMain/kotlin/.../presentation/components/FrFeedMealRow.kt` — thumbnail + placeholder
- `feature/feed/src/commonMain/kotlin/.../presentation/detail/MealDetailScreen.kt` — full image + placeholder
- `feature/meal/src/commonTest/.../data/firebase/MealDtoMapperTest.kt` — +4 thumbHash/path mapping tests
- `feature/meal/src/commonTest/.../data/firebase/PlateCompressionTest.kt` (NEW) — 8 size-calc tests
- `feature/feed/src/commonTest/.../presentation/components/FeedMealUiTest.kt` — +3 thumbnail/url-selection tests
- `docs/session/human.md` — appended §E on-device smoke walk

## Verify (commands + last lines)

`./gradlew :feature:feed:testAndroidHostTest :feature:meal:testAndroidHostTest`
```
> Task :feature:meal:testAndroidHostTest
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 7s
```
Test counts (from XML): feature:feed = 81/0 fail, feature:meal = 122/0 fail.

`./gradlew :core:designsystem:testAndroidHostTest` (added a commonMain decoder there)
```
> Task :core:designsystem:testAndroidHostTest
BUILD SUCCESSFUL in 9s
```
Test count: core:designsystem = 73/0 fail (incl. 6 new ThumbHashTest cases).

`./gradlew :core:domain:testAndroidHostTest :androidApp:assembleDebug` (touched Meal + Coil install + platform compression)
```
> Task :androidApp:assembleDebug
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 19s
```

`./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS actuals touched)
```
> Task :shared:compileKotlinIosSimulatorArm64
BUILD SUCCESSFUL in 10s
```
(Only the benign `expect/actual class … in Beta` warning remains — matches existing project convention,
e.g. `GoogleAuthClient`.)

## Test gaps / notes

- The ThumbHash decoder test is JVM-only (`androidHostTest`, Robolectric for the `Bitmap` step) — the
  pure math is platform-independent so this fully covers the algorithm; the iOS Skia actual is
  compile-verified only (`:core:designsystem` has no commonTest source set; adding one was out of scope).
- The compression math is tested in `:feature:meal` commonTest (runs on iOS targets too, though
  `:feature:meal:iosSimulatorArm64Test` can't link due to Firebase per the project's known iOS-test gap).
  The actual bitmap re-encode is platform IO — verified on-device (human.md §E).

## Suggested next

Image-pipeline workstream is complete (function + presentation + the `onMealDeleted` thumb cleanup in
`w5-thumb-cleanup-onmealdeleted`). Remaining w5 items are independent: 5.2 offline-first compose, 5.3
baseline profiles. The only gate before this is visible end-to-end is the §A functions deploy +
on-device smoke (human.md §E).
