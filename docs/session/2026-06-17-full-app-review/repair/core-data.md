# core-data repair — 2026-06-17

## core-data-01 (MEDIUM — LocationPermissionLauncherHolder concurrent-request leak)

**Changed:** `core/data/src/androidMain/.../location/LocationPermissionLauncherHolder.kt:34`
Replaced `pending.set(it)` with `pending.getAndSet(deferred)?.complete(false)` so any superseded in-flight deferred is completed with `false` rather than leaked forever.

**Tests added:**
`core/data/src/androidHostTest/.../location/LocationPermissionLauncherHolderTest.kt` — five cases covering: no-launcher returns false, deliver-true, deliver-false, concurrent-abandon regression (core-data-01), clear-completes-pending.
`core/data/build.gradle.kts` — added `kotlinx.coroutines.test` + `androidx.activity.compose` to `androidHostTest` dependencies (needed for `ActivityResultLauncher` stub and `runTest`).

---

## core-data-02 (MEDIUM — iOS DataStore force-unwrap crash)

**Changed:** `core/data/src/iosMain/.../datastore/AppDataStore.ios.kt:15–22`
Replaced `URLForDirectory(...)!!` with `?: error(...)` and also guarded `docDir.path` (which is `String?` in the NSURL binding) with a second `?: error(...)`. Both produce descriptive Kotlin exceptions that surface in Crashlytics instead of a silent SIGABRT.

**Tests added:** None — the `actual` function calls a platform API (`NSFileManager`) that is not available on JVM and cannot be tested without Xcode/iOS sim.

---

## core-data-05 (LOW — ForegroundActivityHolder `installed` not `@Volatile`)

**Changed:** `core/data/src/androidMain/.../share/ForegroundActivityHolder.kt:24`
Added `@Volatile` to `private var installed` to match the already-annotated `activityRef` field.

**Tests added:** None required (LOW mechanical fix).

---

## Skipped

- **core-data-03** — deferred per instructions: needs coordinated `ProfileViewModel` migration.
- **core-data-04** — deferred per instructions: typed `FunctionsExceptionCode` classification.

## Build risks

- `LocationPermissionLauncherHolderTest` uses `ActivityResultLauncher` (abstract class, not interface) via a manual stub. The stub overrides `launch(input, options)` and `unregister()` — the only abstract members in the AndroidX class. If the AndroidX version in the test classpath differs, compilation may fail; check that `libs.androidx.activity.compose` resolves the same version already used in `androidMain`.
- The `concurrent_requestAsync_abandons_first_deferred` test relies on `UnconfinedTestDispatcher` to interleave the two `async` blocks before either `await()` returns. If the dispatcher behaviour changes between coroutines-test versions the test could become order-sensitive; mark for review if it flakes.
