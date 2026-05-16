package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/**
 * Default runtime implementation. On Android uses [Dispatchers.IO]; on
 * iOS/Native [Dispatchers.IO] is an alias for [Dispatchers.Default] so
 * the actual is provided per-platform to keep the build clean.
 */
expect class DefaultDispatcherProvider() : DispatcherProvider
