package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin wiring for the write outbox (offline-first P2 §1 T4). The sibling of the
 * meal-publish queue wiring in `:feature:meal`'s `mealModule` (`DraftQueue*` +
 * `DraftRetryRunner`, kept untouched) — the write outbox COEXISTS with it.
 *
 * `:core:data` must NEVER import a `:feature:*` module, so this module binds only
 * the platform-agnostic pieces:
 *  - the single [OutboxLocalStore] (durable SQLDelight `outbox` table; P3b-T6),
 *  - the single [OutboxPort] [OutboxRepository] (the IO boundary),
 *  - the one-shot [OutboxJsonMigration] (lifts any leftover P2 DataStore-JSON
 *    entries into the table once at boot, then clears the legacy key), and
 *  - the single, eager [OutboxRunner] that drains the queue.
 *
 * The runner dispatches each command to the first matching [OutboxCommandHandler];
 * the feature-owned handlers (meal / crew) are bound in their own modules and
 * collected here via Koin `getAll()`. It is started exactly once on the
 * app-lifetime `named("appScope")` scope (the same scope `:feature:ingredient`
 * defines and keeps the catalog listener warm on), so connectivity-return and new
 * enqueues trigger a single drainer.
 *
 * `createdAtStart = true` so the runner subscribes to its connectivity + pending
 * triggers at app boot, not lazily on first injection — the offline-first replay
 * must be live without any screen having resolved the outbox first.
 */
val outboxModule = module {
    // SQLDelight-backed (P3b-T6): the durable outbox is now the `outbox` table in FoodRatsDatabase
    // (databaseModule, wired before this module), not a DataStore-JSON blob.
    single { OutboxLocalStore(database = get(), dispatchers = get()) }
    single<OutboxPort> {
        OutboxRepository(store = get(), clock = get(), dispatchers = get())
    }
    single { OutboxRetryPolicy() }
    single { OutboxJsonMigration(prefs = get(), store = get(), dispatchers = get(), json = get()) }
    single(createdAtStart = true) {
        val appScope = get<CoroutineScope>(named("appScope"))
        // One-shot lift of any leftover P2 DataStore-JSON entries into the table, then clear the
        // legacy key. Fire-and-forget on appScope before the drainer subscribes — the migration
        // writes through the same store the runner reads, so a pre-existing entry replays once
        // migrated. Idempotent across launches (the key is cleared).
        appScope.launch { get<OutboxJsonMigration>().run() }
        // M5: prune stale terminal entries (older than 30 days) so they don't accumulate forever.
        OutboxTerminalPruner(outbox = get(), clock = get(), appScope = appScope).start()
        OutboxRunner(
            outbox = get(),
            // Feature-owned handlers (MealOutboxCommandHandler / CrewOutboxCommandHandler)
            // bound as single<OutboxCommandHandler> in their feature modules. getAll()
            // keeps :core:data free of any :feature:* dependency. STARTUP-1: wrapped in a
            // provider so the handler graph is resolved lazily on the drain path, NOT on the
            // main thread at startKoin (createdAtStart).
            handlersProvider = { getAll<OutboxCommandHandler>() },
            connectivity = get(),
            policy = get(),
            analytics = get(),
            // Platform-specific durable wakeup: WorkManagerOutboxDrainScheduler (Android)
            // or NoopOutboxDrainScheduler (iOS). Bound by outboxAndroidModule /
            // outboxIosModule before outboxModule is loaded.
            scheduler = get(),
        ).also { it.start(appScope) }
    }
}
