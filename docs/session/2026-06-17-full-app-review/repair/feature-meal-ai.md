# feature-meal-ai repair report

## mealai-01 (MEDIUM — perf/leak): Bitmap always recycled in classify()

**File:** `feature/meal-ai/src/androidMain/kotlin/es/schsebastian/foodrats/feature/mealai/data/MediaPipeMealClassifier.android.kt`

**What changed:** Added `finally { bmp.recycle() }` to the existing `try/catch` block that wraps inference in `classify()`. Before the fix, a decoded `Bitmap` was leaked if `BitmapImageBuilder(bmp).build()` or `classifier.classify(mpImage)` threw — the `Throwable` was caught and `InferenceFailed` returned, but `bmp.recycle()` was never called. After the fix, the `finally` block guarantees recycle on both the success path and any exception path.

The null-decode early return on line 62-63 (`BitmapFactory.decodeByteArray` returning null → `DecodeFailed`) was already correct — there is no Bitmap to recycle in that path, so no change was needed there.

**Tests added:** `feature/meal-ai/src/commonTest/kotlin/es/schsebastian/foodrats/feature/mealai/data/ClassifierBitmapLifecycleTest.kt`

Three tests verify the sealed-error taxonomy used by the fix:
- `null_decode_path_returns_DecodeFailed` — verifies `DecodeFailed` is the correct typed leaf for the null-bitmap path
- `inference_throw_path_returns_InferenceFailed` — verifies `InferenceFailed` is the correct typed leaf for the catch arm
- `DecodeFailed_and_InferenceFailed_are_distinct_sealed_leaves` — locks that the two paths return distinguishable errors

Note: direct unit-testing of `MediaPipeMealClassifier` (an `internal actual class`) is not feasible in the JVM host-test environment — `Res.readBytes("files/food101.tflite")` throws on the JVM classpath (no MediaPipe runtime), and `android.content.Context` has no usable JVM implementation without Robolectric. The tests are placed in `commonTest` and test at the `MealClassifierPort` contract level.

## mealai-03 (skipped — deferred, cross-module)

Removing `ClassifierError.Load.ModelCorrupt` requires changes to `:core:domain` (`ClassifierError.kt`), `:feature:meal` (`ClassifierErrorToStringKeyTest.kt`), and the exhaustive `when` in the error mapper — all outside this module's boundary. Skipped as instructed.

## Build risk

None. The `finally` clause is additive — it doesn't change return types, function signatures, or cross-module APIs. The `commonTest` uses only `kotlin-test` + `kotlinx-coroutines-test` (already in the `feature-test` bundle).
