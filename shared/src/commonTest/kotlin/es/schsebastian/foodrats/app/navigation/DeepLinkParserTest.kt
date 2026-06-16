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
    fun https_digest_link_maps_to_weekly_story() {
        assertEquals(
            Route.WeeklyStory(weekStart = "2026-06-08", fromNotification = true),
            parseDeepLink("https://foodrats.app/digest/2026-06-08"),
        )
    }

    @Test
    fun custom_scheme_digest_link_maps_to_weekly_story() {
        assertEquals(
            Route.WeeklyStory(weekStart = "2026-06-08", fromNotification = true),
            parseDeepLink("foodrats://app/digest/2026-06-08"),
        )
    }

    @Test
    fun digest_link_missing_week_returns_null() {
        assertNull(parseDeepLink("https://foodrats.app/digest"))
    }

    @Test
    fun https_invite_link_maps_to_invite_preview() {
        assertEquals(
            Route.InvitePreview(code = "AB2K9P"),
            parseDeepLink("https://foodrats.app/invite/AB2K9P"),
        )
    }

    @Test
    fun custom_scheme_invite_link_maps_to_invite_preview() {
        assertEquals(
            Route.InvitePreview(code = "AB2K9P"),
            parseDeepLink("foodrats://app/invite/AB2K9P"),
        )
    }

    @Test
    fun invite_link_missing_code_returns_null() {
        assertNull(parseDeepLink("https://foodrats.app/invite"))
    }

    @Test
    fun invite_url_builder_uses_app_scheme_and_round_trips_through_the_parser() {
        val url = DeepLinks.inviteUrl("AB2K9P")
        // App-installed-only invite: custom scheme, no https web fallback.
        assertEquals("foodrats://app/invite/AB2K9P", url)
        assertEquals(Route.InvitePreview(code = "AB2K9P"), parseDeepLink(url))
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
