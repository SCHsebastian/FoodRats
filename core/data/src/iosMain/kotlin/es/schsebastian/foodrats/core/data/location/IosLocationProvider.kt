package es.schsebastian.foodrats.core.data.location

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS implementation backed directly by `CoreLocation` via Kotlin/Native interop
 * (no Swift bridge needed): a one-shot `CLLocationManager.requestLocation()` driven
 * by a [CLLocationManagerDelegateProtocol] delegate.
 *
 * Mirrors [AndroidLocationProvider]'s contract: coarse accuracy (meal-photo geotags
 * don't need sub-meter precision), self-managed permission prompt, an 8s timeout, and
 * typed [LocationError]s instead of throwing. Requires `NSLocationWhenInUseUsageDescription`
 * in Info.plist — without it `requestWhenInUseAuthorization()` aborts the app.
 *
 * All `CLLocationManager` interaction happens on the main dispatcher: the manager
 * delivers delegate callbacks on the run loop of the thread it was created on.
 */
class IosLocationProvider : LocationProvider {

    override suspend fun current(): Result<Coordinates, LocationError> =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(TIMEOUT_MS) { requestSingle() }
                ?: Result.failure(LocationError.Timeout)
        }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun requestSingle(): Result<Coordinates, LocationError> =
        suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            manager.desiredAccuracy = kCLLocationAccuracyHundredMeters

            var locationRequested = false

            fun finish(result: Result<Coordinates, LocationError>) {
                manager.delegate = null
                if (cont.isActive) cont.resume(result)
            }

            // Single-threaded on the main run loop, so a plain flag guards against the
            // initial authorization callback and our manual kick-off both firing.
            fun handleStatus(status: Int) {
                when (status) {
                    kCLAuthorizationStatusAuthorizedWhenInUse,
                    kCLAuthorizationStatusAuthorizedAlways -> {
                        if (!locationRequested) {
                            locationRequested = true
                            manager.requestLocation()
                        }
                    }

                    kCLAuthorizationStatusDenied,
                    kCLAuthorizationStatusRestricted ->
                        finish(Result.failure(LocationError.PermissionDenied))

                    kCLAuthorizationStatusNotDetermined ->
                        manager.requestWhenInUseAuthorization()
                }
            }

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    handleStatus(manager.authorizationStatus)
                }

                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val location = didUpdateLocations.lastOrNull() as? CLLocation
                        ?: return finish(Result.failure(LocationError.Unavailable))
                    val (lat, lon) = location.coordinate.useContents { latitude to longitude }
                    val coordinates = (Coordinates.of(lat, lon) as? Result.Ok)?.value
                    finish(
                        if (coordinates != null) Result.success(coordinates)
                        else Result.failure(LocationError.Unavailable),
                    )
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    finish(Result.failure(LocationError.Unavailable))
                }
            }

            manager.delegate = delegate
            // `delegate` is held weakly by the manager; capture it (and the manager) here so
            // both stay alive for the lifetime of the suspended continuation.
            cont.invokeOnCancellation {
                manager.delegate = null
                delegate.hashCode()
            }

            handleStatus(manager.authorizationStatus)
        }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
