# core-domain review — 2026-06-17

## Health summary

`:core:domain` is in excellent shape overall. The DDD conventions are uniformly applied: every error type is a `sealed interface` with `data object` leaves, all value classes carry `@JvmInline`, no Firebase/Android/Compose imports appear in commonMain (Konsist-enforced), and the `Result<T,E>` type is used exclusively throughout. The analytics taxonomy is thorough and well-tested. The main concerns are a vendor-name leak in `SessionError`, a stale `NotImplemented` error leaf left in the deletion port by intent but not yet removed, a type-safety gap in `MealUploadStatus.Failed` (raw `String` errorKey bypasses the `StringKey` contract), a `StreakShared` analytics event that encodes its `item_id` as a numeric `Count` while every other `share` event uses `Text`, and a thread-safety gap in the `FrLog` global singleton. All other dimensions — correctness, architecture, performance, Konsist coverage — are clean.

---

## Findings

### core-domain-01 — `SessionError.FirebaseUnavailable` leaks a vendor name into the domain layer

**Severity:** MEDIUM  
**Category:** architecture  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/session/SessionProvider.kt:25`

**Evidence:**
```kotlin
sealed interface SessionError {
    data object NotSignedIn         : SessionError
    data object TokenExpired        : SessionError
    data object AccountDisabled     : SessionError
    data object FirebaseUnavailable : SessionError   // ← vendor concept in domain
}
```
The `:core:domain` must be vendor-free. `FirebaseUnavailable` names the concrete infrastructure in the domain contract. The project explicitly plans to swap Firebase for an owned server; when that happens this name becomes a lie and every exhaustive `when` in caller code that has `SessionError.FirebaseUnavailable -> …` carries the wrong mental model.

**Fix:** Rename to `data object ProviderUnavailable : SessionError`. The single adapter callsite in `feature/auth` (`AuthSignOutPort.kt:45`) maps `AuthError.EmailPassword -> SessionError.FirebaseUnavailable`; update that mapping to the new name. No behavior change.

**autoFixable:** true  
**Risk:** low

---

### core-domain-02 — `AccountDeletionError.Backend.NotImplemented` is an admitted stale leaf

**Severity:** LOW  
**Category:** cleanup  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountDeletionPort.kt:41`

**Evidence:**
```kotlin
/**
 * Dead-but-kept one release: the stub-era "contact support" outcome.
 * Removed in a follow-up once no shipped build still points at the stub.
 * See spec §10.
 */
data object NotImplemented : Backend
```
The KDoc calls this "dead-but-kept one release". The project is pre-launch (no shipped builds). The leaf creates a false branch in every exhaustive `when` on `AccountDeletionError` — currently one: `feature/auth/ProfileError.kt:85` maps it to `ProfileError.Delete.NotImplemented`. Both the domain leaf and the ProfileError mapping leaf can be deleted now.

**Fix:** Remove `AccountDeletionError.Backend.NotImplemented` and the corresponding `ProfileError.Delete.NotImplemented` leaf + its mapping arm.

**autoFixable:** false (involves two files outside this module)  
**Risk:** low

---

### core-domain-03 — `AnalyticsEvent.StreakShared` encodes `item_id` as `Count` (numeric) inconsistent with every other `share` event and the GA4 `item_id` convention

**Severity:** MEDIUM  
**Category:** correctness  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/analytics/AnalyticsEvent.kt:110-116`

**Evidence:**
```kotlin
data class StreakShared(val streakDays: Int) : AnalyticsEvent {
    override val name = "share"
    override val params = mapOf(
        "content_type" to text("streak"),
        "item_id" to count(streakDays),   // ← Count(Long), not Text
    )
}
```
Every other `share` leaf (`PlateShared`, `AwardShared`, `RecapShared`, `CrewInviteShared`) encodes `item_id` as `AnalyticsValue.Text`. GA4's `select_content` / `share` predefined schema treats `item_id` as a string identifier. Encoding it as a numeric breaks the consistent schema and the `AnalyticsTaxonomyTest.share_card_events_reuse_the_share_name_with_content_type_and_item_id` assertion that checks for `item_id` presence — but does NOT check the type. The Firebase adapter will call `Bundle.putLong` instead of `Bundle.putString`, producing a different BigQuery column type and mismatched report grouping.

**Fix:**
```kotlin
"item_id" to text(streakDays.toString()),
```

**autoFixable:** true  
**Risk:** low

---

### core-domain-04 — `MealUploadStatus.Failed` carries a raw `String errorKey` bypassing the typed `StringKey` i18n contract

**Severity:** MEDIUM  
**Category:** architecture  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/meal/MealUploadStatus.kt:18`

**Evidence:**
```kotlin
data class Failed(val errorKey: String) : MealUploadStatus
```
The project rule "all user-visible text via `resolve(StringKey)`" applies to error messages. The raw string token crosses the domain boundary and is later resolved at the presentation layer by an implicit convention (documented as "opaque token the presentation layer maps to a `StringKey`"), but there is no compile-time guarantee the token is a valid key and no exhaustiveness check. When a caller adds a new error path, a misspelled or forgotten token silently produces a missing-string crash or fallback. Compare the well-typed `CommentError` / `MealValueObjectError` which carry typed leaves.

