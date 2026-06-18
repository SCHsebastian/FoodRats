# core-data review — 2026-06-17

## Health summary

`core/data` is well-structured and follows the project's architectural rules faithfully. The dispatcher boundary is respected: each public repository method has exactly one `withContext(dispatchers.io)`, vendor SDK types (Firebase, Crashlytics) stay in their correct platform layers, and the consent gate (`ConsentGatedAnalytics`) correctly wraps every analytics call. The DataStore singleton, preference repositories, image URL resolver, and the story-card renderer all follow the established patterns. The main risks are a concurrent-request race in `LocationPermissionLauncherHolder`, a force-unwrap crash path in the iOS DataStore, and a minor gate bypass in `ConsentGatedAnalytics` public API surface.

## Findings

### core-data-01 — LocationPermissionLauncherHolder: concurrent requestAsync drops the first caller (MEDIUM/correctness)

**File:** `core/data/src/androidMain/.../location/LocationPermissionLauncherHolder.kt:33–36`

**Evidence:**
```kotlin
val deferred = CompletableDeferred<Boolean>().also { pending.set(it) }
launcher.launch(permission)
return deferred.await()
```
If two coroutines call `requestAsync` concurrently, the second `pending.set(it)` overwrites the first `CompletableDeferred` without completing it. The first caller suspends on `deferred.await()` forever — it can never be resumed because `deliver()` will only complete the second deferred. In practice the UI can only trigger one location prompt at a time, but if a retry or coroutine leak happens the first coroutine hangs indefinitely.

**Fix:** Use `getAndSet` and complete any existing deferred with `false` before storing the new one:
```kotlin
suspend fun requestAsync(permission: String): Boolean {
    val launcher = launcherRef.get() ?: return false
    val deferred = CompletableDeferred<Boolean>()
    pending.getAndSet(deferred)?.complete(false)  // abandon any in-flight request
    launcher.launch(permission)
    return deferred.await()
}
```

---

### core-data-02 — IosDataStore: force-unwrap on `URLForDirectory` result (MEDIUM/correctness)

**File:** `core/data/src/iosMain/.../datastore/AppDataStore.ios.kt:21`

**Evidence:**
```kotlin
docDir = NSFileManager.defaultManager.URLForDirectory(..., error = null)!!
```
`URLForDirectory` can return `nil` when the Documents directory is unavailable (e.g., device first-boot before user unlock on iOS 9+ data-protection profiles, or in an app extension). The `!!` causes an NPE/SIGABRT crash at DataStore initialisation, killing the app at startup.

**Fix:** Guard the unwrap:
```kotlin
val docDir = NSFileManager.defaultManager.URLForDirectory(..., error = null)
    ?: error("NSFileManager cannot resolve NSDocumentDirectory — cannot create DataStore")
```
The `error()` call produces a descriptive Kotlin exception rather than a silent crash at the `!!` site, and the message will appear in Crashlytics.

---

### core-data-03 — ConsentGatedAnalytics: `applyConsent()` and `resetData()` pass through without consent check (LOW/security)

**File:** `core/data/src/commonMain/.../analytics/ConsentGatedAnalytics.kt:61–63`

**Evidence:**
```kotlin
override fun applyConsent(granted: Boolean) = delegate.applyConsent(granted)
override fun resetData() = delegate.resetData()
```
Any holder of the `AnalyticsPort` reference (which is the `ConsentGatedAnalytics` wrapper) can call `applyConsent(true)` and re-enable SDK collection even when the user's stored decision is `Unknown` or `Denied`. `ProfileViewModel` already calls `resetData()` on account deletion (correct use), but `applyConsent(true)` would bypass the consent gate. The internal flow observer already handles the lifecycle transition; the public overrides should be restricted.

**Fix:** Make both no-ops on the public surface (the internal flow observer drives them):
```kotlin
// Consent transitions are driven exclusively by the internal flow observer above.
// External callers must not be able to toggle the SDK gate directly.
override fun applyConsent(granted: Boolean) = Unit
override fun resetData() = Unit
```
If `ProfileViewModel`'s `resetData()` call needs to clear GA4 data on account deletion, it should call `ConsentPort.deny()` (which triggers the flow, which calls `resetData()`), not bypass the gate.

---

### core-data-04 — FirebaseImageUrlResolver: Firestore/Functions error classification via fragile string matching (LOW/correctness)

**File:** `core/data/src/commonMain/.../image/FirebaseImageUrlResolver.kt:79–83`

**Evidence:**
```kotlin
return when {
    "permission" in msg || "permission_denied" in msg -> ImageUrlError.PermissionDenied
    "unauthenticated" in msg || "sign-in" in msg -> ImageUrlError.NotSignedIn
    else -> ImageUrlError.Unavailable
}
```
GitLive Firebase Functions exceptions carry a structured `code` field (e.g., `functions/permission-denied`, `functions/unauthenticated`). Matching against `message.lowercase()` is fragile: a localized or reformatted error message can silently fall through to `Unavailable`, hiding an auth failure from the UI. The notification UX for `Unavailable` vs `NotSignedIn` is different (one is retryable; the other requires re-auth).

**Fix:** Cast the throwable to `FirebaseFunctionsException` (GitLive binding) and switch on `code`:
```kotlin
val code = (this as? FirebaseFunctionsException)?.code
return when (code) {
    FunctionsExceptionCode.PERMISSION_DENIED -> ImageUrlError.PermissionDenied
    FunctionsExceptionCode.UNAUTHENTICATED -> ImageUrlError.NotSignedIn
    else -> ImageUrlError.Unavailable
}
```

---

### core-data-05 — ForegroundActivityHolder: `installed` flag is not `@Volatile` (LOW/cleanup)

**File:** `core/data/src/androidMain/.../share/ForegroundActivityHolder.kt:24,28–29`

**Evidence:**
```kotlin
@Volatile
private var activityRef: WeakReference<Activity> = WeakReference(null)
private var installed = false  // ← NOT @Volatile
```
`activityRef` is correctly marked `@Volatile` but `installed` is not. A thread that calls `install()` on a non-main thread (theoretically possible) might see a stale `false` and register the lifecycle callbacks twice. In practice `Application.onCreate` runs on the main thread so this is benign, but it is an inconsistency that a future refactor could trip on.

**Fix:** Add `@Volatile`:
```kotlin
@Volatile private var installed = false
```
