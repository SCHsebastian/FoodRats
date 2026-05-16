package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// On iOS/Native, Dispatchers.IO is an alias for Dispatchers.Default.
// We use Default explicitly to avoid the internal-visibility restriction
// that applies to Dispatchers.IO in the Native target of coroutines 1.10.x.
actual class DefaultDispatcherProvider actual constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.Default
}
