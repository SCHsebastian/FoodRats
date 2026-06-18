# core-domain repair — 2026-06-17

## core-domain-01 — SessionError.FirebaseUnavailable renamed to ProviderUnavailable

**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/session/SessionProvider.kt`

Renamed the `data object FirebaseUnavailable` leaf to `data object ProviderUnavailable` and added a KDoc explaining the vendor-agnostic intent. The feature/auth agent is responsible for updating the single call site in `AuthSignOutPort.kt`.

**Tests added:** none — the rename is mechanical (LOW). The exhaustive `when` at call sites will catch a missed rename at compile time.

**Risk:** The auth module's `AuthSignOutPort.kt` maps `SessionError.FirebaseUnavailable`; if the auth agent does not update that file the project will fail to compile. The build should confirm both modules compile together.

---

## core-domain-03 — StreakShared item_id changed from Count to Text

**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/analytics/AnalyticsEvent.kt` line ~114

Changed `"item_id" to count(streakDays)` → `"item_id" to text(streakDays.toString())` to be consistent with every other `share` event's `item_id` (GA4 string field).

**Tests added/updated:** `AnalyticsTaxonomyTest.share_card_events_reuse_the_share_name_with_content_type_and_item_id` now also asserts `event.params["item_id"] is AnalyticsValue.Text`, which locks this fix for all current and future share-card leaves.

**Risk:** low — the old `Count` produced a `Bundle.putLong` in the Firebase adapter; this change makes it `Bundle.putString`. No domain behavior change.

---

## core-domain-07 — AccountReadPort.observeMany chains distinctUntilChanged()

**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/account/AccountReadPort.kt`

Added `.distinctUntilChanged()` after `combine(...) { pairs -> pairs.toMap() }` in the default `observeMany` impl. Also added the required import for `kotlinx.coroutines.flow.distinctUntilChanged`.

**Tests added:** none — this is a LOW-severity performance fix. The behavior contract (same map value → no downstream emit) is satisfied by the operator itself.

**Risk:** none — `distinctUntilChanged()` on a `Map` uses structural equality; `Account` is a data class so equals is well-defined.

---

## core-domain-05 — @Volatile on FrLog.enabled and FrLog.sink

**File:** `core/domain/src/commonMain/kotlin/es/schsebastian/foodrats/core/domain/telemetry/FrLog.kt`

Added `@Volatile` to `var enabled: Boolean` and `var sink: FrLogSink?`. The `disabledTags` mutable set is not made concurrent-safe here; per the findings guidance it is documented as "writes must happen before coroutines start" (pragmatic for commonMain without a platform `ConcurrentHashMap`).

**Tests added:** none — LOW severity, thread-visibility annotation, no behavioral test is practical in commonMain.

**Risk:** none on JVM/Android. On Native, `@Volatile` maps to the native `@kotlin.concurrent.Volatile` which the Kotlin Native memory model honours.

---

## Skipped

- **core-domain-02** — Skipped per instructions; the auth agent removes `AccountDeletionError.Backend.NotImplemented` as part of a cross-module chain.
- **core-domain-04** — Skipped per instructions; typed upload key is deferred (cross-cutting change).
- **core-domain-06** — Not in the assigned fix list; left as-is.
