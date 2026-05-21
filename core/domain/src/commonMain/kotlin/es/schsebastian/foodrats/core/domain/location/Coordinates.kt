package es.schsebastian.foodrats.core.domain.location

import es.schsebastian.foodrats.core.domain.result.Result

@ConsistentCopyVisibility
data class Coordinates private constructor(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun of(latitude: Double, longitude: Double): Result<Coordinates, CoordinatesError> {
            if (latitude !in MIN_LAT..MAX_LAT) return Result.failure(CoordinatesError.LatitudeOutOfRange)
            if (longitude !in MIN_LON..MAX_LON) return Result.failure(CoordinatesError.LongitudeOutOfRange)
            return Result.success(Coordinates(latitude, longitude))
        }

        const val MIN_LAT = -90.0
        const val MAX_LAT = 90.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0
    }
}

sealed interface CoordinatesError {
    data object LatitudeOutOfRange : CoordinatesError
    data object LongitudeOutOfRange : CoordinatesError
}
