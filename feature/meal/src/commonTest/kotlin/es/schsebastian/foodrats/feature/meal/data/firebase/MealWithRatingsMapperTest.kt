package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.MealKind
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealWithRatingsMapperTest {
    @Test
    fun maps_meal_with_no_ratings_to_empty_list() {
        val dto = MealDto(
            id = "test-crew-1_uid-a_2026-05-20_lunch",
            authorId = "uid-a",
            authorName = "Maria",
            crewId = "test-crew-1",
            dayKey = "2026-05-20",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/test-crew-1_uid-a_2026-05-20_lunch.jpg",
            dishName = "Paella",
            description = "Hot off the pan",
            publishedAtEpochMs = 1_716_192_000_000L,
        )
        val result = dto.toMealWithRatings(crewMembers = emptyMap())
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals(0, mwr.ratingCount)
        assertNull(mwr.averageScore)
    }

    @Test
    fun maps_ratings_using_crew_member_names() {
        val dto = MealDto(
            id = "m1",
            authorId = "uid-a",
            authorName = "Maria",
            crewId = "test-crew-1",
            dayKey = "2026-05-20",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/m1.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1_716_192_000_000L,
            ratings = mapOf(
                "uid-b" to RatingEntryDto(score = 4, atMs = 1_716_193_000_000L),
                "uid-c" to RatingEntryDto(score = 5, atMs = 1_716_193_500_000L),
            ),
            ratingSum = 9,
            voterCount = 2,
        )
        val members = mapOf(
            "uid-b" to FakeMember(displayName = "Pedro", avatarUrl = null),
            "uid-c" to FakeMember(displayName = "Lucia", avatarUrl = "https://x/a.jpg"),
        )
        val result = dto.toMealWithRatings(crewMembers = members.mapValues { it.value.toLookup() })
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals(2, mwr.ratingCount)
        assertEquals(4.5, mwr.averageScore)
        val pedroRating = mwr.ratings.first { it.raterId.value == "uid-b" }
        assertEquals("Pedro", pedroRating.raterDisplayName)
        assertEquals(4, pedroRating.score.value)
    }

    @Test
    fun overrides_stale_author_name_when_lookup_has_live_identity() {
        val dto = MealDto(
            id = "m1",
            authorId = "uid-a",
            authorName = "OldName",
            crewId = "test-crew-1",
            dayKey = "2026-05-21",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/m1.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1L,
        )
        val members = mapOf(
            "uid-a" to CrewMemberLookup(displayName = "NewName", avatarUrl = "https://new/a.jpg"),
        )
        val result = dto.toMealWithRatings(crewMembers = members)
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals("NewName", mwr.meal.author.displayName)
        assertEquals("https://new/a.jpg", mwr.meal.author.avatarUrl)
    }

    @Test
    fun keeps_dto_author_name_when_lookup_has_no_entry() {
        val dto = MealDto(
            id = "m1",
            authorId = "uid-a",
            authorName = "FallbackName",
            crewId = "test-crew-1",
            dayKey = "2026-05-21",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/m1.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1L,
        )
        val result = dto.toMealWithRatings(crewMembers = emptyMap())
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals("FallbackName", mwr.meal.author.displayName)
        // Author avatar is no longer denormalized on the meal doc — with no live lookup it's
        // null (the avatar resolves via AccountReadPort when the author is a current member).
        assertNull(mwr.meal.author.avatarUrl)
    }

    @Test
    fun substitutes_placeholder_for_ex_members() {
        val dto = MealDto(
            id = "m1",
            authorId = "uid-a",
            authorName = "Maria",
            crewId = "test-crew-1",
            dayKey = "2026-05-20",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/m1.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1L,
            ratings = mapOf("uid-gone" to RatingEntryDto(score = 3, atMs = 1L)),
            ratingSum = 3,
            voterCount = 1,
        )
        val result = dto.toMealWithRatings(crewMembers = emptyMap())
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals(1, mwr.ratingCount)
        assertEquals("—", mwr.ratings.first().raterDisplayName)
    }

    /**
     * The read model feed/stats/detail consume (`MealWithRatings.meal`) carries `MealKind` through
     * unchanged — both with no lookup and through the live-identity `baseMeal.copy(author = …)`
     * branch (which must not drop `kind`). Locks the §4.2 claim that the seam rides `MealReadPort`
     * for free: every read meal is `Solo` today, and nothing downstream branches on it.
     */
    @Test
    fun read_model_carries_solo_kind_through_to_meal_with_ratings() {
        val dto = MealDto(
            id = "m1",
            authorId = "uid-a",
            authorName = "Maria",
            crewId = "test-crew-1",
            dayKey = "2026-05-20",
            slot = "lunch",
            platePath = "crews/test-crew-1/meals/m1.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1L,
        )
        // No-lookup branch.
        val plain = (dto.toMealWithRatings(crewMembers = emptyMap()) as Result.Ok).value
        assertEquals(MealKind.Solo, plain.meal.kind)
        // Live-identity override branch (baseMeal.copy(author = …)) must preserve kind.
        val withLive = (
            dto.toMealWithRatings(
                crewMembers = mapOf("uid-a" to CrewMemberLookup("NewName", "https://new/a.jpg")),
            ) as Result.Ok
            ).value
        assertEquals(MealKind.Solo, withLive.meal.kind)
    }

    private data class FakeMember(val displayName: String, val avatarUrl: String?)
    private fun FakeMember.toLookup() = CrewMemberLookup(displayName = displayName, avatarUrl = avatarUrl)
}
