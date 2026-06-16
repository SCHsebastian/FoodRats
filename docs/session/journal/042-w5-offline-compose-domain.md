# 042 · w5-offline-compose-domain

**Status:** done

**Summary (≤6 lines):**
- Offline-compose domain: `QueuedDraft` + `QueuedDraftStatus` (sealed), `DraftQueuePort` (enqueue/observe/update/dequeue, in `:feature:meal`), pure `DraftRetryPolicy` + `DraftQueueTransitions` + `IdempotencyKeys`.
- Files: `feature/meal/.../domain/model/{QueuedDraft,QueuedDraftStatus}.kt`, `domain/queue/{DraftQueuePort,DraftRetryPolicy,DraftQueueTransitions,IdempotencyKeys}.kt`; commonTest queue tests.
- Decisions: NO separate idempotency token — the existing deterministic `MealId.forDaySlot(crew,author,day,slot)` IS the key (retry overwrites, never duplicates); `QueueEntryId` is queue-tracking only. Top-bar cross-feature read reuses existing `:core:domain` `MealUploadProgressPort` (§5.2 "extend MealUploadStatus"). No new `MealError` leaf (reuse publish errors + opaque `errorKey`). Retry policy deterministic (jitter is the runner's job).
- Blockers: none.

**Verify (quoted):**
```
> Task :feature:meal:testAndroidHostTest
BUILD SUCCESSFUL in 4s
(new queue tests 9+5+5, failures=0 errors=0)
```

**Data handoff:** persist `DraftQueuePort` + WorkManager/iOS retry runner + extend `MealUploadStatus` for the count.

Report: `docs/session/reports/w5-offline-compose-domain.md` · Handoff: `docs/session/handoffs/w5-offline-compose-domain.md`
