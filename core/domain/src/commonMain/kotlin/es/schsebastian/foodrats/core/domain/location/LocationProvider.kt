package es.schsebastian.foodrats.core.domain.location

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Reads the device's current geographic coordinates on demand. Per-platform
 * adapters live in `:core:data`; the domain consumer only sees this port.
 *
 * Implementations are expected to handle permission state themselves and
 * return a typed error rather than throwing.
 */
interface LocationProvider {
    suspend fun current(): Result<Coordinates, LocationError>
}

sealed interface LocationError {
    /** User has not granted (or has revoked) the OS location permission. */
    data object PermissionDenied : LocationError

    /** Location services are turned off or no location fix is available yet. */
    data object Unavailable : LocationError

    /** The platform took too long to return a fix. */
    data object Timeout : LocationError
}
