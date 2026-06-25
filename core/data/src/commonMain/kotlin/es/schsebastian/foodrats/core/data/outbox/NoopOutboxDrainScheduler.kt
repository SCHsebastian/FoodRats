package es.schsebastian.foodrats.core.data.outbox

/**
 * No-op [OutboxDrainScheduler] used:
 *  - As the default constructor argument in [OutboxRunner], keeping test
 *    construction without a real scheduler green (tests don't need WM).
 *  - On iOS (via [es.schsebastian.foodrats.core.data.di.outboxIosModule]):
 *    the `NWPathMonitor` rising-edge trigger in [OutboxRunner.start] already drains
 *    on reconnect. A full `BGTaskScheduler` integration is out of scope here.
 *
 * KDoc on [OutboxDrainScheduler] documents the iOS `BGTaskScheduler` steps needed
 * to replace this with a real implementation.
 */
class NoopOutboxDrainScheduler : OutboxDrainScheduler {
    override fun schedule() = Unit
}
