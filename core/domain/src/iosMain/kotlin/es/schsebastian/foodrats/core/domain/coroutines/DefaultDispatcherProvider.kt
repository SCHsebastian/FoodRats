@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package es.schsebastian.foodrats.core.domain.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext

// On Kotlin/Native, Dispatchers.IO is marked internal in coroutines 1.10.x.
// Dispatchers.Default is CPU-bound (sized to the core count), so blocking IO
// work (DataStore/Firestore/disk) starves the shared compute pool and can wedge
// it. Instead we own a process-lifetime fixed pool dedicated to IO. It is created
// exactly once as a top-level val (never per call) and lives for the whole
// process — matching JVM Dispatchers.IO semantics.
private val foodRatsIoPool: CoroutineDispatcher = newFixedThreadPoolContext(8, "FoodRatsIO")

internal actual fun platformIoDispatcher(): CoroutineDispatcher = foodRatsIoPool
