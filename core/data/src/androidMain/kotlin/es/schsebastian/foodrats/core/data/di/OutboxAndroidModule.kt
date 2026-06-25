package es.schsebastian.foodrats.core.data.di

import android.content.Context
import es.schsebastian.foodrats.core.data.outbox.OutboxDrainScheduler
import es.schsebastian.foodrats.core.data.outbox.WorkManagerOutboxDrainScheduler
import org.koin.dsl.module

/**
 * Android Koin binding for [OutboxDrainScheduler] → [WorkManagerOutboxDrainScheduler].
 *
 * Registered in [es.schsebastian.foodrats.FoodRatsApplication] alongside
 * [connectivityAndroidModule] — both follow the same pattern of taking a [Context]
 * argument rather than resolving `androidContext()` (`:core:data` depends only on
 * `koin-core`, not `koin-android`).
 */
fun outboxAndroidModule(context: Context) = module {
    single<OutboxDrainScheduler> { WorkManagerOutboxDrainScheduler(context) }
}
