package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// On Kotlin/Native, Dispatchers.IO is marked internal in coroutines 1.10.x;
// substituting Dispatchers.Default is behaviorally identical since Native's
// Dispatchers.IO is an alias for Dispatchers.Default anyway.
internal actual fun platformIoDispatcher(): CoroutineDispatcher = Dispatchers.Default
