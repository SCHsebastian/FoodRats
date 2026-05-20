package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals

class MealErrorMapperRateTest {
    private val mapper = MealErrorMapper(NoopCrashReporter)

    @Test fun permission_denied_underscore_maps_to_window_closed() {
        val t = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")
        assertEquals(RateError.RatingWindowClosed, mapper.mapRate(t))
    }

    @Test fun permission_denied_hyphen_maps_to_window_closed() {
        val t = RuntimeException("permission-denied: rules rejected the update.")
        assertEquals(RateError.RatingWindowClosed, mapper.mapRate(t))
    }

    @Test fun unavailable_proxy_maps_to_offline() {
        val t = RuntimeException(
            "Status{code=UNAVAILABLE, description=Failed trying to connect with proxy, " +
                "cause=java.net.NoRouteToHostException: Host unreachable}",
        )
        assertEquals(RateError.Offline, mapper.mapRate(t))
    }

    @Test fun no_route_to_host_maps_to_offline() {
        val t = RuntimeException("java.net.NoRouteToHostException: Host unreachable")
        assertEquals(RateError.Offline, mapper.mapRate(t))
    }

    @Test fun unauthenticated_maps_to_unauthorized() {
        val t = RuntimeException("UNAUTHENTICATED: token expired")
        assertEquals(RateError.Unauthorized, mapper.mapRate(t))
    }

    @Test fun already_exists_maps_to_already_rated() {
        val t = RuntimeException("ALREADY_EXISTS: rating already recorded")
        assertEquals(RateError.AlreadyRated, mapper.mapRate(t))
    }

    @Test fun unknown_error_maps_to_unavailable() {
        val t = RuntimeException("Something completely unexpected happened.")
        assertEquals(RateError.RateUnavailable, mapper.mapRate(t))
    }
}
