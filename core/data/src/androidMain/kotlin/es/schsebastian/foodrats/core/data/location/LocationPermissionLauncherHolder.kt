package es.schsebastian.foodrats.core.data.location

import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped holder for the location-permission `ActivityResultLauncher`.
 * `MainActivity` calls [register] in `onCreate` (before `STARTED`) and [clear]
 * in `onDestroy`. The provider calls [requestAsync] to launch the OS prompt
 * and await the result. Mirrors the `PermissionLauncherHolder` pattern in
 * `:feature:notifications`, kept separate so a single launcher isn't shared
 * between unrelated permissions.
 */
class LocationPermissionLauncherHolder {
    private val launcherRef = AtomicReference<ActivityResultLauncher<String>?>()
    private val pending = AtomicReference<CompletableDeferred<Boolean>?>()

    fun register(launcher: ActivityResultLauncher<String>) {
        launcherRef.set(launcher)
    }

    fun clear() {
        launcherRef.set(null)
        pending.getAndSet(null)?.complete(false)
    }

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
