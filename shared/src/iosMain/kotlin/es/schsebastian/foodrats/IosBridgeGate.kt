package es.schsebastian.foodrats

import org.koin.mp.KoinPlatform

/**
 * Defers Swift→Kotlin bridge work until Koin has started.
 *
 * On a cold-start notification tap, `AppDelegate.userNotificationCenter(_:didReceive:)` fires
 * during scene creation — before `ContentView.makeUIViewController` runs [MainViewController]'s
 * configure block, which is where `startKoin` lives. An unguarded `KoinPlatform.getKoin()` in a
 * bridge then throws `IllegalStateException`, which aborts at the Kotlin/ObjC boundary (confirmed
 * Crashlytics crash). Bridges route through [runWhenReady] so pre-Koin events are stashed and
 * replayed by [open] instead of crashing — or being silently dropped.
 *
 * Main-thread-only contract: every Swift call site (UNUserNotificationCenter delegate,
 * MessagingDelegate, `onOpenURL`, `continue:userActivity`) is main-thread, and `startKoin` runs
 * on main during `ComposeUIViewController`'s configure, so no synchronization is needed.
 */
internal object IosBridgeGate {
    private val pending = mutableListOf<() -> Unit>()
    private var opened = false

    fun runWhenReady(block: () -> Unit) {
        when {
            opened -> block()
            KoinPlatform.getKoinOrNull() != null -> {
                opened = true
                block()
            }
            else -> pending += block
        }
    }

    fun open() {
        opened = true
        // Copy-then-clear before invoking: a block that enqueues another block must not
        // mutate the list being iterated.
        val drained = pending.toList()
        pending.clear()
        drained.forEach { it() }
    }
}
