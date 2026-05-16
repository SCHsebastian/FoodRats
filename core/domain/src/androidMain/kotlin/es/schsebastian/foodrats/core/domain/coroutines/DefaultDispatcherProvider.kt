package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun platformIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
