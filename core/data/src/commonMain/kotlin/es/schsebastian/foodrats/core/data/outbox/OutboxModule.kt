package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin wiring for the write outbox (offline-first P2 §1 T4). The sibling of the
 * meal-publish queue wiring in `:feature:meal`'s `mealModule` (`DraftQueue*` +
 * `DraftRetryRunner`, kept untouched) — the write outbox COEXISTS with it.
 *
 * `:core:data` must NEVER import a `:feature:*` module, so this module binds only
 * the platform-agnostic pieces:
 *  - the single [OutboxLocalStore] (durable JSON list in DataStore),
 *  - the single [OutboxPort] [OutboxRepository] (the IO boundary), and
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
    single { OutboxLocalStore(prefs = get(), json = get()) }
    single<OutboxPort> {
        OutboxRepository(store = get(), clock = get(), dispatchers = get())
    }
    single { OutboxRetryPolicy() }
    single(createdAtStart = true) {
        OutboxRunner(
            outbox = get(),
            // Feature-owned handlers (MealOutboxCommandHandler / CrewOutboxCommandHandler)
            // bound as single<OutboxCommandHandler> in their feature modules. getAll()
            // keeps :core:data free of any :feature:* dependency.
            handlers = getAll<OutboxCommandHandler>(),
            connectivity = get(),
            policy = get(),
            analytics = get(),
        ).also { it.start(get(named("appScope"))) }
    }
}
