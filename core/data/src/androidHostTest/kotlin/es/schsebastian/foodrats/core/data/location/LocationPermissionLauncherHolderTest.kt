package es.schsebastian.foodrats.core.data.location

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPermissionLauncherHolderTest {

    /** Stub launcher that records the last-launched input and optionally invokes a callback. */
    private class FakeLauncher(private val onLaunch: (String) -> Unit = {}) :
        ActivityResultLauncher<String>() {
        override fun launch(input: String, options: ActivityOptionsCompat?) {
            onLaunch(input)
        }
        override fun unregister() = Unit
        override val contract: ActivityResultContract<String, *>
            get() = throw UnsupportedOperationException()
    }

    @Test
    fun requestAsync_returns_false_when_no_launcher_registered() = runTest(UnconfinedTestDispatcher()) {
        val holder = LocationPermissionLauncherHolder()
        assertFalse(holder.requestAsync("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun requestAsync_completes_with_true_when_deliver_called() = runTest(UnconfinedTestDispatcher()) {
        val holder = LocationPermissionLauncherHolder()
        var launched = false
        holder.register(FakeLauncher { launched = true })

        val result = async { holder.requestAsync("android.permission.ACCESS_FINE_LOCATION") }
        assertTrue(launched)
        holder.deliver(true)

        assertTrue(result.await())
    }

    @Test
    fun requestAsync_completes_with_false_when_deliver_called_denied() = runTest(UnconfinedTestDispatcher()) {
        val holder = LocationPermissionLauncherHolder()
        holder.register(FakeLauncher())

        val result = async { holder.requestAsync("android.permission.ACCESS_FINE_LOCATION") }
        holder.deliver(false)

        assertFalse(result.await())
    }

    /**
     * core-data-01 regression: a second concurrent [requestAsync] call must complete the first
     * deferred with `false` (abandon it) rather than leaking it forever.
     */
    @Test
    fun concurrent_requestAsync_abandons_first_deferred() = runTest(UnconfinedTestDispatcher()) {
        val holder = LocationPermissionLauncherHolder()
        holder.register(FakeLauncher())

        val first = async { holder.requestAsync("android.permission.ACCESS_FINE_LOCATION") }
        // Start a second request — this should complete the first with false.
        val second = async { holder.requestAsync("android.permission.ACCESS_FINE_LOCATION") }
        // Deliver the result only for the second (pending) deferred.
        holder.deliver(true)

        assertFalse(first.await(), "first deferred must be abandoned with false")
        assertTrue(second.await(), "second deferred must receive the delivered result")
    }

    @Test
    fun clear_completes_pending_deferred_with_false() = runTest(UnconfinedTestDispatcher()) {
        val holder = LocationPermissionLauncherHolder()
        holder.register(FakeLauncher())

        val result = async { holder.requestAsync("android.permission.ACCESS_FINE_LOCATION") }
        holder.clear()

        assertEquals(false, result.await())
    }
}
