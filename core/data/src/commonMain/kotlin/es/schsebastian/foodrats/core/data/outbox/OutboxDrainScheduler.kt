package es.schsebastian.foodrats.core.data.outbox

/**
 * Platform port for scheduling a durable background drain of the write outbox that
 * survives process death.
 *
 * - Android: implemented by [WorkManagerOutboxDrainScheduler], which enqueues a
 *   unique [OutboxDrainWorker] under a `NetworkType.CONNECTED` constraint with
 *   exponential backoff. WorkManager persists the request and retries on reconnect
 *   even if the process is killed between the enqueue and the drain.
 * - iOS: implemented by [NoopOutboxDrainScheduler]. The `NWPathMonitor` rising-edge
 *   trigger in `OutboxRunner.start()` already drains on reconnect; a full
 *   `BGTaskScheduler` integration (Info.plist `BGTaskSchedulerPermittedIdentifiers` +
 *   background-mode entitlements) is out of scope here.
 *
 * [schedule] is idempotent — calling it while a worker is already enqueued or
 * running has no effect (KEEP policy on Android).
 */
interface OutboxDrainScheduler {
    /** Request a durable background drain. Safe to call from any thread/coroutine. */
    fun schedule()
}
