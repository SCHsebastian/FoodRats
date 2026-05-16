package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/**
 * Default runtime implementation. On Android, `io` uses the dedicated IO pool;
 * on iOS/Native it aliases to the default pool because `Dispatchers.IO` is
 * `internal` to coroutines on Native — the actual is provided per platform.
 */
expect class DefaultDispatcherProvider() : DispatcherProvider
