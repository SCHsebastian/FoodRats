package es.schsebastian.foodrats.app.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the auth-gating classification of every [Route].
 *
 * The exhaustiveness itself is enforced by the compiler: [requiresSession] is a `when` with no
 * `else`, so a new route won't compile until it is classified there. This test additionally pins
 * the *expected* answer per route so a wrong classification (e.g. a protected screen flipped to
 * public) is caught, not just a missing one.
 */
class RouteAccessTest {

    @Test
    fun public_routes_do_not_require_a_session() {
        assertFalse(Route.Splash.requiresSession(), "Splash must be public")
        assertFalse(Route.SignIn.requiresSession(), "SignIn must be public")
    }

    @Test
    fun protected_routes_require_a_session() {
        val protectedRoutes: List<Route> = listOf(
            Route.NotificationPermission,
            Route.CrewPicker,
            Route.CrewSettings(crewId = "crew-1"),
            Route.Profile,
            Route.Main,
            Route.CaptureMeal,
            Route.ComposePlate,
            Route.SelectIngredients,
            Route.MealDetail(mealId = "m1", dayIso = "2026-06-13"),
            MainTab.Feed,
            MainTab.Stats,
        )
        protectedRoutes.forEach { route ->
            assertTrue(route.requiresSession(), "$route must require a session")
        }
    }

    @Test
    fun marker_classification_agrees_with_requires_session() {
        // The Public/Protected markers are the documented classification; requiresSession() is the
        // gating SOURCE OF TRUTH. They must never disagree — every Public route is session-free and
        // every Protected route is gated.
        val allRoutes: List<Route> = listOf(
            Route.Splash,
            Route.SignIn,
            Route.NotificationPermission,
            Route.CrewPicker,
            Route.CrewSettings(crewId = "crew-1"),
            Route.Profile,
            Route.Main,
            Route.CaptureMeal,
            Route.ComposePlate,
            Route.SelectIngredients,
            Route.MealDetail(mealId = "m1", dayIso = "2026-06-13"),
            MainTab.Feed,
            MainTab.Stats,
        )
        allRoutes.forEach { route ->
            when (route) {
                is Route.Public -> assertFalse(
                    route.requiresSession(),
                    "$route is Route.Public but requiresSession() == true",
                )
                is Route.Protected -> assertTrue(
                    route.requiresSession(),
                    "$route is Route.Protected but requiresSession() == false",
                )
            }
        }
    }
}
