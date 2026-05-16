package es.schsebastian.foodrats.feature.notifications.platform

import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped holder for the POST_NOTIFICATIONS ActivityResultLauncher. MainActivity calls
 * [register] in onCreate (before STARTED) and [clear] in onDestroy. The gateway calls [requestAsync]
 * to launch the OS prompt and await the result.
 */
class PermissionLauncherHolder {
    private val launcherRef = AtomicReference<ActivityResultLauncher<String>?>()
    private val pending = AtomicReference<CompletableDeferred<Boolean>?>()

    fun register(launcher: ActivityResultLauncher<String>) {
        launcherRef.set(launcher)
    }

    fun clear() {
        launcherRef.set(null)
        pending.getAndSet(null)?.complete(false)
    }

    /** Called by the launcher's callback with the granted boolean. */
    fun deliver(granted: Boolean) {
        pending.getAndSet(null)?.complete(granted)
    }

    suspend fun requestAsync(permission: String): Boolean {
        val launcher = launcherRef.get() ?: return false
        val deferred = CompletableDeferred<Boolean>().also { pending.set(it) }
        launcher.launch(permission)
        return deferred.await()
    }
}
