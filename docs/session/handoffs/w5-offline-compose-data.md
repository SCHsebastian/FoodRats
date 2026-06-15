# Handoff — w5-offline-compose-data → w5-offline-compose-presentation

The DATA/INFRA layer for offline-first compose is in place and verified. This tells the
presentation task exactly how to OBSERVE the queue and what actions it can trigger.

## How to OBSERVE the pending/failed counts (feed top bar)

Use the **existing** `:core:domain` `MealUploadProgressPort` — feed already injects it (no new dep,
no feed→meal dep). It now exposes a second flow alongside `status`:

```kotlin
interface MealUploadProgressPort {
    val status: StateFlow<MealUploadStatus>                 // unchanged
    val queue: StateFlow<MealUploadQueueSnapshot>           // NEW (defaulted to EMPTY)
}
```

`core/domain/.../meal/MealUploadQueueSnapshot.kt`:
```kotlin
data class MealUploadQueueSnapshot(
    val pending: Int = 0,        // Pending + Uploading + retryable Failed — resolve on their own
    val terminalFailed: Int = 0, // Failed(retryable = false) — need a user retry/dismiss
) {
    val hasWork: Boolean get() = pending > 0 || terminalFailed > 0
    companion object { val EMPTY = MealUploadQueueSnapshot() }
}
```

Wire it MVI-style, like the existing `uploadProgress.status` consumer in `FeedViewModel` (lines ~99):
```kotlin
uploadProgress.queue
    .distinctUntilChanged()
    .onEach { snap -> update { it.copy(queuedPending = snap.pending, queuedFailed = snap.terminalFailed) } }
    .launchIn(viewModelScope)
```
Keep both counts in `FeedState` (single source of truth — no parallel `MutableStateFlow`). The existing
`isUploadActive` (`status is Uploading`) still works for the in-flight single upload; you can keep it or
fold it into `pending > 0`. Show the top bar when `pending > 0 || terminalFailed > 0`.

i18n: add `FeedStringKey` entries for the bar copy — e.g. `"%1$d waiting to publish"` and the
terminal-failed affordance label — and resolve via `resolve(...)`. Populate BOTH `values/strings.xml`
and `values-es/strings.xml`. (Counts in copy are i18n too — template `"%1$d"`, don't concatenate.)

## Per-draft status (if the UI wants a list/detail of queued entries)

The aggregate snapshot is enough for a count badge. If you want a per-entry list (e.g. a "failed uploads"
sheet), the per-entry model lives in `:feature:meal`'s `DraftQueuePort.observe(): Flow<List<QueuedDraft>>`
— but **feed must not depend on `:feature:meal`**. So either (a) keep it to the aggregate count (recommended
for the top bar), or (b) if a richer surface is needed, declare a NEW `:core:domain` read port exposing a
PII-free per-entry summary and bind it on the meal `DraftQueueRepository`. Do NOT import `QueuedDraft`
across the boundary.

## Retry / cancel actions the UI can trigger

- **Retry** a terminal `Failed` entry: the data layer auto-retries while `retryable = true`; once terminal,
  a user "retry" should flip the entry back to `Pending`. There is **no cross-feature write port for this
  yet** — `DraftQueuePort` is meal-internal. If the top bar needs a retry/dismiss button, declare a tiny
  `:core:domain` write port (e.g. `QueuedDraftActionsPort { suspend fun retryAll(); suspend fun dismissFailed() }`)
  and implement it on `DraftQueueRepository` (retry = `updateStatus(id, Pending)` for each terminal entry;
  dismiss = `remove(id)`). Keep the choke point in the data layer; the UI only calls the port.
- **Cancel/dismiss** a terminal entry: same port, `remove(id)`.
- The simplest MVP: a count-only badge with no actions (auto-retry covers the retryable cases; terminal
  failures are rare). Add the actions port only if the design calls for an explicit retry/dismiss button.

## Idempotency / reconcile (already handled in data — nothing for the UI to do)
Retries re-publish to the deterministic `MealId.forDaySlot(...)` (overwrite, never duplicate); the runner
removes an entry on publish success. The UI just reflects counts; it must not itself publish or dedupe.

## Verify command
`./gradlew :feature:meal:testAndroidHostTest` (data) + `:feature:feed:testAndroidHostTest` (your top bar).
Note: `:feature:stats` host tests are PRE-EXISTING red (unrelated `Meal()` ctor fixture breakage) — don't
let it block you; it is not caused by the offline-compose work.
