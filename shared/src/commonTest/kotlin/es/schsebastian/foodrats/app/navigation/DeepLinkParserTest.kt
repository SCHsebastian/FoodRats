package es.schsebastian.foodrats.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {

    @Test
    fun https_meal_link_maps_to_meal_detail() {
        assertEquals(
            Route.MealDetail(mealId = "abc123", dayIso = "2026-05-26"),
            parseDeepLink("https://foodrats.app/meal/abc123/2026-05-26"),
        )
    }

    @Test
    fun custom_scheme_meal_link_maps_to_meal_detail() {
        assertEquals(
            Route.MealDetail(mealId = "abc123", dayIso = "2026-05-26"),
            parseDeepLink("foodrats://app/meal/abc123/2026-05-26"),
        )
    }

    @Test
    fun https_crew_link_maps_to_crew_settings() {
        assertEquals(
            Route.CrewSettings(crewId = "crew-7"),
            parseDeepLink("https://foodrats.app/crew/crew-7"),
        )
    }

    @Test
    fun custom_scheme_crew_link_maps_to_crew_settings() {
        assertEquals(
            Route.CrewSettings(crewId = "crew-7"),
            parseDeepLink("foodrats://app/crew/crew-7"),
        )
    }

    @Test
    fun query_and_fragment_are_ignored() {
        assertEquals(
            Route.CrewSettings(crewId = "crew-7"),
            parseDeepLink("https://foodrats.app/crew/crew-7?ref=push#top"),
        )
    }

    @Test
    fun unknown_path_returns_null() {
        assertNull(parseDeepLink("https://foodrats.app/settings/profile"))
    }

    @Test
    fun meal_link_missing_day_returns_null() {
        assertNull(parseDeepLink("https://foodrats.app/meal/abc123"))
    }

    @Test
    fun root_link_returns_null() {
        assertNull(parseDeepLink("https://foodrats.app/"))
    }

    @Test
    fun garbage_returns_null() {
        assertNull(parseDeepLink("not even a uri"))
    }
}