**Fix:** Replace the raw `String` field with the `StringKey` sealed interface:
```kotlin
data class Failed(val errorKey: es.schsebastian.foodrats.core.i18n.StringKey) : MealUploadStatus
```
Or, if `:core:domain` must not depend on `:core:i18n`, introduce a typed `MealUploadErrorKey` sealed interface in this file and have the feature's `uploadErrorKey()` mapper return one of its leaves instead of raw strings. This restores exhaustiveness and compiler enforcement.

**autoFixable:** false  
**Risk:** medium (requires coordinated change across `BackgroundMealUploadCoordinator`, `DraftRetryRunner`, and feed/stats presentation)

---

### core-domain-05 — `FrLog` global singleton has unsynchronized mutable state shared across coroutines and threads

**Severity:** LOW  
**Category:** correctness  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/telemetry/FrLog.kt:66-78`

**Evidence:**
```kotlin
var enabled: Boolean = true
var sink: FrLogSink? = null
@PublishedApi
internal val disabledTags: MutableSet<String> = mutableSetOf()
```
`FrLog` is a global `object`. `enabled`, `sink`, and `disabledTags` are mutated at app boot (main thread) and read from coroutines on any dispatcher (background meal upload, Firestore listeners, etc.) with no `@Volatile` or `AtomicBoolean`. On the JVM this is a data race; on Native the new memory model permits concurrent mutation of non-isolated objects. The practical impact is low because writes happen once at boot before background work starts, but the contract is unsound.

**Fix:** Annotate `enabled` with `@Volatile`. `sink` is already effectively a write-once publish — add `@Volatile`. Replace `mutableSetOf()` with `ConcurrentHashMap.newKeySet()` (JVM) or use a `@Volatile` snapshot approach in commonMain (e.g., store as `List<String>` rebuilt on each mutation under a lock). For commonMain the simplest fix is:
```kotlin
@Volatile var enabled: Boolean = true
@Volatile var sink: FrLogSink? = null
```
The `disabledTags` set is harder to make thread-safe in commonMain without a platform impl; as a pragmatic solution, document it as "writes must happen before coroutines start" or replace with a `@Volatile` immutable snapshot.

**autoFixable:** false  
**Risk:** low

---

### core-domain-06 — `AnalyticsTaxonomyTest.allEvents` is a manually-maintained list, not compiler-enforced — a new leaf can be silently omitted

**Severity:** LOW  
**Category:** correctness  
**File:** `core/domain/src/commonTest/kotlin/es/schsebastian/foodrats/core/domain/analytics/AnalyticsTaxonomyTest.kt:21-58`

**Evidence:**
The KDoc says: "Every [AnalyticsEvent] leaf must appear in [allEvents]; if you add a leaf and forget it here, you lose coverage (the governance PR review is the backstop)." The `allEvents` list currently matches (36 leaves, 36 entries), but it is purely manual: the test cannot fail if a new leaf is added to `AnalyticsEvent` but forgotten in `allEvents`. When a sealed interface has `data object` leaves, a `when(event)` over all leaves in a helper could provide compile-time exhaustiveness, but GA4-name validation does not naturally lend itself to an exhaustive `when`.

**Fix:** Add a sealed-when–based exhaustive enumerator as a private helper function:
```kotlin
private fun exhaustiveLeaves(): List<AnalyticsEvent> {
    // A when over a dummy representative instance forces the compiler to cover all leaves.
    // Alternatively, use reflection in JVM-only test:
    // AnalyticsEvent::class.sealedSubclasses (requires kotlin-reflect)
}
```
A lighter alternative: add a Konsist rule asserting the count of `allEvents` == the count of `data class` / `data object` leaves in `AnalyticsEvent`. That at least catches size drift.

**autoFixable:** false  
**Risk:** low

---

### core-domain-07 — `AccountReadPort.observeMany` default impl: `combine(List<Flow>)` emits on every individual account update, potentially firing many recompositions for unchanged members

**Severity:** LOW  
**Category:** performance  
**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountReadPort.kt:30`

**Evidence:**
```kotlin
combine(ids.map { id -> observe(id).map { id to it } }) { pairs -> pairs.toMap() }
```
For a crew of 8, this starts 8 Firestore listeners. Each individual listener update triggers a `combine` re-emit producing a fresh `Map<AccountId, Account?>`. For unchanged members this downstream recomposition is spurious. The upstream docs note "Fine for crew sizes (≤ 8)" — acknowledged, but the default impl also makes no attempt to `distinctUntilChanged()` on the combined output, so even a self-equal update (same snapshot from Firestore) causes a downstream emit.

**Fix:** Chain `.distinctUntilChanged()` on the final flow:
```kotlin
combine(...) { pairs -> pairs.toMap() }.distinctUntilChanged()
```

**autoFixable:** true  
**Risk:** low
