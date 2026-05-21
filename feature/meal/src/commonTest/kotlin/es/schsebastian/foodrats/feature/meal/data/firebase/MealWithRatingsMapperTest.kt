package es.schsebastian.foodrats.feature.meal.data.firebase

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
            authorAvatarUrl = null,
            crewId = "test-crew-1",
            dayKey = "2026-05-20",
            slot = "lunch",
            photoUrl = "https://example.com/p.jpg",
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
            photoUrl = "https://example.com/p.jpg",
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
            authorAvatarUrl = "https://old/avatar.jpg",
            crewId = "test-crew-1",
            dayKey = "2026-05-21",
            slot = "lunch",
            photoUrl = "https://example.com/p.jpg",
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
            authorAvatarUrl = "https://fallback/avatar.jpg",
            crewId = "test-crew-1",
            dayKey = "2026-05-21",
            slot = "lunch",
            photoUrl = "https://example.com/p.jpg",
            dishName = "Paella",
            publishedAtEpochMs = 1L,
        )
        val result = dto.toMealWithRatings(crewMembers = emptyMap())
        assertTrue(result is Result.Ok)
        val mwr = (result as Result.Ok).value
        assertEquals("FallbackName", mwr.meal.author.displayName)
        assertEquals("https://fallback/avatar.jpg", mwr.meal.author.avatarUrl)
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
            photoUrl = "https://example.com/p.jpg",
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

    private data class FakeMember(val displayName: String, val avatarUrl: String?)
    private fun FakeMember.toLookup() = CrewMemberLookup(displayName = displayName, avatarUrl = avatarUrl)
}
