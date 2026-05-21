package es.schsebastian.foodrats.core.data.location

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.location.LocationError
import es.schsebastian.foodrats.core.domain.location.LocationProvider
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * iOS implementation: stubbed for now. Real implementation requires bridging
 * to `CoreLocation` from Swift (`CLLocationManager` + delegate), mirroring
 * the existing `GoogleSignInBridge` / `CrashlyticsBridge` pattern. Until that
 * lands the UI surfaces a "Unavailable" message rather than failing silently.
 */
class IosLocationProvider : LocationProvider {
    override suspend fun current(): Result<Coordinates, LocationError> =
        Result.failure(LocationError.Unavailable)
}
