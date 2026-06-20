package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.outbox.NoopOutboxDrainScheduler
import es.schsebastian.foodrats.core.data.outbox.OutboxDrainScheduler
import org.koin.dsl.module

/**
 * iOS Koin binding for [OutboxDrainScheduler] → [NoopOutboxDrainScheduler].
 *
 * Registered in `MainViewController` alongside [connectivityIosModule]. The no-op
 * is correct for iOS: the `NWPathMonitor` rising-edge trigger in `OutboxRunner.start`
 * already drains on reconnect; a `BGTaskScheduler` wakeup is out of scope.
 */
val outboxIosModule = module {
    single<OutboxDrainScheduler> { NoopOutboxDrainScheduler() }
}
