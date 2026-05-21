package es.schsebastian.foodrats.core.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Uses `LocationManager.getCurrentLocation` (API 30+, matches `minSdk = 30`)
 * over the NETWORK provider first, falling back to GPS. The implementation
 * keeps to coarse precision — meal-photo geotagging doesn't need sub-meter
 * accuracy and finer accuracy would require `ACCESS_FINE_LOCATION` + GPS lock,
 * which is overkill (slow + drains battery + scarier permission prompt).
 *
 * If the permission is missing, the launcher held in [permissionHolder] is
 * triggered exactly once; on denial the call returns
 * [LocationError.PermissionDenied] rather than silently failing.
 */
class AndroidLocationProvider(
    private val appContext: Context,
    private val permissionHolder: LocationPermissionLauncherHolder,
) : LocationProvider {

    private val locationManager: LocationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun current(): Result<Coordinates, LocationError> {
        if (!hasPermission()) {
            val granted = permissionHolder.requestAsync(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!granted) return Result.failure(LocationError.PermissionDenied)
        }

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        if (providers.isEmpty()) return Result.failure(LocationError.Unavailable)

        val raw = withTimeoutOrNull(TIMEOUT_MS) {
            providers.firstNotNullOfOrNull { provider -> requestSingle(provider) }
        } ?: return Result.failure(LocationError.Timeout)

        return (Coordinates.of(raw.latitude, raw.longitude) as? Result.Ok)?.let { Result.success(it.value) }
            ?: Result.failure(LocationError.Unavailable)
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // gated by hasPermission() check
    private suspend fun requestSingle(provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            locationManager.getCurrentLocation(
                provider,
                signal,
                appContext.mainExecutor,
            ) { loc -> if (cont.isActive) cont.resume(loc) }
        }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
